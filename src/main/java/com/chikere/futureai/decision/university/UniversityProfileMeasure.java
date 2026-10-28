package com.chikere.futureai.decision.university;

/**
 * The three {@code university-decision-v2} measures. Deliberately separate from the five v1
 * {@link UniversityMeasure}s, which historical assessments keep. There is no composite headline measure.
 */
public enum UniversityProfileMeasure {
    WORKING_ALONGSIDE_AI("Working Alongside AI"),
    HUMAN_AND_PRACTICAL_CAPABILITY("Human and Practical Capability"),
    TRANSFERABLE_CAPABILITY("Transferable Capability");

    private final String displayName;

    UniversityProfileMeasure(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
