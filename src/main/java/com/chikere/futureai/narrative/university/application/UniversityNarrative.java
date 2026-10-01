package com.chikere.futureai.narrative.university.application;

import java.util.List;

/** Vendor-neutral, HTML-free explanation of an already-decided university assessment. */
public record UniversityNarrative(
        String headline,
        String summary,
        String whatThisMeans,
        String futureReadinessExplanation,
        String strengthsExplanation,
        String developmentOpportunitiesExplanation,
        String complementarityExplanation,
        String transferableSkillsExplanation,
        String careerAdaptabilityExplanation,
        List<String> practicalActions,
        String cautionOrLimitations) {

    public static final int MIN_ACTIONS = 3;
    public static final int MAX_ACTIONS = 5;

    public UniversityNarrative {
        headline = required(headline, "headline");
        summary = required(summary, "summary");
        whatThisMeans = required(whatThisMeans, "whatThisMeans");
        futureReadinessExplanation = required(futureReadinessExplanation, "futureReadinessExplanation");
        strengthsExplanation = required(strengthsExplanation, "strengthsExplanation");
        developmentOpportunitiesExplanation =
                required(developmentOpportunitiesExplanation, "developmentOpportunitiesExplanation");
        complementarityExplanation = required(complementarityExplanation, "complementarityExplanation");
        transferableSkillsExplanation = required(transferableSkillsExplanation, "transferableSkillsExplanation");
        careerAdaptabilityExplanation = required(careerAdaptabilityExplanation, "careerAdaptabilityExplanation");
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
