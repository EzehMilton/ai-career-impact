package com.chikere.futureai.decision.alevel;

import java.util.Objects;

public record ALevelMeasureResult(
        ALevelMeasure measure,
        double indicator,
        OptionalityBand band,
        double confidence) {

    public ALevelMeasureResult {
        Objects.requireNonNull(measure, "measure must not be null");
        Objects.requireNonNull(band, "band must not be null");
        requireRange(indicator, 0.0, 10.0, "indicator");
        requireRange(confidence, 0.0, 1.0, "confidence");
    }

    private static void requireRange(double value, double minimum, double maximum, String fieldName) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite and between " + minimum + " and " + maximum);
        }
    }
}
