package com.chikere.futureai.narrative.alevel.application;

import java.util.ArrayList;
import java.util.List;
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

    public ALevelNarrative validate(ALevelNarrative narrative) {
        if (narrative == null) {
            throw new IllegalArgumentException("generated narrative must not be null");
        }
        List<String> text = new ArrayList<>(List.of(
                narrative.headline(), narrative.summary(), narrative.whatThisMeans(),
                narrative.futureOptionalityExplanation(), narrative.strengthsExplanation(),
                narrative.degreePathwayExplanation(), narrative.analyticalFoundationExplanation(),
                narrative.humanAndCreativeExplanation(), narrative.transferabilityExplanation(),
                narrative.adaptabilityExplanation(), narrative.developmentOpportunitiesExplanation(),
                narrative.cautionOrLimitations()));
        text.addAll(narrative.practicalActions());
        if (text.stream().anyMatch(value -> NUMERIC_CLAIM.matcher(value).find())) {
            throw new IllegalArgumentException("narrative must not repeat numeric scores or percentage claims");
        }
        if (text.stream().anyMatch(value -> CURRENCY_CLAIM.matcher(value).find())) {
            throw new IllegalArgumentException("narrative must not state salary or pay figures");
        }
        if (text.stream().anyMatch(value -> PREREQUISITE_CLAIM.matcher(value).find())) {
            throw new IllegalArgumentException(
                    "narrative must not state university entry requirements; those belong in evidence");
        }
        if (text.stream().anyMatch(value -> UNSUPPORTED_CLAIM.matcher(value).find())) {
            throw new IllegalArgumentException(
                    "narrative must not make unsupported admission, employment or subject-verdict claims");
        }
        return narrative;
    }
}
