package com.chikere.futureai.responsibility.professional.application;

/** Where responsibility judgments came from. */
public enum ResponsibilityJudgmentSource {
    JEV,
    /** Synthetic, hand-authored data for automated tests and local development. Never a real provider result. */
    SYNTHETIC_FIXTURE
}
