package com.chikere.futureai.narrative.professional.application;

public final class ProfessionalNarrativeVersions {
    /**
     * v5: provider input reduced to customer-facing meaning (no dimension ids, direction or effect codes), and an
     * explicit role-level contract: no claims that tasks or the work can be automated or performed by AI.
     * v4: leads the AI Exposure explanation with the dominant factor, plain language instead of internal dimension
     * names, safety and regulated-procedure guardrails, a narrower provider input, and one validated repair attempt.
     * v4, v3 and v2 remain the identifiers of narratives saved under earlier prompts.
     */
    public static final String PROMPT_AND_SCHEMA = "professional-narrative-v5";

    private ProfessionalNarrativeVersions() {
    }
}
