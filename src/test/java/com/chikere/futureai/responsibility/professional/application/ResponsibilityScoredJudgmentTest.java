package com.chikere.futureai.responsibility.professional.application;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ResponsibilityScoredJudgmentTest {

    @Test
    void aValidJudgmentKeepsItsRawEvidence() {
        var judgment = judgment(2.4, Map.of(2, 0.6, 3, 0.4));
        assertThat(judgment.distribution()).containsExactly(Map.entry(2, 0.6), Map.entry(3, 0.4));
        assertThat(judgment(1.0, Map.of()).distribution()).isEmpty();
    }

    @Test
    void invalidValuesAndDistributionsAreRefused() {
        assertThatIllegalArgumentException().isThrownBy(() -> judgment(4.01, Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> judgment(-0.1, Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> judgment(Double.NaN, Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> judgment(Double.POSITIVE_INFINITY, Map.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> judgment(2, Map.of(2, 0.7, 3, 0.7)))
                .withMessageContaining("sum to 1");
        assertThatIllegalArgumentException().isThrownBy(() -> judgment(2, Map.of(5, 1.0)));
        assertThatIllegalArgumentException().isThrownBy(() -> judgment(2, Map.of(2, 1.2, 3, -0.2)));
        assertThatIllegalArgumentException().isThrownBy(() -> judgment(2, Map.of(2, Double.NaN)));
        assertThatIllegalArgumentException().isThrownBy(() -> new ResponsibilityScoredJudgment(2, Map.of(),
                OptionalDouble.of(1.5), OptionalInt.empty(), Optional.empty()));
        assertThatIllegalArgumentException().isThrownBy(() -> new ResponsibilityScoredJudgment(2, Map.of(),
                OptionalDouble.empty(), OptionalInt.of(7), Optional.empty()));
    }

    private static ResponsibilityScoredJudgment judgment(double value, Map<Integer, Double> distribution) {
        return new ResponsibilityScoredJudgment(value, distribution, OptionalDouble.of(0.8), OptionalInt.empty(),
                Optional.empty());
    }
}
