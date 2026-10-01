package com.chikere.futureai.narrative.alevel.application;

import java.util.List;

/** Vendor-neutral, HTML-free explanation of an already-decided A-level assessment. */
public record ALevelNarrative(
        String headline,
        String summary,
        String whatThisMeans,
        String futureOptionalityExplanation,
        String strengthsExplanation,
        String degreePathwayExplanation,
        String analyticalFoundationExplanation,
        String humanAndCreativeExplanation,
        String transferabilityExplanation,
        String adaptabilityExplanation,
        String developmentOpportunitiesExplanation,
        List<String> practicalActions,
        String cautionOrLimitations) {

    public static final int MIN_ACTIONS = 3;
    public static final int MAX_ACTIONS = 5;

    public ALevelNarrative {
        headline = required(headline, "headline");
        summary = required(summary, "summary");
        whatThisMeans = required(whatThisMeans, "whatThisMeans");
        futureOptionalityExplanation = required(futureOptionalityExplanation, "futureOptionalityExplanation");
        strengthsExplanation = required(strengthsExplanation, "strengthsExplanation");
        degreePathwayExplanation = required(degreePathwayExplanation, "degreePathwayExplanation");
        analyticalFoundationExplanation =
                required(analyticalFoundationExplanation, "analyticalFoundationExplanation");
        humanAndCreativeExplanation = required(humanAndCreativeExplanation, "humanAndCreativeExplanation");
        transferabilityExplanation = required(transferabilityExplanation, "transferabilityExplanation");
        adaptabilityExplanation = required(adaptabilityExplanation, "adaptabilityExplanation");
        developmentOpportunitiesExplanation =
                required(developmentOpportunitiesExplanation, "developmentOpportunitiesExplanation");
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
