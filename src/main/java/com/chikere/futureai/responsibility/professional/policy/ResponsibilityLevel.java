package com.chikere.futureai.responsibility.professional.policy;

/**
 * An explanatory label for a 0-4 responsibility judgment, named after the rubric anchor the value sits nearest:
 * Low is levels 0-1, Moderate level 2, High levels 3-4. Not a probability, confidence, exposure band or
 * automation score.
 */
public enum ResponsibilityLevel {
    LOW("Low"),
    MODERATE("Moderate"),
    HIGH("High");

    private final String label;

    ResponsibilityLevel(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
