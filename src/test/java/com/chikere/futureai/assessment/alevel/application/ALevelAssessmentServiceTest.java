package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ALevelAssessmentServiceTest {

    @Test
    void callsTheProviderOnceAndThenTheDeterministicPolicyWithVersionedProvenance() {
        ALevelAssessmentState state = state();
        ALevelJudgmentProvider provider = mock(ALevelJudgmentProvider.class);
        doReturn(new LocalFixtureALevelJudgmentProvider("maths_economics_geography").judge(state))
                .when(provider).judge(state);
        when(provider.source()).thenReturn(AssessmentSource.JEV);

        ALevelAssessmentResult result = new ALevelAssessmentService(
                provider, new ALevelDecisionPolicyV1()).assess(state);

        verify(provider, times(1)).judge(state);
        verify(provider, times(1)).source();
        verifyNoMoreInteractions(provider);
        assertThat(result.source()).isEqualTo(AssessmentSource.JEV);
        assertThat(result.judgmentSchemaVersion()).isEqualTo("a-level-v1");
        assertThat(result.decisionPolicyVersion()).isEqualTo(ALevelDecisionPolicyV1.POLICY_VERSION);
        assertThat(result.decision().measure(ALevelMeasure.FUTURE_OPTIONALITY).orElseThrow().indicator())
                .isCloseTo(8.3125, within(0.0001));
    }

    @Test
    void providerFailurePropagatesWithoutAnyFixtureFallback() {
        ALevelAssessmentState state = state();
        ALevelJudgmentProvider provider = mock(ALevelJudgmentProvider.class);
        IllegalStateException failure = new IllegalStateException("Jev unavailable");
        doThrow(failure).when(provider).judge(state);

        ALevelAssessmentService service = new ALevelAssessmentService(provider, new ALevelDecisionPolicyV1());

        assertThatThrownBy(() -> service.assess(state)).isSameAs(failure);
        verify(provider, times(1)).judge(state);
        verifyNoMoreInteractions(provider);
        assertThatNullPointerException().isThrownBy(() -> service.assess(null));
    }

    @Test
    void submissionSavesOneSnapshotAndHistoricalReadNeverReassesses() {
        var state = state();
        var judgments = new LocalFixtureALevelJudgmentProvider("english_history_politics").judge(state);
        var provider = mock(ALevelJudgmentProvider.class);
        var policy = spy(new ALevelDecisionPolicyV1());
        var store = mock(ALevelAssessmentStore.class);
        doReturn(judgments).when(provider).judge(state);
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
        assertThat(saved.modelIdentifier()).contains("jev-latest");
        verify(provider, times(1)).judge(state);
        verify(policy, times(1)).decide(judgments);

        when(store.findById(id)).thenReturn(Optional.of(saved));
        assertThat(service.getALevelAssessment(id)).contains(saved);
        verify(provider, times(1)).judge(state);
        verify(policy, times(1)).decide(judgments);
    }

    @Test
    void providerOrPersistenceFailureNeverProducesASuccessfulSubmission() {
        var state = state();
        var provider = mock(ALevelJudgmentProvider.class);
        var store = mock(ALevelAssessmentStore.class);
        var service = new ALevelAssessmentService(provider, new ALevelDecisionPolicyV1(),
                store, Clock.systemUTC());
        doThrow(new IllegalStateException("Jev unavailable")).when(provider).judge(state);

        assertThatThrownBy(() -> service.submitALevelAssessment(state)).hasMessage("Jev unavailable");
        verify(store, never()).save(any());

        doReturn(new LocalFixtureALevelJudgmentProvider("mixed_balanced").judge(state))
                .when(provider).judge(state);
        when(provider.source()).thenReturn(AssessmentSource.LOCAL_FIXTURE);
        doThrow(new IllegalStateException("DB unavailable")).when(store).save(any());
        assertThatThrownBy(() -> service.submitALevelAssessment(state)).hasMessage("DB unavailable");
    }

    private ALevelAssessmentState state() {
        return new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"), Map.of(),
                List.of("Problem solving"), List.of(), List.of(), Optional.empty(), Optional.empty());
    }
}
