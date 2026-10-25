package com.chikere.futureai.narrative.alevel.application;

import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.decision.alevel.OptionalityBand;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleChecks;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Small deterministic guardrail applied after typed provider mapping.
 *
 * <p>It is a backstop for the prompt contract, not a substitute for it. Anything it rejects fails
 * the narrative attempt; it can never change the saved assessment.</p>
 *
 * <p>The prerequisite guard matters most here. A-level subject requirements for specific courses
 * are factual information that belongs in the evidence layer with a traceable source. A narrative
 * that states one has invented it.</p>
 */
public final class ALevelNarrativeValidator {

    private static final List<String> TEXT_RULES = List.of("NARRATIVE_PRESENT", "WELL_FORMED_STRUCTURED_OUTPUT",
            "NO_NUMERIC_SCORES_OR_PERCENTAGES", "NO_SALARY_OR_PAY_FIGURES", "NO_UNIVERSITY_ENTRY_REQUIREMENTS",
            "NO_UNSUPPORTED_ADMISSION_EMPLOYMENT_OR_SUBJECT_VERDICT_CLAIMS");
    private static final List<String> CONTEXT_RULES = java.util.stream.Stream.concat(TEXT_RULES.stream(),
            java.util.stream.Stream.of("PRESERVES_BALANCED_HUMAN_AND_CREATIVE_BAND")).toList();

    /** No trailing \b: a percent sign is not a word character, so "92%" would otherwise slip through. */
    private static final Pattern NUMERIC_CLAIM = Pattern.compile(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:%|/\\s*10\\b|out\\s+of\\s+10\\b)");

    /** Fabricated salary or pay figures. */
    private static final Pattern CURRENCY_CLAIM = Pattern.compile(
            "(?i)(?:[\\u00A3$\\u20AC]\\s?\\d)|\\b\\d[\\d,.]*\\s*(?:k|thousand)?\\s*(?:pounds|gbp|usd|euros)\\b");

    /** Invented entry requirements and admissions promises. */
    private static final Pattern PREREQUISITE_CLAIM = Pattern.compile(
            "(?i)\\b(?:requires?\\s+(?:a[- ]level\\s+)?(?:mathematics|maths|chemistry|physics|biology)"
                    + "|entry\\s+requirements?\\s+(?:are|is|include)"
                    + "|you\\s+(?:will\\s+)?need\\s+(?:a[- ]level\\s+)?(?:mathematics|maths|chemistry|physics|biology)"
                    + "|universit(?:y|ies)\\s+require"
                    + "|aaa|aab|abb|bbb)\\b");

    /** Admission or employment promises, verdicts on subjects, and drop/change instructions. */
    private static final Pattern UNSUPPORTED_CLAIM = Pattern.compile(
            "(?i)\\b(?:future[-\\s]?proof"
                    + "|guarantee(?:s|d)?\\s+(?:you|a|your|admission|a\\s+place|entry|employment|a\\s+job)"
                    + "|will\\s+(?:definitely|certainly)\\s+(?:get|be)\\s+(?:in|accepted|offered)"
                    + "|employment\\s+rate|graduate\\s+salary"
                    + "|you\\s+should\\s+drop|drop\\s+(?:geography|history|art|music|sociology)"
                    + "|wrong\\s+(?:subjects?|combination)|bad\\s+(?:subjects?|combination)"
                    + "|you\\s+(?:won't|will\\s+not)\\s+get\\s+into"
                    + "|limit\\s+your\\s+future)\\b");

    // Scoped to the matching explanation field; "strong" may legitimately describe another skill.
    private static final Pattern STRONG_HUMAN_CREATIVE = Pattern.compile(
            "(?i)\\b(?:strong\\s+human\\s+(?:and|&)\\s+creative"
                    + "|human\\s+(?:and|&)\\s+creative\\s+(?:aspect|strength|capabilit(?:y|ies))"
                    + "\\s+(?:is|are|appears?\\s+to\\s+be)\\s+strong)\\b");

    private final AssessmentTrace trace;

    public ALevelNarrativeValidator() {
        this(AssessmentTrace.disabled());
    }

    /** The trace records each rule's outcome; the rules and their order are unchanged. */
    public ALevelNarrativeValidator(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public ALevelNarrative validate(ALevelNarrative narrative, ALevelNarrativeContext context) {
        NarrativeRuleChecks checks = NarrativeRuleChecks.start(trace, JourneyType.A_LEVEL, CONTEXT_RULES);
        validateText(narrative, checks);
        checks.check("PRESERVES_BALANCED_HUMAN_AND_CREATIVE_BAND", context != null
                        && context.measures().stream().anyMatch(measure ->
                                measure.measure() == ALevelMeasure.HUMAN_AND_CREATIVE_STRENGTH
                                        && measure.band() == OptionalityBand.BALANCED)
                        && STRONG_HUMAN_CREATIVE.matcher(narrative.humanAndCreativeExplanation()).find(),
                () -> NarrativeRuleChecks.firstMatch(STRONG_HUMAN_CREATIVE,
                        List.of(narrative.humanAndCreativeExplanation())),
                "narrative must preserve the BALANCED human and creative band");
        checks.accepted();
        return narrative;
    }

    public ALevelNarrative validate(ALevelNarrative narrative) {
        NarrativeRuleChecks checks = NarrativeRuleChecks.start(trace, JourneyType.A_LEVEL, TEXT_RULES);
        validateText(narrative, checks);
        checks.accepted();
        return narrative;
    }

    private void validateText(ALevelNarrative narrative, NarrativeRuleChecks checks) {
        checks.check("NARRATIVE_PRESENT", narrative == null, () -> null, "generated narrative must not be null");
        checks.check("WELL_FORMED_STRUCTURED_OUTPUT", false, () -> null,
                "enforced when the provider response was mapped onto the narrative record");
        List<String> text = new ArrayList<>(List.of(
                narrative.headline(), narrative.summary(), narrative.whatThisMeans(),
                narrative.futureOptionalityExplanation(), narrative.strengthsExplanation(),
                narrative.degreePathwayExplanation(), narrative.analyticalFoundationExplanation(),
                narrative.humanAndCreativeExplanation(), narrative.transferabilityExplanation(),
                narrative.adaptabilityExplanation(), narrative.developmentOpportunitiesExplanation(),
                narrative.cautionOrLimitations()));
        text.addAll(narrative.practicalActions());
        checks.check("NO_NUMERIC_SCORES_OR_PERCENTAGES",
                text.stream().anyMatch(value -> NUMERIC_CLAIM.matcher(value).find()),
                () -> NarrativeRuleChecks.firstMatch(NUMERIC_CLAIM, text),
                "narrative must not repeat numeric scores or percentage claims");
        checks.check("NO_SALARY_OR_PAY_FIGURES", text.stream().anyMatch(value -> CURRENCY_CLAIM.matcher(value).find()),
                () -> NarrativeRuleChecks.firstMatch(CURRENCY_CLAIM, text),
                "narrative must not state salary or pay figures");
        checks.check("NO_UNIVERSITY_ENTRY_REQUIREMENTS",
                text.stream().anyMatch(value -> PREREQUISITE_CLAIM.matcher(value).find()),
                () -> NarrativeRuleChecks.firstMatch(PREREQUISITE_CLAIM, text),
                "narrative must not state university entry requirements; those belong in evidence");
        checks.check("NO_UNSUPPORTED_ADMISSION_EMPLOYMENT_OR_SUBJECT_VERDICT_CLAIMS",
                text.stream().anyMatch(value -> UNSUPPORTED_CLAIM.matcher(value).find()),
                () -> NarrativeRuleChecks.firstMatch(UNSUPPORTED_CLAIM, text),
                "narrative must not make unsupported admission, employment or subject-verdict claims");
    }
}
