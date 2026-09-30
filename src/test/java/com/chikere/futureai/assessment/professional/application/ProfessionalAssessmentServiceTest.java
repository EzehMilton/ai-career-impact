package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;

class ProfessionalAssessmentServiceTest {

    @Test
    void callsProviderOnceAndUsesTheExistingPolicyWithVersionedProvenance() {
        ProfessionalAssessmentState state = state("Software Developer");
        ProfessionalJudgmentProvider provider = mock(ProfessionalJudgmentProvider.class);
        when(provider.judge(state)).thenReturn(
                new LocalFixtureProfessionalJudgmentProvider("routine_software_developer").judge(state));
        when(provider.source()).thenReturn(AssessmentSource.JEV);

        ProfessionalAssessmentResult result = new ProfessionalAssessmentService(
                provider, new ProfessionalDecisionPolicyV1()).assess(state);

        verify(provider, times(1)).judge(state);
        verify(provider, times(1)).source();
        verifyNoMoreInteractions(provider);
        assertThat(result.source()).isEqualTo(AssessmentSource.JEV);
        assertThat(result.judgmentSchemaVersion()).isEqualTo("professional-v1");
        assertThat(result.decisionPolicyVersion()).isEqualTo(ProfessionalDecisionPolicyV1.POLICY_VERSION);
        assertThat(result.decision().policyVersion()).isEqualTo(result.decisionPolicyVersion());
        assertThat(result.decision().measure(ProfessionalMeasure.AI_EXPOSURE).orElseThrow().indicator())
                .isCloseTo(9.471, within(0.001));
    }

    @Test
    void providerFailurePropagatesWithoutFixtureFallback() {
        ProfessionalAssessmentState state = state("Software Developer");
        ProfessionalJudgmentProvider provider = mock(ProfessionalJudgmentProvider.class);
        IllegalStateException failure = new IllegalStateException("Jev unavailable");
        when(provider.judge(state)).thenThrow(failure);

        ProfessionalAssessmentService service = new ProfessionalAssessmentService(
                provider, new ProfessionalDecisionPolicyV1());

        assertThatThrownBy(() -> service.assess(state)).isSameAs(failure);
        verify(provider, times(1)).judge(state);
        verifyNoMoreInteractions(provider);
        assertThatNullPointerException().isThrownBy(() -> service.assess(null));
        verifyNoMoreInteractions(provider);
    }

    @Test
    void fixtureAssessmentCarriesFixtureProvenanceToTheApplicationResult() {
        ProfessionalAssessmentResult result = new ProfessionalAssessmentService(
                new LocalFixtureProfessionalJudgmentProvider("electrician"),
                new ProfessionalDecisionPolicyV1()).assess(state("Electrician"));

        assertThat(result.source()).isEqualTo(AssessmentSource.LOCAL_FIXTURE);
        assertThat(result.judgmentSchemaVersion()).isEqualTo("professional-v1");
        assertThat(result.decisionPolicyVersion()).isEqualTo(ProfessionalDecisionPolicyV1.POLICY_VERSION);
    }

    @Test
    void submissionCapturesCompleteSnapshotWithOneProviderAndPolicyCallAndReadDoesNotReassess() {
        var state = state("Software Architect");
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var provider = mock(ProfessionalJudgmentProvider.class);
        var policy = spy(new ProfessionalDecisionPolicyV1());
        var store = mock(ProfessionalAssessmentStore.class);
        org.mockito.Mockito.doReturn(judgments).when(provider).judge(state);
        when(provider.source()).thenReturn(AssessmentSource.JEV);
        when(provider.modelIdentifier()).thenReturn(Optional.of("jev-latest"));
        var service = new ProfessionalAssessmentService(provider, policy, store,
                Clock.fixed(Instant.parse("2026-09-30T12:00:00Z"), ZoneOffset.UTC));

        UUID id = service.submitProfessionalAssessment(state);
        ArgumentCaptor<StoredProfessionalAssessment> capture =
                ArgumentCaptor.forClass(StoredProfessionalAssessment.class);
        verify(store).save(capture.capture());
        var saved = capture.getValue();
        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.createdAt()).isEqualTo(Instant.parse("2026-09-30T12:00:00Z"));
        assertThat(saved.state()).isEqualTo(state);
        assertThat(saved.judgments()).isEqualTo(judgments);
        assertThat(saved.result().source()).isEqualTo(AssessmentSource.JEV);
        assertThat(saved.modelIdentifier()).contains("jev-latest");
        verify(provider, times(1)).judge(state);
        verify(policy, times(1)).decide(judgments);

        when(store.findById(id)).thenReturn(Optional.of(saved));
        assertThat(service.getProfessionalAssessment(id)).contains(saved);
        verify(provider, times(1)).judge(state);
        verify(policy, times(1)).decide(judgments);
    }

    @Test
    void providerOrPersistenceFailureNeverProducesSuccessfulSubmission() {
        var state = state("Electrician");
        var provider = mock(ProfessionalJudgmentProvider.class);
        var store = mock(ProfessionalAssessmentStore.class);
        var service = new ProfessionalAssessmentService(provider, new ProfessionalDecisionPolicyV1(),
                store, Clock.systemUTC());
        when(provider.judge(state)).thenThrow(new IllegalStateException("Jev unavailable"));
        assertThatThrownBy(() -> service.submitProfessionalAssessment(state))
                .hasMessage("Jev unavailable");
        verify(store, never()).save(any());

        var judgments = new LocalFixtureProfessionalJudgmentProvider("electrician").judge(state);
        org.mockito.Mockito.doReturn(judgments).when(provider).judge(state);
        when(provider.source()).thenReturn(AssessmentSource.LOCAL_FIXTURE);
        org.mockito.Mockito.doThrow(new IllegalStateException("DB unavailable"))
                .when(store).save(any());
        assertThatThrownBy(() -> service.submitProfessionalAssessment(state))
                .hasMessage("DB unavailable");
        verify(provider, times(2)).judge(state);
    }

    private ProfessionalAssessmentState state(String profession) {
        return new ProfessionalAssessmentState(profession, "Explicit local test case",
                List.of(), List.of(), OptionalInt.empty());
    }
}
