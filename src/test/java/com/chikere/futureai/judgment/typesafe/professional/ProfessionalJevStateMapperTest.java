package com.chikere.futureai.judgment.typesafe.professional;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalJevStateMapperTest {

    private final ProfessionalJevStateMapper mapper = new ProfessionalJevStateMapper();

    @Test
    void mapsProfessionalStateToExplicitStructuredFields() {
        ProfessionalAssessmentState assessmentState = new ProfessionalAssessmentState(
                "Physiotherapist",
                "Assesses patients and creates rehabilitation plans",
                List.of("Examine mobility", "Guide rehabilitation exercises"),
                List.of("Clinical reasoning", "Patient communication"),
                OptionalInt.of(7));

        Map<String, Object> state = mapper.map(assessmentState);

        assertThat(state).containsExactlyInAnyOrderEntriesOf(Map.of(
                "journey", "PROFESSIONAL",
                "profession", "Physiotherapist",
                "work_description", "Assesses patients and creates rehabilitation plans",
                "responsibilities", List.of("Examine mobility", "Guide rehabilitation exercises"),
                "skills", List.of("Clinical reasoning", "Patient communication"),
                "years_experience", 7));
        assertThat(state.get("responsibilities")).isInstanceOf(List.class);
        assertThat(state.get("skills")).isInstanceOf(List.class);
    }

    @Test
    void omitsAbsentExperienceAndContainsNoUnrelatedFields() {
        ProfessionalAssessmentState assessmentState = new ProfessionalAssessmentState(
                "Copywriter",
                "Writes product descriptions",
                List.of("Draft copy"),
                List.of("Editing"),
                OptionalInt.empty());

        Map<String, Object> state = mapper.map(assessmentState);

        assertThat(state).containsOnlyKeys(
                "journey", "profession", "work_description", "responsibilities", "skills");
        assertThat(state).doesNotContainKeys("years_experience", "course", "subjects", "api_key");
    }
}
