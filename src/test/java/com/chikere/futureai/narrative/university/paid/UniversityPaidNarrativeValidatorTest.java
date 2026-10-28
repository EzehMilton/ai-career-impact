package com.chikere.futureai.narrative.university.paid;

import com.chikere.futureai.trace.NarrativeRuleViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The deterministic university-paid-narrative-v1 guardrail: conservative pattern rules, one per forbidden claim. */
class UniversityPaidNarrativeValidatorTest {

    private final UniversityPaidNarrativeValidator validator = new UniversityPaidNarrativeValidator();

    static UniversityPaidNarrativeContext context(boolean professionalStandardsRelevant) {
        return new UniversityPaidNarrativeContext(UUID.randomUUID(), UUID.randomUUID(), "BSc Nursing (Adult)",
                List.of(new UniversityPaidNarrativeContext.Measure("Working Alongside AI", "MODERATE", List.of(),
                                List.of()),
                        new UniversityPaidNarrativeContext.Measure("Human and Practical Capability", "HIGH", List.of(),
                                List.of()),
                        new UniversityPaidNarrativeContext.Measure("Transferable Capability", "HIGH", List.of(),
                                List.of())),
                List.of(new UniversityPaidNarrativeContext.Module("Year 2 Clinical Placement",
                        "Mainly practical or experiential")),
                List.of("Clinical reasoning"), List.of("Check your institution's rules"), List.of("Placement practice"),
                professionalStandardsRelevant);
    }

    static UniversityPaidNarrative clean() {
        return new UniversityPaidNarrative(
                "Your course shows a moderate Working Alongside AI profile alongside high Human and Practical Capability.",
                "AI may help you prepare for and revise the theory behind Year 2 Clinical Placement.",
                "Much of the course asks you to apply judgement in person, with patients and colleagues.",
                "The communication and reasoning you build here carry into many settings.",
                "The plan below turns these findings into steps for now, the coming months and later study.");
    }

    @Test
    void acceptsCleanProseIncludingNumbersInsideModuleTitles() {
        assertThat(validator.validate(clean(), context(false))).isEqualTo(clean());
    }

    @Test
    void rejectsAMissingField() {
        var missing = new UniversityPaidNarrative(" ", clean().aiLearningPerspective(),
                clean().humanPracticalPerspective(), clean().transferablePerspective(),
                clean().actionPlanIntroduction());
        assertRule(missing, false, UniversityPaidNarrativeValidator.PRESENT);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Your Working Alongside AI score is 6.2 out of ten.|NO_NUMBERS_OR_SCORES",
            "Graduates of this course earn a good salary.|NO_SALARY_OR_PAY",
            "This course will get you a job in the NHS.|NO_EMPLOYMENT_OR_REPLACEMENT_PREDICTION",
            "Nursing roles will never be replaced; AI cannot care for patients.|NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO",
            "This course is AI-proof.|NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO",
            "You are allowed to use AI in your coursework.|NO_AI_PERMISSION_CLAIM",
            "Your Future Readiness is strong.|ONLY_THE_THREE_COURSE_PROFILE_MEASURES",
            "Your course shows low Transferable Capability.|BANDS_MATCH_THE_COURSE_PROFILE",
            "Human and Practical Capability is your strongest measure.|NO_OVERALL_SCORE_OR_RANKING",
            "Because nursing is regulated, standards matter.|NO_UNSUPPORTED_REGULATION_CLAIM"})
    void rejectsEachForbiddenClaim(String overall, String rule) {
        assertRule(withOverall(overall), false, rule);
    }

    @Test
    void allowsRegulationOnlyWhenProfessionalStandardsAreRelevant() {
        var regulated = withOverall("Because nursing is regulated, professional standards shape much of the course.");
        assertThat(validator.validate(regulated, context(true))).isEqualTo(regulated);
    }

    @Test
    void everyRuleExceptAMissingNarrativeIsRepairable() {
        assertThat(UniversityPaidNarrativeValidator.REPAIRABLE)
                .doesNotContain(UniversityPaidNarrativeValidator.PRESENT)
                .hasSize(UniversityPaidNarrativeValidator.RULES.size() - 1);
    }

    private void assertRule(UniversityPaidNarrative narrative, boolean standards, String rule) {
        assertThatThrownBy(() -> validator.validate(narrative, context(standards)))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation ->
                        assertThat(violation.rule()).isEqualTo(rule));
    }

    static UniversityPaidNarrative withOverall(String overall) {
        var base = clean();
        return new UniversityPaidNarrative(overall, base.aiLearningPerspective(), base.humanPracticalPerspective(),
                base.transferablePerspective(), base.actionPlanIntroduction());
    }
}
