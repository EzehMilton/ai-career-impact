package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ProfessionalAssessmentServiceTest {

    @Test
    void callsProviderOnceAndUsesTheExistingPolicyWithVersionedProvenance() {
        ProfessionalAssessmentState state = state("routine_software_developer");
        ProfessionalJudgmentProvider provider = mock(ProfessionalJudgmentProvider.class);
        when(provider.judge(state)).thenReturn(new LocalFixtureProfessionalJudgmentProvider().judge(state));
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
        ProfessionalAssessmentState state = state("routine_software_developer");
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
                new LocalFixtureProfessionalJudgmentProvider(),
                new ProfessionalDecisionPolicyV1()).assess(state("electrician"));

        assertThat(result.source()).isEqualTo(AssessmentSource.LOCAL_FIXTURE);
        assertThat(result.judgmentSchemaVersion()).isEqualTo("professional-v1");
        assertThat(result.decisionPolicyVersion()).isEqualTo(ProfessionalDecisionPolicyV1.POLICY_VERSION);
    }

    private ProfessionalAssessmentState state(String profession) {
        return new ProfessionalAssessmentState(profession, "Explicit local test case",
                List.of(), List.of(), OptionalInt.empty());
    }
}
