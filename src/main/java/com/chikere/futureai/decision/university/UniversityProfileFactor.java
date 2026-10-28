package com.chikere.futureai.decision.university;

import java.util.Objects;

/**
 * One dimension's signed effect on a {@code university-decision-v2} measure, from that measure's own formula:
 * {@code effect = 10 x weight x (score / 4 - 0.5)}. A measure's effects sum to its score minus 5.
 */
public record UniversityProfileFactor(String dimensionId, double score, double weight, double effect) {

    public UniversityProfileFactor {
        Objects.requireNonNull(dimensionId, "dimensionId must not be null");
        if (dimensionId.isBlank()) {
            throw new IllegalArgumentException("dimensionId must not be blank");
        }
        requireRange(score, 0.0, 4.0, "score");
        requireRange(weight, 0.0, 1.0, "weight");
        requireRange(effect, -5.0, 5.0, "effect");
    }

    private static void requireRange(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}
