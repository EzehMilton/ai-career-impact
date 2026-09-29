package com.chikere.futureai.judgment.domain;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

public record DimensionJudgment(
        String dimensionId,
        String name,
        OptionalDouble numericValue,
        Optional<String> categoricalValue,
        Optional<AssessmentConfidence> confidence,
        Map<String, Double> probabilityDistribution) {

    private static final double PROBABILITY_TOLERANCE = 1.0e-9;

    public DimensionJudgment {
        dimensionId = requiredText(dimensionId, "dimensionId");
        name = requiredText(name, "name");
        Objects.requireNonNull(numericValue, "numericValue must not be null");
        if (numericValue.isPresent() && !Double.isFinite(numericValue.getAsDouble())) {
            throw new IllegalArgumentException("numericValue must be finite");
        }
        Objects.requireNonNull(categoricalValue, "categoricalValue must not be null");
        categoricalValue = categoricalValue.map(value -> requiredText(value, "categoricalValue"));
        Objects.requireNonNull(confidence, "confidence must not be null");
        probabilityDistribution = immutableProbabilityDistribution(probabilityDistribution);

        if (numericValue.isEmpty() && categoricalValue.isEmpty() && probabilityDistribution.isEmpty()) {
            throw new IllegalArgumentException("a judgment must contain a value or probability distribution");
        }
    }

    private static Map<String, Double> immutableProbabilityDistribution(Map<String, Double> probabilities) {
        Objects.requireNonNull(probabilities, "probabilityDistribution must not be null");
        LinkedHashMap<String, Double> copy = new LinkedHashMap<>();
        probabilities.forEach((category, probability) -> {
            String validCategory = requiredText(category, "probability category");
            Objects.requireNonNull(probability, "probability must not be null");
            if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
                throw new IllegalArgumentException("probability must be finite and between 0 and 1");
            }
            copy.put(validCategory, probability);
        });
        if (!copy.isEmpty()) {
            double total = copy.values().stream().mapToDouble(Double::doubleValue).sum();
            if (Math.abs(total - 1.0) > PROBABILITY_TOLERANCE) {
                throw new IllegalArgumentException("probabilityDistribution must sum to 1");
            }
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String requiredText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value.strip();
    }
}
