package com.chikere.futureai.decision.alevel;

import java.util.List;
import java.util.Objects;

/**
 * One {@code a-level-decision-v2} measure.
 *
 * @param contributions every dimension in the measure's formula, largest absolute effect first
 * @param drivers at most two material positive effects
 * @param limitingFactors at most two material negative effects; always empty for a capability-profile side and in a
 *     measure's top band
 * @param lowerConfidence the weighted confidence of the measure's own dimensions is below the display threshold
 */
public record ALevelProfileMeasureResult(
        ALevelProfileMeasure measure,
        double indicator,
        ALevelProfileBand band,
        double confidence,
        boolean lowerConfidence,
        List<ALevelProfileFactor> contributions,
        List<ALevelProfileFactor> drivers,
        List<ALevelProfileFactor> limitingFactors) {

    public ALevelProfileMeasureResult {
        Objects.requireNonNull(measure, "measure must not be null");
        Objects.requireNonNull(band, "band must not be null");
        requireRange(indicator, 0.0, 10.0, "indicator");
        requireRange(confidence, 0.0, 1.0, "confidence");
        contributions = List.copyOf(Objects.requireNonNull(contributions, "contributions must not be null"));
        drivers = List.copyOf(Objects.requireNonNull(drivers, "drivers must not be null"));
        limitingFactors = List.copyOf(Objects.requireNonNull(limitingFactors, "limitingFactors must not be null"));
        if (drivers.stream().anyMatch(factor -> factor.effect() <= 0.0)
                || limitingFactors.stream().anyMatch(factor -> factor.effect() >= 0.0)) {
            throw new IllegalArgumentException("drivers must be positive and limiting factors negative");
        }
        if (measure.capabilityProfile() && !limitingFactors.isEmpty()) {
            throw new IllegalArgumentException("a capability-profile side never has limiting factors");
        }
    }

    private static void requireRange(double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}
