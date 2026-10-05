package com.chikere.futureai.cv.professional.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalProfileAssessmentMapperTest {
    @Test
    void explicitlyMapsConfirmedProfileIntoExistingAssessmentState() {
        var profile = new ProfessionalProfile("Software Architect",
                "Designs complex platforms and guides teams through technical trade-offs.",
                List.of("Design platforms"), List.of("Java"), List.of("Communication"),
                List.of("Mentor engineers"), List.of("Approve architecture decisions"),
                List.of("Financial services"), OptionalInt.of(12), ProfessionalProfileCompleteness.COMPLETE, List.of());

        var state = new ProfessionalProfileAssessmentMapper().toState(profile);

        assertThat(state.profession()).isEqualTo("Software Architect");
        assertThat(state.workDescription()).isEqualTo(profile.roleSummary());
        assertThat(state.responsibilities()).containsExactly(
                "Design platforms", "Mentor engineers", "Approve architecture decisions");
        assertThat(state.skills()).containsExactly("Java", "Communication", "Financial services");
        assertThat(state.yearsOfExperience()).hasValue(12);
    }
}
