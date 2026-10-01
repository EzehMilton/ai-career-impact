package com.chikere.futureai.decision.university;

import java.util.Objects;

/** One dimension's signed contribution to the deterministic university explanation. */
public record UniversityDecisionFactor(
        String dimensionId,
        UniversityFactorEffect effect,
        UniversityFactorDirection direction,
        double score,
        double readinessComponent,
        double weight,
        double signedEffect) {

    public UniversityDecisionFactor {
        Objects.requireNonNull(dimensionId, "dimensionId must not be null");
        if (dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
        dimensionId = dimensionId.strip();
        Objects.requireNonNull(effect, "effect must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        requireRange(score, 0.0, 4.0, "score");
        requireRange(readinessComponent, 0.0, 1.0, "readinessComponent");
        requireRange(weight, 0.0, 1.0, "weight");
        requireRange(signedEffect, -0.5, 0.5, "signedEffect");
        if (effect == UniversityFactorEffect.STRENGTH && signedEffect <= 0.0
                || effect == UniversityFactorEffect.DEVELOPMENT_OPPORTUNITY && signedEffect >= 0.0) {
            throw new IllegalArgumentException("effect must match signedEffect");
        }
    }

    private static void requireRange(double value, double minimum, double maximum, String fieldName) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}
