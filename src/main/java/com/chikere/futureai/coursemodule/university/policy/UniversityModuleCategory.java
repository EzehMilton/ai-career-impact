package com.chikere.futureai.coursemodule.university.policy;

/**
 * The primary {@code university-module-policy-v1} categories: what kind of learning or work a module appears to
 * involve. None says whether AI use is permitted, whether AI could do the module, or anything about employment.
 */
public enum UniversityModuleCategory {
    AI_ASSISTED_LEARNING("Good opportunity for AI-supported learning"),
    AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION("AI may help with learning, but your own capability remains central"),
    HUMAN_JUDGEMENT_LED("Human judgement remains central"),
    PRACTICAL_OR_EXPERIENTIAL("Mainly practical or experiential"),
    TECHNICAL_CORE("Strong technical or quantitative core"),
    MIXED("Mixed learning profile");

    private final String customerLabel;

    UniversityModuleCategory(String customerLabel) {
        this.customerLabel = customerLabel;
    }

    public String customerLabel() {
        return customerLabel;
    }
}
