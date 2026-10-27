package com.chikere.futureai.responsibility.professional.policy;

/** The pattern of one responsibility. Each label describes scope for assistance, never automation or safety. */
public enum ResponsibilityCategory {
    GOOD_AI_ASSISTANCE_OPPORTUNITY("Good opportunity for AI assistance"),
    AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT("AI may assist with parts"),
    HUMAN_LED_AI_SUPPORTING("Human-led, with AI support"),
    MAINLY_HANDS_ON_OR_IN_PERSON("Mainly hands-on or in person");

    private final String label;

    ResponsibilityCategory(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
