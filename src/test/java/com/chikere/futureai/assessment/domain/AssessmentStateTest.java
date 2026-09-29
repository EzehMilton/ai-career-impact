package com.chikere.futureai.assessment.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentStateTest {

    @Test
    void eachStateHasItsOwnJourneyAndRuntimeType() {
        AssessmentState professional = professionalState(List.of("Interview users"));
        AssessmentState university = new UniversityAssessmentState(
                "Computer Science", List.of("Robotics"), Optional.of("Research engineer"));
        AssessmentState aLevel = new ALevelAssessmentState(
                List.of("Mathematics", "Physics"), List.of("Technology"), List.of("Analysis"), Optional.empty());

        assertThat(JourneyType.values()).containsExactly(
                JourneyType.PROFESSIONAL, JourneyType.UNIVERSITY, JourneyType.A_LEVEL);
        assertThat(professional.journey()).isEqualTo(JourneyType.PROFESSIONAL);
        assertThat(university.journey()).isEqualTo(JourneyType.UNIVERSITY);
        assertThat(aLevel.journey()).isEqualTo(JourneyType.A_LEVEL);
        assertThat(professional).isInstanceOf(ProfessionalAssessmentState.class)
                .isNotInstanceOf(UniversityAssessmentState.class);
    }

    @Test
    void inputCollectionsAreDefensivelyCopiedAndUnmodifiable() {
        ArrayList<String> responsibilities = new ArrayList<>(List.of("Interview users"));
        ProfessionalAssessmentState state = professionalState(responsibilities);

        responsibilities.add("Manage budgets");

        assertThat(state.responsibilities()).containsExactly("Interview users");
        assertThatThrownBy(() -> state.responsibilities().add("Write reports"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requiredValuesAndExperienceAreValidated() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProfessionalAssessmentState(
                " ", "Plans services", List.of(), List.of(), OptionalInt.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new UniversityAssessmentState(
                "Computer Science", List.of(" "), Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ALevelAssessmentState(
                List.of(), List.of(), List.of(), Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProfessionalAssessmentState(
                "Product manager", "Plans services", List.of(), List.of(), OptionalInt.of(-1)));
    }

    private ProfessionalAssessmentState professionalState(List<String> responsibilities) {
        return new ProfessionalAssessmentState(
                "Product manager",
                "Plans and improves digital services",
                responsibilities,
                List.of("Research", "Prioritisation"),
                OptionalInt.of(5));
    }
}
