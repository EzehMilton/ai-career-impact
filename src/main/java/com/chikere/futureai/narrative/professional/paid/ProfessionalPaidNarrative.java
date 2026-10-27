package com.chikere.futureai.narrative.professional.paid;

import java.util.List;

/**
 * The structured explanation the model returns. Every list is keyed by identifiers copied from the deterministic
 * guidance, in its order; the validator rejects any missing, extra, unknown or reordered identifier. No field
 * carries a score, category, ranking or recommendation of its own.
 *
 * <p>Deliberately without constructor validation: a malformed provider response must reach the validator (and its
 * single repair attempt) rather than fail while it is being mapped.</p>
 */
public record ProfessionalPaidNarrative(
        String executiveSummary,
        String scoresTogether,
        String responsibilityPattern,
        List<ItemExplanation> opportunityExplanations,
        List<ItemExplanation> humanValueExplanations,
        String skillDevelopment,
        List<ItemExplanation> skillExplanations,
        String planIntroduction,
        List<ItemExplanation> planActionNotes,
        String closingPerspective) {

    /** One explanation for exactly one deterministic item, identified by the id supplied in the prompt. */
    public record ItemExplanation(String id, String explanation) {
    }
}
