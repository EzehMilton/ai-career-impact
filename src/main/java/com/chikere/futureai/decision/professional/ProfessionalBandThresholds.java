package com.chikere.futureai.decision.professional;

import java.math.BigDecimal;

/**
 * The one definition of the Professional band boundaries, shared by every measure: LOW below
 * {@link #MODERATE_FROM}, MODERATE from it up to {@link #HIGH_FROM}, HIGH from there. The decision policy assigns
 * bands from these on the raw score; presentation may read them only to keep a displayed score inside the band
 * the policy already assigned. Changing either value is a decision-policy change and needs a new policy version.
 */
public final class ProfessionalBandThresholds {

    public static final double MODERATE_FROM = 3.50;
    public static final double HIGH_FROM = 7.00;

    private ProfessionalBandThresholds() {
    }

    /** The exclusive upper boundary of a band as an exact decimal, or empty for HIGH, which has none below 10. */
    public static java.util.Optional<BigDecimal> upperBoundary(IndicatorBand band) {
        return switch (band) {
            case LOW -> java.util.Optional.of(BigDecimal.valueOf(MODERATE_FROM));
            case MODERATE -> java.util.Optional.of(BigDecimal.valueOf(HIGH_FROM));
            case HIGH -> java.util.Optional.empty();
        };
    }
}
