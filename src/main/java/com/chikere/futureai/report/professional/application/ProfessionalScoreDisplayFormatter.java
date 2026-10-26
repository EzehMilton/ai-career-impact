package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.decision.professional.IndicatorBand;
import com.chikere.futureai.decision.professional.ProfessionalBandThresholds;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * The one way a Professional score is shown to a visitor, on the free result, the detailed report and the PDF.
 *
 * <p>The score is shown to two decimal places, rounded {@link RoundingMode#HALF_UP} from its shortest decimal
 * representation ({@link BigDecimal#valueOf(double)}), so 2.275 shows as 2.28. One exception: if that rounded
 * value would visually reach the next band's boundary while the stored band is the lower one, the highest
 * two-decimal value still inside the stored band is shown instead. A Moderate 6.99625 shows as 6.99, not 7.00.
 * Rounding never moves a score below its own band's lower boundary, so no upward adjustment exists.</p>
 *
 * <p>Presentation only. The band always comes from the stored deterministic result and is never recomputed
 * from the displayed number; the raw score itself is never changed.</p>
 *
 * <p>Stored results and purchased snapshots keep raw doubles, so a historical assessment or purchased report
 * viewed as HTML is formatted by this rule when it is rendered, which corrects the old boundary display without
 * touching any stored data. Stored PDFs are immutable bytes and keep the formatting they were rendered with; new
 * PDFs are professional-report-pdf-v4.</p>
 */
public final class ProfessionalScoreDisplayFormatter {

    private static final int DECIMALS = 2;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal STEP = BigDecimal.ONE.movePointLeft(DECIMALS);

    private ProfessionalScoreDisplayFormatter() {
    }

    /** The score as shown, for example {@code "6.99"}, consistent with {@code storedBand}. */
    public static String format(double score, IndicatorBand storedBand) {
        Objects.requireNonNull(storedBand, "storedBand must not be null");
        if (!Double.isFinite(score) || score < 0.0 || score > 10.0) {
            throw new IllegalArgumentException("score must be finite and between 0 and 10");
        }
        BigDecimal rounded = BigDecimal.valueOf(score).setScale(DECIMALS, ROUNDING);
        BigDecimal shown = ProfessionalBandThresholds.upperBoundary(storedBand)
                .filter(boundary -> rounded.compareTo(boundary) >= 0)
                .map(boundary -> boundary.setScale(DECIMALS, ROUNDING).subtract(STEP))
                .orElse(rounded);
        return shown.toPlainString();
    }
}
