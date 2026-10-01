package com.chikere.futureai.judgment.typesafe.university;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UniversityJevStateMapperTest {

    private final UniversityJevStateMapper mapper = new UniversityJevStateMapper();

    @Test
    void mapsEveryDomainFieldAndOmitsAbsentOptionals() {
        Map<String, Object> state = mapper.map(new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms", "Databases"), List.of("Programming"), List.of("Software engineering"),
                Optional.empty(), Optional.empty()));

        assertThat(state).containsExactly(
                Map.entry("journey", "UNIVERSITY"),
                Map.entry("course_title", "Computer Science"),
                Map.entry("course_description", "Programming, algorithms, systems and a final-year project."),
                Map.entry("key_modules", List.of("Algorithms", "Databases")),
                Map.entry("skills_developed", List.of("Programming")),
                Map.entry("preferred_career_areas", List.of("Software engineering")));
        assertThatThrownBy(() -> state.put("injected", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void includesInstitutionAndCountryOnlyWhenSupplied() {
        Map<String, Object> state = mapper.map(new UniversityAssessmentState("History",
                "Sources, historiography, essays and an independent dissertation.",
                List.of("Early modern Europe"), List.of("Argument"), List.of(),
                Optional.of("A UK university"), Optional.of("United Kingdom")));

        assertThat(state).containsEntry("institution", "A UK university")
                .containsEntry("country", "United Kingdom");
        assertThatNullPointerException().isThrownBy(() -> mapper.map(null));
    }
}
