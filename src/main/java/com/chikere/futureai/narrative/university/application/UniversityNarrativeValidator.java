package com.chikere.futureai.narrative.university.application;

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
 */
public final class UniversityNarrativeValidator {

    private static final List<String> RULES = List.of("NARRATIVE_PRESENT", "WELL_FORMED_STRUCTURED_OUTPUT",
            "NO_NUMERIC_SCORES_OR_PERCENTAGES", "NO_SALARY_OR_PAY_FIGURES",
            "NO_UNSUPPORTED_EMPLOYMENT_OR_QUALITY_CLAIMS");

    // No trailing \\b: a percent sign is not a word character, so "92%" would otherwise slip through.
    private static final Pattern NUMERIC_CLAIM = Pattern.compile(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:%|/\\s*10\\b|out\\s+of\\s+10\\b)");

    /** Fabricated salary or pay figures. */
    private static final Pattern CURRENCY_CLAIM = Pattern.compile(
            "(?i)(?:[\\u00A3$\\u20AC]\\s?\\d)|\\b\\d[\\d,.]*\\s*(?:k|thousand)?\\s*(?:pounds|gbp|usd|euros)\\b");

    /** Invented labour-market statistics and employment guarantees. */
    private static final Pattern UNSUPPORTED_CLAIM = Pattern.compile(
            "(?i)\\b(?:future[-\\s]?proof"
                    + "|employment\\s+rate|graduate\\s+(?:outcome|employment|salary)\\s+(?:rate|figure|statistic)"
                    + "|guarantee(?:s|d)?\\s+(?:you|a|your|employment|a\\s+job|work)"
                    + "|will\\s+(?:definitely|certainly)\\s+(?:get|find)\\s+(?:a\\s+job|work)"
                    + "|(?:graduates|students)\\s+will\\s+(?:find|secure|get)\\s+(?:work|jobs?|employment)"
                    + "|(?:employers\\s+will\\s+hire|graduates\\s+are\\s+in\\s+demand)"
                    + "|bad\\s+degree|worthless\\s+degree"
                    + "|you\\s+will\\s+struggle\\s+to\\s+find\\s+work)\\b");

    private final AssessmentTrace trace;

    public UniversityNarrativeValidator() {
        this(AssessmentTrace.disabled());
    }

    /** The trace records each rule's outcome; the rules and their order are unchanged. */
    public UniversityNarrativeValidator(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public UniversityNarrative validate(UniversityNarrative narrative) {
        NarrativeRuleChecks checks = NarrativeRuleChecks.start(trace, JourneyType.UNIVERSITY, RULES);
        checks.check("NARRATIVE_PRESENT", narrative == null, () -> null, "generated narrative must not be null");
        checks.check("WELL_FORMED_STRUCTURED_OUTPUT", false, () -> null,
                "enforced when the provider response was mapped onto the narrative record");
        List<String> text = new ArrayList<>(List.of(
                narrative.headline(), narrative.summary(), narrative.whatThisMeans(),
                narrative.futureReadinessExplanation(), narrative.strengthsExplanation(),
                narrative.developmentOpportunitiesExplanation(), narrative.complementarityExplanation(),
                narrative.transferableSkillsExplanation(), narrative.careerAdaptabilityExplanation(),
                narrative.cautionOrLimitations()));
        text.addAll(narrative.practicalActions());
        checks.check("NO_NUMERIC_SCORES_OR_PERCENTAGES",
                text.stream().anyMatch(value -> NUMERIC_CLAIM.matcher(value).find()),
                () -> NarrativeRuleChecks.firstMatch(NUMERIC_CLAIM, text),
                "narrative must not repeat numeric scores or percentage claims");
        checks.check("NO_SALARY_OR_PAY_FIGURES", text.stream().anyMatch(value -> CURRENCY_CLAIM.matcher(value).find()),
                () -> NarrativeRuleChecks.firstMatch(CURRENCY_CLAIM, text),
                "narrative must not state salary or pay figures");
        checks.check("NO_UNSUPPORTED_EMPLOYMENT_OR_QUALITY_CLAIMS",
                text.stream().anyMatch(value -> UNSUPPORTED_CLAIM.matcher(value).find()),
                () -> NarrativeRuleChecks.firstMatch(UNSUPPORTED_CLAIM, text),
                "narrative must not make unsupported employment or quality claims");
        checks.accepted();
        return narrative;
    }
}
