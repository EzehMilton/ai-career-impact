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

    @Test
    void allExplicitFixturesAreDeterministicAndValidForTheRealPolicy() {
        assertThat(LocalFixtureProfessionalJudgmentProvider.supportedFixtureIds()).containsExactly(
                "electrician", "registered_nurse", "routine_software_developer", "senior_software_architect");
        for (String id : LocalFixtureProfessionalJudgmentProvider.supportedFixtureIds()) {
            LocalFixtureProfessionalJudgmentProvider provider =
                    new LocalFixtureProfessionalJudgmentProvider(id);
            AssessmentJudgmentSet first = provider.judge(state("A real job title"));
            AssessmentJudgmentSet second = provider.judge(state("Another real job title"));
            assertThat(first).isEqualTo(second);
            assertThat(provider.source()).isEqualTo(AssessmentSource.LOCAL_FIXTURE);
            assertThat(first.journey()).isEqualTo(JourneyType.PROFESSIONAL);
            assertThat(first.schemaVersion()).isEqualTo(ProfessionalJudgmentSchema.VERSION);
            assertThat(first.judgments()).hasSize(9);
            assertThat(new ProfessionalDecisionPolicyV1().decide(first).measures()).hasSize(4);
        }
    }

    @Test
    void unsupportedFixtureFailsRatherThanChoosingADefault() {
        assertThatThrownBy(() -> new LocalFixtureProfessionalJudgmentProvider("unknown"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported app.professional.fixture-id 'unknown'")
                .hasMessageContaining("routine_software_developer");
        assertThatThrownBy(() -> new LocalFixtureProfessionalJudgmentProvider(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("app.professional.fixture-id");
    }

    @Test
    void configuredFixtureNotProfessionDeterminesJudgments() {
        ProfessionalAssessmentState state = state("Software Developer");
        AssessmentJudgmentSet routine = new LocalFixtureProfessionalJudgmentProvider(
                "routine_software_developer").judge(state);
        AssessmentJudgmentSet architect = new LocalFixtureProfessionalJudgmentProvider(
                "senior_software_architect").judge(state);

        assertThat(routine).isNotEqualTo(architect);
    }

    private ProfessionalAssessmentState state(String profession) {
        return new ProfessionalAssessmentState(profession, "Explicit local test case",
                List.of(), List.of(), OptionalInt.empty());
    }
}
