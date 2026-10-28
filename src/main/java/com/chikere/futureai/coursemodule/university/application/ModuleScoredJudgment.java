package com.chikere.futureai.coursemodule.university.application;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.TreeMap;

/**
 * One 0-4 module judgment as the provider returned it: the probability-weighted value, the probability of each
 * level, the provider's own confidence and the most likely level. Raw evidence only: no label, band or category is
 * derived here.
 */
public record ModuleScoredJudgment(
        double value,
        Map<Integer, Double> distribution,
        OptionalDouble providerConfidence,
        OptionalInt nearestLevel,
        Optional<String> nearestLabel) {

    public static final int MINIMUM_LEVEL = 0;
    public static final int MAXIMUM_LEVEL = 4;
    /**
     * As for the Professional responsibility-level contract: providers round probabilities, so a distribution may
     * sum to 1 within this tolerance. The course-level {@code university-v1} contract is unchanged.
     */
    public static final double DISTRIBUTION_TOLERANCE = 0.01;

    public ModuleScoredJudgment {
        if (!Double.isFinite(value) || value < MINIMUM_LEVEL || value > MAXIMUM_LEVEL) {
            throw new IllegalArgumentException("judgment value must be finite and between 0 and 4");
        }
        Objects.requireNonNull(distribution, "distribution must not be null");
        double total = 0;
        for (Map.Entry<Integer, Double> level : distribution.entrySet()) {
            Integer key = Objects.requireNonNull(level.getKey(), "distribution level must not be null");
            Double probability = Objects.requireNonNull(level.getValue(), "distribution probability must not be null");
            if (key < MINIMUM_LEVEL || key > MAXIMUM_LEVEL) {
                throw new IllegalArgumentException("distribution level must be between 0 and 4, was " + key);
            }
            if (!Double.isFinite(probability) || probability < 0 || probability > 1) {
                throw new IllegalArgumentException("distribution probability must be finite and between 0 and 1");
            }
            total += probability;
        }
        if (!distribution.isEmpty() && Math.abs(total - 1.0) > DISTRIBUTION_TOLERANCE) {
            throw new IllegalArgumentException("distribution probabilities must sum to 1, were " + total);
        }
        distribution = Collections.unmodifiableMap(new TreeMap<>(distribution));
        Objects.requireNonNull(providerConfidence, "providerConfidence must not be null");
        providerConfidence.ifPresent(confidence -> {
            if (!Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                throw new IllegalArgumentException("provider confidence must be finite and between 0 and 1");
            }
        });
        Objects.requireNonNull(nearestLevel, "nearestLevel must not be null");
        nearestLevel.ifPresent(level -> {
            if (level < MINIMUM_LEVEL || level > MAXIMUM_LEVEL) {
                throw new IllegalArgumentException("nearest level must be between 0 and 4");
            }
        });
        nearestLabel = Objects.requireNonNull(nearestLabel, "nearestLabel must not be null")
                .map(String::strip).filter(label -> !label.isEmpty());
    }
}
