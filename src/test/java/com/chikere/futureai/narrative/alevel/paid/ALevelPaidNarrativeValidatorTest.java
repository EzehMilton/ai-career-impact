package com.chikere.futureai.narrative.alevel.paid;

import com.chikere.futureai.trace.NarrativeRuleViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The deterministic a-level-paid-narrative-v1 guardrail: one conservative rule per forbidden claim. */
class ALevelPaidNarrativeValidatorTest {

    private final ALevelPaidNarrativeValidator validator = new ALevelPaidNarrativeValidator();

    static ALevelPaidNarrativeContext context() {
        return new ALevelPaidNarrativeContext(UUID.randomUUID(), List.of("Mathematics", "Economics", "Geography"),
                List.of(new ALevelPaidNarrativeContext.Measure("Future study options", "broad", List.of(), List.of()),
                        new ALevelPaidNarrativeContext.Measure("Maths and problem-solving", "strong emphasis",
                                List.of(), List.of()),
                        new ALevelPaidNarrativeContext.Measure("Writing, ideas and the wider world",
                                "moderate emphasis", List.of(), List.of()),
                        new ALevelPaidNarrativeContext.Measure("How AI can help you learn", "high", List.of(), List.of())),
                List.of("Mathematical and statistical reasoning"), List.of(), List.of("Working without AI first"),
                List.of("Set one AI study habit"), List.of("Entry requirements vary"), List.of("Economics"),
                List.of("Finance"));
    }

    static ALevelPaidNarrative clean() {
        return new ALevelPaidNarrative(
                "Your subjects keep a broad range of future study options open across Mathematics, Economics and "
                        + "Geography.",
                "They place a strong emphasis on maths and problem-solving. They also develop writing, interpretation "
                        + "and an understanding of the wider world; neither area is better.",
                "You mentioned Economics and Finance. Check the published entry requirements of each course in Year 13 "
                        + "before you apply, including post-18 routes.",
                "AI may help you practise and check your understanding, but your own work comes first.",
                "The plan below sets out steps for now, the rest of sixth form and your A-level applications.");
    }

    static ALevelPaidNarrative withOptions(String options) {
        var base = clean();
        return new ALevelPaidNarrative(base.profileExplanation(), base.capabilityExplanation(), options,
                base.aiLearningExplanation(), base.actionPlanIntroduction());
    }

    @Test
    void acceptsCleanProseIncludingSubjectNamesYearThirteenAndPostEighteen() {
        assertThat(validator.validate(clean(), context())).isEqualTo(clean());
    }

    @Test
    void rejectsAMissingField() {
        assertRule(withOptions(" "), ALevelPaidNarrativeValidator.PRESENT);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Your profile score is 8.5 out of ten.|NO_NUMBERS_SCORES_OR_PERCENTAGES",
            "About 80% of students like this combination.|NO_NUMBERS_SCORES_OR_PERCENTAGES",
            "This combination guarantees you a place at university.|NO_ADMISSION_OR_ELIGIBILITY_CLAIM",
            "You will be accepted onto most courses.|NO_ADMISSION_OR_ELIGIBILITY_CLAIM",
            "These subjects make you eligible for medicine.|NO_ADMISSION_OR_ELIGIBILITY_CLAIM",
            "Economics degrees require A-level Mathematics.|NO_INVENTED_ENTRY_OR_GRADE_REQUIREMENT",
            "Entry requirements are usually AAA.|NO_INVENTED_ENTRY_OR_GRADE_REQUIREMENT",
            "Universities expect three sciences for this.|NO_INVENTED_ENTRY_OR_GRADE_REQUIREMENT",
            "Finance graduates earn a high salary.|NO_SALARY_OR_PAY",
            "This will get you a job in finance.|NO_EMPLOYMENT_OR_REPLACEMENT_PREDICTION",
            "AI cannot do economic reasoning.|NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO",
            "These subjects are AI-proof.|NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO",
            "You are allowed to use AI in your coursework.|NO_AI_PERMISSION_CLAIM",
            "Your Future Optionality is broad.|NO_SUPERSEDED_V1_TERMINOLOGY",
            "Your AI-Era Adaptability is strong.|NO_SUPERSEDED_V1_TERMINOLOGY",
            "Your Future study options measure is in the broad band.|NO_MECHANICAL_MEASURE_LANGUAGE",
            "It also shows broad Future study options and high potential for How AI can help you learn.|NO_MECHANICAL_MEASURE_LANGUAGE",
            "Your profile combines several useful measures.|NO_MECHANICAL_MEASURE_LANGUAGE",
            "This is the best combination for most students.|NO_OVERALL_SCORE_OR_RANKING",
            "Your overall score is excellent.|NO_OVERALL_SCORE_OR_RANKING",
            "You have focused Future study options.|BANDS_MATCH_THE_SUBJECT_PROFILE",
            "You have lighter emphasis on Maths and problem-solving.|BANDS_MATCH_THE_SUBJECT_PROFILE"})
    void rejectsEachForbiddenClaim(String options, String rule) {
        assertRule(withOptions(options), rule);
    }

    @Test
    void everyRuleExceptAMissingNarrativeIsRepairable() {
        assertThat(ALevelPaidNarrativeValidator.REPAIRABLE).doesNotContain(ALevelPaidNarrativeValidator.PRESENT)
                .hasSize(ALevelPaidNarrativeValidator.RULES.size() - 1);
    }

    private void assertRule(ALevelPaidNarrative narrative, String rule) {
        assertThatThrownBy(() -> validator.validate(narrative, context()))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation ->
                        assertThat(violation.rule()).isEqualTo(rule));
    }
}
