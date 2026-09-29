package com.chikere.futureai.decision.professional;

import java.util.Objects;

public record ProfessionalDecisionFactor(
        String dimensionId,
        DecisionFactorEffect effect,
        ProfessionalFactorDirection direction,
        double score,
        double exposureComponent,
        double weight,
        double signedEffect) {

    public ProfessionalDecisionFactor {
        Objects.requireNonNull(dimensionId, "dimensionId must not be null");
        if (dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
        dimensionId = dimensionId.strip();
        Objects.requireNonNull(effect, "effect must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
        requireRange(score, 0.0, 4.0, "score");
        requireRange(exposureComponent, 0.0, 1.0, "exposureComponent");
        requireRange(weight, 0.0, 1.0, "weight");
        requireRange(signedEffect, -0.5, 0.5, "signedEffect");
        if (effect == DecisionFactorEffect.INCREASES_AI_EXPOSURE && signedEffect <= 0.0
                || effect == DecisionFactorEffect.REDUCES_AI_EXPOSURE && signedEffect >= 0.0) {
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
