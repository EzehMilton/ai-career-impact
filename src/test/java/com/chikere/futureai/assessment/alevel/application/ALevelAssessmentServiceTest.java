package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasure;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelV2JudgmentProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ALevelAssessmentServiceTest {

    @Test
    void callsTheProviderOnceAndThenTheDeterministicPolicyWithVersionedProvenance() {
        ALevelAssessmentState state = state();
        ALevelJudgmentProvider provider = mock(ALevelJudgmentProvider.class);
        doReturn(new LocalFixtureALevelV2JudgmentProvider("maths_economics_geography").judge(ALevelJudgmentInput.of(state)))
                .when(provider).judge(ALevelJudgmentInput.of(state));
        when(provider.source()).thenReturn(AssessmentSource.JEV);

        ALevelAssessmentResult result = new ALevelAssessmentService(
                provider, new ALevelDecisionPolicyV2()).assess(state);

        verify(provider, times(1)).judge(ALevelJudgmentInput.of(state));
        verify(provider, times(1)).source();
        verifyNoMoreInteractions(provider);
        assertThat(result.source()).isEqualTo(AssessmentSource.JEV);
        assertThat(result.judgmentSchemaVersion()).isEqualTo("a-level-v2");
        assertThat(result.decisionPolicyVersion()).isEqualTo("a-level-decision-v2");
        assertThat(result.usesSubjectProfile()).isTrue();
        assertThat(result.headline().overallIndicator()).isEmpty();
        // 10 x (0.60 x 3.4/4 + 0.40 x 3.4/4)
        assertThat(result.profile().measure(ALevelProfileMeasure.OPTIONS_KEPT_OPEN).orElseThrow().indicator())
                .isCloseTo(8.5, within(1e-9));
    }

    @Test
    void providerFailurePropagatesWithoutAnyFixtureFallback() {
        ALevelAssessmentState state = state();
        ALevelJudgmentProvider provider = mock(ALevelJudgmentProvider.class);
        IllegalStateException failure = new IllegalStateException("Jev unavailable");
        doThrow(failure).when(provider).judge(ALevelJudgmentInput.of(state));

        ALevelAssessmentService service = new ALevelAssessmentService(provider, new ALevelDecisionPolicyV2());

        assertThatThrownBy(() -> service.assess(state)).isSameAs(failure);
        verify(provider, times(1)).judge(ALevelJudgmentInput.of(state));
        verifyNoMoreInteractions(provider);
        assertThatNullPointerException().isThrownBy(() -> service.assess(null));
    }

    @Test
    void submissionSavesOneSnapshotAndHistoricalReadNeverReassesses() {
        var state = state();
        var judgments = new LocalFixtureALevelV2JudgmentProvider("english_history_politics").judge(ALevelJudgmentInput.of(state));
        var provider = mock(ALevelJudgmentProvider.class);
        var policy = new ALevelDecisionPolicyV2();
        var store = mock(ALevelAssessmentStore.class);
        doReturn(judgments).when(provider).judge(ALevelJudgmentInput.of(state));
        when(provider.source()).thenReturn(AssessmentSource.JEV);
        when(provider.modelIdentifier()).thenReturn(Optional.of("jev-latest"));
        var service = new ALevelAssessmentService(provider, policy, store,
                Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC));

        UUID id = service.submitALevelAssessment(state);

        ArgumentCaptor<StoredALevelAssessment> capture =
                ArgumentCaptor.forClass(StoredALevelAssessment.class);
        verify(store).save(capture.capture());
        var saved = capture.getValue();
        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.createdAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(saved.state()).isEqualTo(state);
        assertThat(saved.judgments()).isEqualTo(judgments);
        var expectedDecision = policy.decide(judgments);
        assertThat(saved.result()).isEqualTo(new ALevelAssessmentResult(expectedDecision,
                AssessmentSource.JEV, judgments.schemaVersion(), expectedDecision.policyVersion()));
        assertThat(saved.modelIdentifier()).contains("jev-latest");
        verify(provider, times(1)).judge(ALevelJudgmentInput.of(state));

        when(store.findById(id)).thenReturn(Optional.of(saved));
        assertThat(service.getALevelAssessment(id)).contains(saved);
        verify(provider, times(1)).judge(ALevelJudgmentInput.of(state));
    }

    @Test
    void providerOrPersistenceFailureNeverProducesASuccessfulSubmission() {
        var state = state();
        var provider = mock(ALevelJudgmentProvider.class);
        var store = mock(ALevelAssessmentStore.class);
        var service = new ALevelAssessmentService(provider, new ALevelDecisionPolicyV2(),
                store, Clock.systemUTC());
        doThrow(new IllegalStateException("Jev unavailable")).when(provider).judge(ALevelJudgmentInput.of(state));

        assertThatThrownBy(() -> service.submitALevelAssessment(state)).hasMessage("Jev unavailable");
        verify(store, never()).save(any());

        doReturn(new LocalFixtureALevelV2JudgmentProvider("mixed_balanced").judge(ALevelJudgmentInput.of(state)))
                .when(provider).judge(ALevelJudgmentInput.of(state));
        when(provider.source()).thenReturn(AssessmentSource.LOCAL_FIXTURE);
        doThrow(new IllegalStateException("DB unavailable")).when(store).save(any());
        assertThatThrownBy(() -> service.submitALevelAssessment(state)).hasMessage("DB unavailable");
    }

    @Test
    void theProviderSeesOnlyTheSubjectsSoStudentContextCannotChangeTheJudgment() {
        var bare = state();
        var rich = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of("Mathematics", "A*", "Economics", "B"), List.of("Debating", "Data"), List.of("Economics"),
                List.of("Finance"), Optional.of("United Kingdom"), Optional.of("Thinking about a gap year."));
        var provider = mock(ALevelJudgmentProvider.class);
        var judgments = new LocalFixtureALevelV2JudgmentProvider("maths_economics_geography")
                .judge(ALevelJudgmentInput.of(bare));
        when(provider.judge(any())).thenReturn(judgments);
        when(provider.source()).thenReturn(AssessmentSource.JEV);
        var service = new ALevelAssessmentService(provider, new ALevelDecisionPolicyV2());

        var fromBare = service.assess(bare);
        var fromRich = service.assess(rich);

        ArgumentCaptor<ALevelJudgmentInput> inputs = ArgumentCaptor.forClass(ALevelJudgmentInput.class);
        verify(provider, times(2)).judge(inputs.capture());
        assertThat(inputs.getAllValues()).containsOnly(new ALevelJudgmentInput(
                List.of("Mathematics", "Economics", "Geography")));
        assertThat(fromRich).isEqualTo(fromBare);
    }

    private ALevelAssessmentState state() {
        return new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"), Map.of(),
                List.of("Problem solving"), List.of(), List.of(), Optional.empty(), Optional.empty());
    }
}
