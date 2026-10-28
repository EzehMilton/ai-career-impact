package com.chikere.futureai.coursemodule.university.application;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Providers round probabilities: a distribution may differ from 1.0 by at most 0.01, and no more. */
class ModuleScoredJudgmentTest {

    @Test
    void distributionsWithinOneHundredthOfOneAreAccepted() {
        assertThat(judgment(Map.of(2, 1.0)).distribution()).containsEntry(2, 1.0);
        assertThat(judgment(Map.of(1, 0.5, 2, 0.5)).distribution()).hasSize(2);
        // 0.99 and 1.01 as the provider returns them: |sum - 1| evaluates a hair above 0.01.
        assertThat(Math.abs(0.33 + 0.33 + 0.33 - 1.0)).isGreaterThan(ModuleScoredJudgment.DISTRIBUTION_TOLERANCE);
        assertThat(judgment(Map.of(1, 0.33, 2, 0.33, 3, 0.33)).distribution()).hasSize(3);
        assertThat(judgment(Map.of(1, 0.5, 2, 0.49)).distribution()).hasSize(2);
        assertThat(judgment(Map.of(1, 0.5, 2, 0.51)).distribution()).hasSize(2);
        assertThat(judgment(Map.of(1, 0.39, 2, 0.6)).distribution()).hasSize(2);
    }

    @Test
    void floatingPointAroundTheExactBoundaryKeepsTheBusinessTolerance() {
        assertThat(judgment(Map.of(1, 0.5, 2, 0.49 - 1.0e-12)).distribution()).hasSize(2);
        assertThat(judgment(Map.of(1, 0.5, 2, 0.51 + 1.0e-12)).distribution()).hasSize(2);
        assertThatThrownBy(() -> judgment(Map.of(1, 0.5, 2, 0.49 - 1.0e-6))).hasMessageContaining("sum to 1");
        assertThatThrownBy(() -> judgment(Map.of(1, 0.5, 2, 0.51 + 1.0e-6))).hasMessageContaining("sum to 1");
    }

    @Test
    void distributionsMateriallyOutsideTheToleranceAreRejected() {
        assertThatThrownBy(() -> judgment(Map.of(1, 0.5, 2, 0.4899))).hasMessageContaining("sum to 1");
        assertThatThrownBy(() -> judgment(Map.of(1, 0.5, 2, 0.48))).hasMessageContaining("sum to 1");
        assertThatThrownBy(() -> judgment(Map.of(1, 0.5, 2, 0.52))).hasMessageContaining("sum to 1");
        assertThatThrownBy(() -> judgment(Map.of(1, 0.6, 3, 0.6))).hasMessageContaining("sum to 1");
    }

    private static ModuleScoredJudgment judgment(Map<Integer, Double> distribution) {
        return new ModuleScoredJudgment(2.0, distribution, OptionalDouble.of(0.8), OptionalInt.of(2), Optional.empty());
    }
}
