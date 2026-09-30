package com.chikere.futureai.narrative.professional.application;

import java.util.List;

/** Vendor-neutral, HTML-free explanation of an already-decided assessment. */
public record ProfessionalNarrative(
        String headline,
        String summary,
        String whatThisMeans,
        String exposureExplanation,
        String humanAdvantageExplanation,
        String complementarityExplanation,
        String adaptabilityExplanation,
        List<String> practicalActions,
        String cautionOrLimitations) {

    public static final int MIN_ACTIONS = 3;
    public static final int MAX_ACTIONS = 5;

    public ProfessionalNarrative {
        headline = required(headline, "headline");
        summary = required(summary, "summary");
        whatThisMeans = required(whatThisMeans, "whatThisMeans");
        exposureExplanation = required(exposureExplanation, "exposureExplanation");
        humanAdvantageExplanation = required(humanAdvantageExplanation, "humanAdvantageExplanation");
        complementarityExplanation = required(complementarityExplanation, "complementarityExplanation");
        adaptabilityExplanation = required(adaptabilityExplanation, "adaptabilityExplanation");
        cautionOrLimitations = required(cautionOrLimitations, "cautionOrLimitations");
        if (practicalActions == null
                || practicalActions.size() < MIN_ACTIONS
                || practicalActions.size() > MAX_ACTIONS) {
            throw new IllegalArgumentException("practicalActions must contain between 3 and 5 items");
        }
        practicalActions = practicalActions.stream()
                .map(action -> required(action, "practical action"))
                .toList();
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
