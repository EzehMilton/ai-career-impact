package com.chikere.futureai.cv.professional.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalProfileAssessmentMapperTest {
    @Test
    void explicitlyMapsConfirmedProfileIntoExistingAssessmentState() {
        var profile = new ProfessionalProfile("Software Architect", "", "", "",
                "Designs complex platforms and guides teams through technical trade-offs.",
                List.of("Design platforms"), List.of("Java"), List.of("Communication"),
                List.of("Mentor engineers"), List.of("Approve architecture decisions"),
                List.of("Financial services"), OptionalInt.of(12), List.of(), ProfessionalProfileCompleteness.COMPLETE, List.of());

        var state = new ProfessionalProfileAssessmentMapper().toState(profile);

        assertThat(state.profession()).isEqualTo("Software Architect");
        assertThat(state.workDescription()).isEqualTo(profile.roleSummary());
        assertThat(state.responsibilities()).containsExactly(
                "Design platforms", "Mentor engineers", "Approve architecture decisions");
        assertThat(state.skills()).containsExactly("Java", "Communication", "Financial services");
        assertThat(state.yearsOfExperience()).hasValue(12);
    }

    @Test
    void employerAndDatesNeverReachTheAssessmentState() {
        var without = profile("", "", "");
        var with = profile("Northbridge Digital", "March 2019", "Present");

        var mapper = new ProfessionalProfileAssessmentMapper();
        var expected = new com.chikere.futureai.assessment.domain.ProfessionalAssessmentState("Software Architect",
                "Designs complex platforms and guides teams through technical trade-offs.",
                List.of("Design platforms", "Mentor engineers"), List.of("Java", "Communication"),
                OptionalInt.of(12));

        assertThat(mapper.toState(with)).isEqualTo(mapper.toState(without)).isEqualTo(expected);
        assertThat(mapper.toState(with).toString()).doesNotContain("Northbridge", "March 2019", "Present");
    }

    @Test
    void experienceBasisNeverReachesTheAssessmentState() {
        var mapper = new ProfessionalProfileAssessmentMapper();
        var withBasis = profile("", "", "", List.of("Software Engineer at Push Gaming (08/2025 – Present)"));

        assertThat(mapper.toState(withBasis)).isEqualTo(mapper.toState(profile("", "", "")));
        assertThat(mapper.toState(withBasis).yearsOfExperience()).hasValue(12);
        assertThat(mapper.toState(withBasis).toString()).doesNotContain("Push Gaming", "08/2025");
    }

    private static ProfessionalProfile profile(String employer, String start, String end) {
        return profile(employer, start, end, List.of());
    }

    private static ProfessionalProfile profile(String employer, String start, String end, List<String> basis) {
        return new ProfessionalProfile("Software Architect", employer, start, end,
                "Designs complex platforms and guides teams through technical trade-offs.",
                List.of("Design platforms"), List.of("Java"), List.of("Communication"),
                List.of("Mentor engineers"), List.of(), List.of(), OptionalInt.of(12),
                basis, ProfessionalProfileCompleteness.COMPLETE, List.of());
    }
}
