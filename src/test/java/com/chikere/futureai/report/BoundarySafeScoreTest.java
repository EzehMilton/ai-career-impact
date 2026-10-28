package com.chikere.futureai.report;

import org.junit.jupiter.api.Test;

import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The shared journey-neutral score display: it never visually reaches the next band. */
class BoundarySafeScoreTest {

    private static final OptionalDouble MIDDLE_FROM = OptionalDouble.of(3.5);
    private static final OptionalDouble TOP_FROM = OptionalDouble.of(7.0);

    @Test
    void aMiddleBandScoreJustBelowSevenNeverShowsAsSeven() {
        assertThat(BoundarySafeScore.format(6.996, TOP_FROM)).isEqualTo("6.99");
        assertThat(BoundarySafeScore.format(6.9999999, TOP_FROM)).isEqualTo("6.99");
        assertThat(BoundarySafeScore.format(6.994, TOP_FROM)).isEqualTo("6.99");
        assertThat(BoundarySafeScore.format(6.985, TOP_FROM)).isEqualTo("6.99");
        assertThat(BoundarySafeScore.format(6.98, TOP_FROM)).isEqualTo("6.98");
    }

    @Test
    void aLowBandScoreJustBelowThreePointFiveNeverShowsAsThreePointFive() {
        assertThat(BoundarySafeScore.format(3.4999, MIDDLE_FROM)).isEqualTo("3.49");
        assertThat(BoundarySafeScore.format(3.495, MIDDLE_FROM)).isEqualTo("3.49");
        assertThat(BoundarySafeScore.format(3.48, MIDDLE_FROM)).isEqualTo("3.48");
    }

    @Test
    void scoresAtOrAboveABoundaryShowAsTheyAre() {
        assertThat(BoundarySafeScore.format(3.5, TOP_FROM)).isEqualTo("3.50");
        assertThat(BoundarySafeScore.format(7.0, OptionalDouble.empty())).isEqualTo("7.00");
        assertThat(BoundarySafeScore.format(9.995, OptionalDouble.empty())).isEqualTo("10.00");
        assertThat(BoundarySafeScore.format(0.0, MIDDLE_FROM)).isEqualTo("0.00");
    }

    @Test
    void floatingPointNoiseNeverChangesTheRoundedScore() {
        // 10 x (0.7 x 0.7 + 0.15 x 0.9 + 0.15 x 0.45) is exactly 6.925; summation order decides which side it lands.
        assertThat(BoundarySafeScore.format(6.924999999999999, TOP_FROM)).isEqualTo("6.93");
        assertThat(BoundarySafeScore.format(6.925000000000001, TOP_FROM)).isEqualTo("6.93");
        assertThat(BoundarySafeScore.format(6.9249, TOP_FROM)).isEqualTo("6.92");
    }

    @Test
    void rejectsScoresOutsideTheScale() {
        assertThatThrownBy(() -> BoundarySafeScore.format(10.01, OptionalDouble.empty()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BoundarySafeScore.format(Double.NaN, TOP_FROM))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
