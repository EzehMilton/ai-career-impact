package com.chikere.futureai.decision.professional;

import java.util.Objects;

public record ProfessionalDecisionFactor(
        String dimensionId,
        DecisionFactorEffect effect,
        double score,
        double normalisedEffect,
        double weight,
        double weightedContribution) {

    public ProfessionalDecisionFactor {
        Objects.requireNonNull(dimensionId, "dimensionId must not be null");
        if (dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
        dimensionId = dimensionId.strip();
        Objects.requireNonNull(effect, "effect must not be null");
        requireRange(score, 0.0, 4.0, "score");
        requireRange(normalisedEffect, 0.0, 1.0, "normalisedEffect");
        requireRange(weight, 0.0, 1.0, "weight");
        requireRange(weightedContribution, 0.0, 1.0, "weightedContribution");
    }

    private static void requireRange(double value, double minimum, double maximum, String fieldName) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}
