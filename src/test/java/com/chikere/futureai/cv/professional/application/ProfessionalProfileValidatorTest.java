package com.chikere.futureai.cv.professional.application;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfessionalProfileValidatorTest {
    private final ProfessionalProfileValidator validator = new ProfessionalProfileValidator();

    @Test
    void stripsEmployerAndDatesAndClearsOverLongValuesWithoutRejectingOrWarning() {
        var candidate = withRoleEntry("  Northbridge Digital  ", " March 2019 ", "Present ");
        var kept = validator.validate(candidate);
        assertThat(kept.currentEmployer()).isEqualTo("Northbridge Digital");
        assertThat(kept.currentRoleStart()).isEqualTo("March 2019");
        assertThat(kept.currentRoleEnd()).isEqualTo("Present");

        var bounded = validator.validate(withRoleEntry("e".repeat(120), "s".repeat(40), "n".repeat(40)));
        assertThat(bounded.currentEmployer()).hasSize(120);
        assertThat(bounded.currentRoleStart()).hasSize(40);
        assertThat(bounded.currentRoleEnd()).hasSize(40);

        var cleared = validator.validate(withRoleEntry("e".repeat(121), "s".repeat(41), "n".repeat(41)));
        assertThat(cleared.currentEmployer()).isEmpty();
        assertThat(cleared.currentRoleStart()).isEmpty();
        assertThat(cleared.currentRoleEnd()).isEmpty();
        assertThat(cleared.warnings()).isEmpty();
        assertThat(cleared.completeness()).isEqualTo(ProfessionalProfileCompleteness.COMPLETE);
    }

    @Test
    void missingEmployerAndDatesNeverRejectTheProfile() {
        var profile = validator.validate(withRoleEntry(null, null, "  "));
        assertThat(profile.currentEmployer()).isEmpty();
        assertThat(profile.currentRoleStart()).isEmpty();
        assertThat(profile.currentRoleEnd()).isEmpty();
    }

    private static ProfessionalProfile withRoleEntry(String employer, String start, String end) {
        return new ProfessionalProfile("Software Engineer", employer, start, end,
                "Builds and maintains dependable software services for operational business teams.",
                List.of("Builds services"), List.of("Java"), List.of(), List.of(), List.of(), List.of(),
                OptionalInt.of(6), ProfessionalProfileCompleteness.PARTIAL, List.of());
    }

    @Test
    void rejectsMissingRoleInsteadOfInventingOne() {
        assertThatThrownBy(() -> validator.validate(profile("", List.of("Builds services"))))
                .isInstanceOf(ProfessionalProfileExtractionException.class);
    }

    @Test
    void rejectsResponsibilitySentenceAsRoleTitle() {
        assertThatThrownBy(() -> validator.validate(profile(
                "Responsible for developing software platforms for customers", List.of("Builds services"))))
                .isInstanceOf(ProfessionalProfileExtractionException.class);
    }

    @Test
    void keepsResponsibilitiesAndSkillsSeparateAndPreservesAmbiguityWarning() {
        var candidate = new ProfessionalProfile("Software Architect", "", "", "",
                "Designs dependable systems and makes technical decisions with delivery teams.",
                List.of("Designs service boundaries"), List.of("Java"), List.of("Communication"),
                List.of(), List.of(), List.of(), OptionalInt.empty(), ProfessionalProfileCompleteness.PARTIAL,
                List.of("Several roles were present; the most recent identifiable role was used."));

        var result = validator.validate(candidate);

        assertThat(result.responsibilities()).containsExactly("Designs service boundaries");
        assertThat(result.technicalSkills()).containsExactly("Java");
        assertThat(result.humanSkills()).containsExactly("Communication");
        assertThat(result.yearsExperience()).isEmpty();
        assertThat(result.warnings()).contains("Several roles were present; the most recent identifiable role was used.");
    }

    @Test
    void omitsImplausibleYearsAndParagraphLengthSkills() {
        var candidate = new ProfessionalProfile("Software Architect", "", "", "",
                "Designs dependable systems and makes technical decisions with delivery teams.",
                List.of("Designs service boundaries"), List.of("Java", "x".repeat(130)), List.of(),
                List.of(), List.of(), List.of(), OptionalInt.of(99), ProfessionalProfileCompleteness.PARTIAL,
                List.of());

        var result = validator.validate(candidate);

        assertThat(result.technicalSkills()).containsExactly("Java");
        assertThat(result.yearsExperience()).isEmpty();
        assertThat(result.warnings()).anyMatch(value -> value.contains("implausible"));
    }

    private ProfessionalProfile profile(String role, List<String> responsibilities) {
        return new ProfessionalProfile(role, "", "", "",
                "Builds and maintains dependable software services for operational business teams.",
                responsibilities, List.of("Java"), List.of(), List.of(), List.of(), List.of(),
                OptionalInt.empty(), ProfessionalProfileCompleteness.PARTIAL, List.of());
    }
}
