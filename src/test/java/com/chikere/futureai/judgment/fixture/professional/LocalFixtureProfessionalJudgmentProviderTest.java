package com.chikere.futureai.judgment.fixture.professional;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFixtureProfessionalJudgmentProviderTest {

    private final LocalFixtureProfessionalJudgmentProvider provider =
            new LocalFixtureProfessionalJudgmentProvider();

    @Test
    void allExplicitFixturesAreDeterministicAndValidForTheRealPolicy() {
        assertThat(LocalFixtureProfessionalJudgmentProvider.supportedFixtureIds()).containsExactly(
                "electrician", "registered_nurse", "routine_software_developer", "senior_software_architect");
        assertThat(provider.source()).isEqualTo(AssessmentSource.LOCAL_FIXTURE);
        for (String id : LocalFixtureProfessionalJudgmentProvider.supportedFixtureIds()) {
            AssessmentJudgmentSet first = provider.judge(state(id));
            AssessmentJudgmentSet second = provider.judge(state(id));
            assertThat(first).isEqualTo(second);
            assertThat(first.journey()).isEqualTo(JourneyType.PROFESSIONAL);
            assertThat(first.schemaVersion()).isEqualTo(ProfessionalJudgmentSchema.VERSION);
            assertThat(first.judgments()).hasSize(9);
            assertThat(new ProfessionalDecisionPolicyV1().decide(first).measures()).hasSize(4);
        }
    }

    @Test
    void unsupportedFixtureFailsRatherThanChoosingADefault() {
        assertThatThrownBy(() -> provider.judge(state("Software Developer")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported professional fixture 'Software Developer'")
                .hasMessageContaining("routine_software_developer");
    }

    private ProfessionalAssessmentState state(String profession) {
        return new ProfessionalAssessmentState(profession, "Explicit local test case",
                List.of(), List.of(), OptionalInt.empty());
    }
}
