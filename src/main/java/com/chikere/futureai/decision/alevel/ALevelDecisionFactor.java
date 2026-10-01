package com.chikere.futureai.decision.alevel;

import java.util.Objects;

/** One dimension's signed contribution to the deterministic A-level explanation. */
public record ALevelDecisionFactor(
        String dimensionId,
        ALevelFactorEffect effect,
        ALevelFactorDirection direction,
        double score,
        double optionalityComponent,
        double weight,
        double signedEffect) {

    public ALevelDecisionFactor {
        Objects.requireNonNull(dimensionId, "dimensionId must not be null");
        if (dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
        dimensionId = dimensionId.strip();
        Objects.requireNonNull(effect, "effect must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        requireRange(score, 0.0, 4.0, "score");
        requireRange(optionalityComponent, 0.0, 1.0, "optionalityComponent");
        requireRange(weight, 0.0, 1.0, "weight");
        requireRange(signedEffect, -0.5, 0.5, "signedEffect");
        if (effect == ALevelFactorEffect.STRENGTH && signedEffect <= 0.0
                || effect == ALevelFactorEffect.DEVELOPMENT_OPPORTUNITY && signedEffect >= 0.0) {
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
