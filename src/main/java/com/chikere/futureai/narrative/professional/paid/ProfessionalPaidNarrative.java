package com.chikere.futureai.narrative.professional.paid;

import java.util.List;

/**
 * The {@value ProfessionalPaidNarrativeVersions#V2} structured narrative. Every list is keyed by identifiers copied
 * from the deterministic guidance, in its order; the validator rejects any missing, extra, unknown or reordered
 * identifier. No field carries a score, category, ranking or recommendation of its own: the practical content
 * illustrates how a stored finding could be applied, it never changes the finding.
 *
 * <p>Deliberately without constructor validation: a malformed provider response must reach the validator (and its
 * single repair attempt) rather than fail while it is being mapped.</p>
 */
public record ProfessionalPaidNarrative(
        String executiveSummary,
        String scoresTogether,
        String responsibilityPattern,
        List<OpportunityInsight> opportunityInsights,
        List<HumanValueInsight> humanValueInsights,
        List<SkillInsight> skillInsights,
        String planIntroduction,
        List<ItemExplanation> planActionNotes,
        AdaptationStrategy adaptationStrategy,
        String closingPerspective) {

    /**
     * One stored AI-assistance opportunity made concrete: why it suits assistance, two to four illustrative uses
     * within that exact responsibility, one to three things you keep owning, and one small reversible experiment.
     */
    public record OpportunityInsight(String id, String explanation, List<String> practicalAiUses,
            List<String> humanOwnership, String firstExperiment) {
    }

    /** Why your contribution matters in one stored human-value area, and what to deliberately strengthen there. */
    public record HumanValueInsight(String id, String explanation, String strengthen) {
    }

    /** One stored skill family, with what it means in this person's own work. The family itself never changes. */
    public record SkillInsight(String id, String explanation, List<String> inYourWorkThisMeans) {
    }

    /** One explanation for exactly one deterministic item, identified by the id supplied in the prompt. */
    public record ItemExplanation(String id, String explanation) {
    }

    /** One to three short items under each heading, derived from the stored findings; never a prediction. */
    public record AdaptationStrategy(List<String> protect, List<String> learn, List<String> experiment,
            List<String> strengthen, List<String> monitor) {
    }
}
