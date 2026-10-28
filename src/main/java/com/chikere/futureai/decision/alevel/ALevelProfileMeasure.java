package com.chikere.futureai.decision.alevel;

/**
 * The scored dimensions of {@code a-level-decision-v2}. They are deliberately separate from the six v1
 * {@link ALevelMeasure}s, which historical assessments keep. There is no composite headline measure.
 *
 * <p>The two capability-profile dimensions form one customer-facing card and are never added together or ranked
 * against each other: different combinations build different strengths.</p>
 */
public enum ALevelProfileMeasure {
    OPTIONS_KEPT_OPEN("Options Kept Open"),
    ANALYTICAL_AND_QUANTITATIVE("Analytical & Quantitative"),
    COMMUNICATION_INTERPRETATION_AND_CONTEXT("Communication, Interpretation & Context"),
    LEARNING_ALONGSIDE_AI("Learning Alongside AI");

    private final String displayName;

    ALevelProfileMeasure(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** The two capability-profile sides explain only what builds them, never a limit. */
    public boolean capabilityProfile() {
        return this == ANALYTICAL_AND_QUANTITATIVE || this == COMMUNICATION_INTERPRETATION_AND_CONTEXT;
    }
}
