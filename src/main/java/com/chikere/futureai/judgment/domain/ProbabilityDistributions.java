package com.chikere.futureai.judgment.domain;

/**
 * The one rule for how far a provider's probability distribution may stray from summing to 1, shared by every
 * journey's judgments: course and role level, University modules and Professional responsibilities.
 *
 * <p>Providers round probabilities, so a distribution may sum to 1 within {@link #TOLERANCE}. {@link #EPSILON} is a
 * floating-point allowance on top, so a sum the provider means as exactly 0.99 or 1.01 is accepted
 * ({@code |0.99 - 1.0|} evaluates to 0.010000000000000009). It does not widen the business rule. Distributions are
 * stored for provenance; no decision policy reads them, so accepting a rounded one changes no score.</p>
 */
public final class ProbabilityDistributions {

    public static final double TOLERANCE = 0.01;
    public static final double EPSILON = 1.0e-9;

    private ProbabilityDistributions() {
    }

    /** Whether probabilities totalling {@code total} sum to 1 within the shared tolerance. */
    public static boolean sumsToOne(double total) {
        return Double.isFinite(total) && Math.abs(total - 1.0) <= TOLERANCE + EPSILON;
    }
}
