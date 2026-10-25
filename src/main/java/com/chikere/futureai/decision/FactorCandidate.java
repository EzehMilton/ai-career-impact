package com.chikere.futureai.decision;

/**
 * One dimension considered for the displayed factors, with the signed effect the policy computed for
 * it: {@code weight * (transformedValue - 0.5)}. A zero effect means the score sat at the neutral
 * midpoint and the dimension is never shown.
 */
public record FactorCandidate(
        String dimensionId,
        double rawScore,
        double transformedValue,
        double weight,
        double signedEffect) {
}
