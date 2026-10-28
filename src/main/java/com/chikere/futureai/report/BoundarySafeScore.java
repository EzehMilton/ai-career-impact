package com.chikere.futureai.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.OptionalDouble;

/**
 * The one way a banded 0–10 score is shown, for every journey. Scores are shown to two decimal places, rounded
 * half-up. Floating-point noise below {@value #NOISE_DECIMALS} decimal places is removed first, so a value that is
 * mathematically 6.925 rounds to 6.93 whether the policy's arithmetic produced 6.925000000000001 or
 * 6.924999999999999: the shown number never depends on the order a weighted sum was added up. A rounded value that would visually reach the next band's
 * lower boundary while the stored band is the lower one shows as the highest two-decimal value still inside the
 * stored band: a middle-band 6.996 shows as 6.99, never 7.00. The band always comes from the stored deterministic
 * result; it is never recomputed from the displayed number.
 */
public final class BoundarySafeScore {

    private static final int DECIMALS = 2;
    /** The same allowance the decision policies use for their band thresholds (1e-9). */
    static final int NOISE_DECIMALS = 9;
    private static final BigDecimal STEP = BigDecimal.ONE.movePointLeft(DECIMALS);

    private BoundarySafeScore() {
    }

    /**
     * @param nextBandFrom the lower boundary of the band above the stored one; empty for the top band
     */
    public static String format(double score, OptionalDouble nextBandFrom) {
        if (!Double.isFinite(score) || score < 0.0 || score > 10.0) {
            throw new IllegalArgumentException("score must be finite and between 0 and 10");
        }
        BigDecimal rounded = BigDecimal.valueOf(score).setScale(NOISE_DECIMALS, RoundingMode.HALF_UP)
                .setScale(DECIMALS, RoundingMode.HALF_UP);
        if (nextBandFrom.isPresent()) {
            BigDecimal boundary = BigDecimal.valueOf(nextBandFrom.getAsDouble()).setScale(DECIMALS, RoundingMode.HALF_UP);
            if (rounded.compareTo(boundary) >= 0) {
                return boundary.subtract(STEP).toPlainString();
            }
        }
        return rounded.toPlainString();
    }
}
