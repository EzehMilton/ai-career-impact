package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.decision.professional.IndicatorBand;
import com.chikere.futureai.decision.professional.ProfessionalBandThresholds;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static com.chikere.futureai.decision.professional.IndicatorBand.HIGH;
import static com.chikere.futureai.decision.professional.IndicatorBand.LOW;
import static com.chikere.futureai.decision.professional.IndicatorBand.MODERATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProfessionalScoreDisplayFormatterTest {

    @Test
    void aLowScoreNeverDisplaysAsTheModerateBoundary() {
        assertThat(format(3.4949, LOW)).isEqualTo("3.49");
        assertThat(format(3.4950, LOW)).isEqualTo("3.49");
        assertThat(format(3.4975, LOW)).isEqualTo("3.49");
        assertThat(format(3.499999, LOW)).isEqualTo("3.49");
        assertThat(format(Math.nextDown(3.5), LOW)).isEqualTo("3.49");
        assertThat(format(3.5000, MODERATE)).isEqualTo("3.50");
        assertThat(format(3.504, MODERATE)).isEqualTo("3.50");
    }

    @Test
    void aModerateScoreNeverDisplaysAsTheHighBoundary() {
        assertThat(format(6.9949, MODERATE)).isEqualTo("6.99");
        assertThat(format(6.9950, MODERATE)).isEqualTo("6.99");
        assertThat(format(6.99625, MODERATE)).isEqualTo("6.99");
        assertThat(format(6.999999, MODERATE)).isEqualTo("6.99");
        assertThat(format(Math.nextDown(7.0), MODERATE)).isEqualTo("6.99");
        assertThat(format(7.0000, HIGH)).isEqualTo("7.00");
        assertThat(format(7.004, HIGH)).isEqualTo("7.00");
    }

    @Test
    void ordinaryScoresUseHalfUpRoundingOfTheShortestDecimalForm() {
        assertThat(format(2.275, LOW)).isEqualTo("2.28");
        assertThat(format(4.8105, MODERATE)).isEqualTo("4.81");
        assertThat(format(7.525, HIGH)).isEqualTo("7.53");
        assertThat(format(8.9375, HIGH)).isEqualTo("8.94");
        assertThat(format(6.984, MODERATE)).isEqualTo("6.98");
        assertThat(format(0.0, LOW)).isEqualTo("0.00");
        assertThat(format(10.0, HIGH)).isEqualTo("10.00");
    }

    @Test
    void theDisplayedScoreAlwaysBelongsToTheStoredBandAndOtherwiseEqualsTheFormerFormatting() {
        Random random = new Random(20261026L);
        for (int sample = 0; sample < 200_000; sample++) {
            // Half the samples sit within 0.01 of a boundary.
            double score = sample % 2 == 0 ? random.nextDouble() * 10.0
                    : (sample % 4 == 1 ? 3.5 : 7.0) + (random.nextDouble() - 0.5) * 0.02;
            IndicatorBand band = ProfessionalDecisionPolicyV2.indicatorBand(score);
            String shown = format(score, band);
            assertThat(ProfessionalDecisionPolicyV2.indicatorBand(Double.parseDouble(shown))).as("%s", score)
                    .isEqualTo(band);
            String former = String.format(java.util.Locale.ROOT, "%.2f", score);
            if (ProfessionalDecisionPolicyV2.indicatorBand(Double.parseDouble(former)) == band) {
                assertThat(shown).as("unchanged outside the boundary windows: %s", score).isEqualTo(former);
            }
        }
    }

    @Test
    void theBoundariesAreThePolicysOwnAndInvalidScoresAreRefused() {
        assertThat(ProfessionalDecisionPolicyV2.indicatorBand(Math.nextDown(ProfessionalBandThresholds.MODERATE_FROM)))
                .isEqualTo(LOW);
        assertThat(ProfessionalDecisionPolicyV2.indicatorBand(ProfessionalBandThresholds.MODERATE_FROM)).isEqualTo(MODERATE);
        assertThat(ProfessionalDecisionPolicyV2.indicatorBand(Math.nextDown(ProfessionalBandThresholds.HIGH_FROM)))
                .isEqualTo(MODERATE);
        assertThat(ProfessionalDecisionPolicyV2.indicatorBand(ProfessionalBandThresholds.HIGH_FROM)).isEqualTo(HIGH);
        assertThatIllegalArgumentException().isThrownBy(() -> format(Double.NaN, LOW));
        assertThatIllegalArgumentException().isThrownBy(() -> format(10.01, HIGH));
        assertThatIllegalArgumentException().isThrownBy(() -> format(-0.01, LOW));
    }

    private static String format(double score, IndicatorBand band) {
        return ProfessionalScoreDisplayFormatter.format(score, band);
    }
}
