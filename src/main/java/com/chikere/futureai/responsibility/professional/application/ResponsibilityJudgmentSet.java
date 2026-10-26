package com.chikere.futureai.responsibility.professional.application;

import java.util.Objects;

/**
 * The judgments for one selected responsibility, exactly as provided. {@code describedClearly} is the provider's
 * 0-1 answer to "is this description specific enough to judge?": 0 means too vague, 1 clearly specific. It is
 * reported as it is; deciding what counts as too vague belongs to the classification policy, not here.
 */
public record ResponsibilityJudgmentSet(
        int responsibilityIndex,
        String responsibilityKey,
        String responsibilityText,
        double describedClearly,
        ResponsibilityScoredJudgment assistanceScope,
        ResponsibilityScoredJudgment contextualJudgement,
        ResponsibilityScoredJudgment humanInteraction,
        ResponsibilityScoredJudgment physicalPresence,
        ResponsibilityScoredJudgment outcomeAccountability) {

    public ResponsibilityJudgmentSet {
        if (!ProfessionalResponsibilityAnalysisRequest.key(responsibilityIndex).equals(responsibilityKey)) {
            throw new IllegalArgumentException("responsibility key must match its index");
        }
        if (responsibilityText == null || responsibilityText.isBlank()) {
            throw new IllegalArgumentException("responsibilityText must not be blank");
        }
        if (!Double.isFinite(describedClearly) || describedClearly < 0 || describedClearly > 1) {
            throw new IllegalArgumentException("describedClearly must be finite and between 0 and 1");
        }
        Objects.requireNonNull(assistanceScope, "assistanceScope must not be null");
        Objects.requireNonNull(contextualJudgement, "contextualJudgement must not be null");
        Objects.requireNonNull(humanInteraction, "humanInteraction must not be null");
        Objects.requireNonNull(physicalPresence, "physicalPresence must not be null");
        Objects.requireNonNull(outcomeAccountability, "outcomeAccountability must not be null");
    }
}
