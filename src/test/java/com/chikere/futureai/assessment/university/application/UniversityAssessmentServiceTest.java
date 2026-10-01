package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

class UniversityAssessmentServiceTest {

    @Test
    void callsTheProviderOnceAndThenTheDeterministicPolicyWithVersionedProvenance() {
        UniversityAssessmentState state = state();
        UniversityJudgmentProvider provider = mock(UniversityJudgmentProvider.class);
        doReturn(new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state))
                .when(provider).judge(state);
        when(provider.source()).thenReturn(AssessmentSource.JEV);

        UniversityAssessmentResult result = new UniversityAssessmentService(
                provider, new UniversityDecisionPolicyV1()).assess(state);

        verify(provider, times(1)).judge(state);
        verify(provider, times(1)).source();
        verifyNoMoreInteractions(provider);
        assertThat(result.source()).isEqualTo(AssessmentSource.JEV);
        assertThat(result.judgmentSchemaVersion()).isEqualTo("university-v1");
        assertThat(result.decisionPolicyVersion()).isEqualTo(UniversityDecisionPolicyV1.POLICY_VERSION);
        assertThat(result.decision().measure(UniversityMeasure.FUTURE_READINESS).orElseThrow().indicator())
                .isCloseTo(8.750, within(0.001));
    }

    @Test
    void providerFailurePropagatesWithoutAnyFixtureFallback() {
        UniversityAssessmentState state = state();
        UniversityJudgmentProvider provider = mock(UniversityJudgmentProvider.class);
        IllegalStateException failure = new IllegalStateException("Jev unavailable");
        doThrow(failure).when(provider).judge(state);

        UniversityAssessmentService service = new UniversityAssessmentService(
                provider, new UniversityDecisionPolicyV1());

        assertThatThrownBy(() -> service.assess(state)).isSameAs(failure);
        verify(provider, times(1)).judge(state);
        verifyNoMoreInteractions(provider);
        assertThatNullPointerException().isThrownBy(() -> service.assess(null));
    }

    @Test
    void submissionSavesOneSnapshotAndHistoricalReadNeverReassesses() {
        var state = state();
        var judgments = new LocalFixtureUniversityJudgmentProvider("nursing").judge(state);
        var provider = mock(UniversityJudgmentProvider.class);
        var policy = spy(new UniversityDecisionPolicyV1());
        var store = mock(UniversityAssessmentStore.class);
        doReturn(judgments).when(provider).judge(state);
        when(provider.source()).thenReturn(AssessmentSource.JEV);
        when(provider.modelIdentifier()).thenReturn(Optional.of("jev-latest"));
        var service = new UniversityAssessmentService(provider, policy, store,
                Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC));

        UUID id = service.submitUniversityAssessment(state);

        ArgumentCaptor<StoredUniversityAssessment> capture =
                ArgumentCaptor.forClass(StoredUniversityAssessment.class);
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
        assertThat(service.getUniversityAssessment(id)).contains(saved);
        verify(provider, times(1)).judge(state);
        verify(policy, times(1)).decide(judgments);
    }

    @Test
    void providerOrPersistenceFailureNeverProducesASuccessfulSubmission() {
        var state = state();
        var provider = mock(UniversityJudgmentProvider.class);
        var store = mock(UniversityAssessmentStore.class);
        var service = new UniversityAssessmentService(provider, new UniversityDecisionPolicyV1(),
                store, Clock.systemUTC());
        doThrow(new IllegalStateException("Jev unavailable")).when(provider).judge(state);

        assertThatThrownBy(() -> service.submitUniversityAssessment(state)).hasMessage("Jev unavailable");
        verify(store, never()).save(any());

        doReturn(new LocalFixtureUniversityJudgmentProvider("history").judge(state)).when(provider).judge(state);
        when(provider.source()).thenReturn(AssessmentSource.LOCAL_FIXTURE);
        doThrow(new IllegalStateException("DB unavailable")).when(store).save(any());
        assertThatThrownBy(() -> service.submitUniversityAssessment(state)).hasMessage("DB unavailable");
    }

    private UniversityAssessmentState state() {
        return new UniversityAssessmentState("A course", "Explicit local test case with enough detail.",
                List.of("A module"), List.of("A skill"), List.of("An area"),
                Optional.empty(), Optional.empty());
    }
}
