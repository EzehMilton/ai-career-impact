package com.chikere.futureai.judgment.typesafe.alevel;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ALevelJevStateMapperTest {

    private final ALevelJevStateMapper mapper = new ALevelJevStateMapper();

    @Test
    void mapsCanonicalSubjectsAndOmitsAbsentOptionals() {
        Map<String, Object> state = mapper.map(new ALevelAssessmentState(
                List.of("maths", "Economics", "geog"), Map.of(), List.of(), List.of(), List.of(),
                Optional.empty(), Optional.empty()));

        assertThat(state).containsExactly(
                Map.entry("journey", "A_LEVEL"),
                Map.entry("selected_subjects", List.of("Mathematics", "Economics", "Geography")),
                Map.entry("strengths_and_interests", List.of()),
                Map.entry("preferred_degree_areas", List.of()),
                Map.entry("preferred_career_areas", List.of()));
        assertThatThrownBy(() -> state.put("injected", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void includesGradesContextAndCountryOnlyWhenSupplied() {
        Map<String, Object> state = mapper.map(new ALevelAssessmentState(
                List.of("Mathematics", "Physics"), Map.of("Mathematics", "A"),
                List.of("Problem solving"), List.of("Engineering"), List.of("Civil engineering"),
                Optional.of("United Kingdom"), Optional.of("Considering a placement year.")));

        assertThat(state).containsEntry("predicted_grades", Map.of("Mathematics", "A"))
                .containsEntry("country", "United Kingdom")
                .containsEntry("additional_context", "Considering a placement year.")
                .containsEntry("strengths_and_interests", List.of("Problem solving"))
                .containsEntry("preferred_degree_areas", List.of("Engineering"));
        assertThatNullPointerException().isThrownBy(() -> mapper.map(null));
    }
}
