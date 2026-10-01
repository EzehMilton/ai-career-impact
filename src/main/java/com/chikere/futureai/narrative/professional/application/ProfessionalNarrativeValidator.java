package com.chikere.futureai.narrative.professional.application;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Small deterministic guardrail applied after typed provider mapping. */
public final class ProfessionalNarrativeValidator {

    // No trailing \\b: a percent sign is not a word character, so "92%" would otherwise slip through.
    private static final Pattern NUMERIC_CLAIM = Pattern.compile(
            "(?i)\\b\\d+(?:\\.\\d+)?\\s*(?:%|/\\s*10\\b|out\\s+of\\s+10\\b)");

    public ProfessionalNarrative validate(ProfessionalNarrative narrative) {
        if (narrative == null) {
            throw new IllegalArgumentException("generated narrative must not be null");
        }
        List<String> text = new ArrayList<>(List.of(
                narrative.headline(), narrative.summary(), narrative.whatThisMeans(),
                narrative.exposureExplanation(), narrative.humanAdvantageExplanation(),
                narrative.complementarityExplanation(), narrative.adaptabilityExplanation(),
                narrative.cautionOrLimitations()));
        text.addAll(narrative.practicalActions());
        if (text.stream().anyMatch(value -> NUMERIC_CLAIM.matcher(value).find())) {
            throw new IllegalArgumentException("narrative must not repeat numeric scores or percentage claims");
        }
        return narrative;
    }
}
