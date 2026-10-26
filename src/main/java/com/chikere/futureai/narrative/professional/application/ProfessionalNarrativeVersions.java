package com.chikere.futureai.narrative.professional.application;

public final class ProfessionalNarrativeVersions {
    /**
     * v7: the repair request for a categorical AI-capability claim forbids rewording the capability or replacement
     * idea and offers neutral alternatives. The first-draft prompt is unchanged from v6, but a repaired narrative
     * is produced by a different contract, so stored narratives must say which one.
     * v6: factor lists are explained as material factors only; an empty list is meaningful, no factor is invented to
     * fill a side, and with both lists empty the result is not attributed to any specific factor.
     * v5: provider input reduced to customer-facing meaning (no dimension ids, direction or effect codes), and an
     * explicit role-level contract: no claims that tasks or the work can be automated or performed by AI.
     * v4: leads the AI Exposure explanation with the dominant factor, plain language instead of internal dimension
     * names, safety and regulated-procedure guardrails, a narrower provider input, and one validated repair attempt.
     * v6, v5, v4, v3 and v2 remain the identifiers of narratives saved under earlier prompts.
     */
    public static final String PROMPT_AND_SCHEMA = "professional-narrative-v7";

    private ProfessionalNarrativeVersions() {
    }
}
