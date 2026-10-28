package com.chikere.futureai.judgment.domain;

import com.chikere.futureai.coursemodule.university.application.ModuleScoredJudgment;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * One distribution rule for every journey: a rounded sum within 0.01 of 1 (with a floating-point allowance) is
 * accepted at course and role level, for University modules and for Professional responsibilities alike.
 */
class ProbabilityDistributionsTest {

    @ParameterizedTest
    @ValueSource(doubles = {0.99, 1.0, 1.01, 0.995})
    void roundedSumsWithinTheToleranceAreAccepted(double total) {
        assertThat(ProbabilityDistributions.sumsToOne(total)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.98, 1.02, 0.4, Double.NaN})
    void sumsBeyondTheToleranceAreRejected(double total) {
        assertThat(ProbabilityDistributions.sumsToOne(total)).isFalse();
    }

    @Test
    void theEpsilonCoversTheFloatingPointErrorOfAnExactPointNineNine() {
        assertThat(Math.abs(0.99 - 1.0)).isGreaterThan(ProbabilityDistributions.TOLERANCE);
        assertThat(ProbabilityDistributions.sumsToOne(0.33 + 0.33 + 0.33)).isTrue();
        assertThat(ProbabilityDistributions.sumsToOne(0.34 + 0.34 + 0.33)).isTrue();
    }

    @Test
    void aCourseOrRoleLevelJudgmentAcceptsARoundedJevDistribution() {
        assertThatNoException().isThrownBy(() -> courseLevel(Map.of("1", 0.33, "2", 0.33, "3", 0.33)));
        assertThatNoException().isThrownBy(() -> courseLevel(Map.of("1", 0.34, "2", 0.34, "3", 0.33)));
        assertThatThrownBy(() -> courseLevel(Map.of("1", 0.30, "2", 0.30, "3", 0.30)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sum to 1");
        assertThatThrownBy(() -> courseLevel(Map.of("1", 0.5, "2", 0.6)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sum to 1");
    }

    @Test
    void aProfessionalResponsibilityJudgmentAcceptsAnExactPointNineNine() {
        // Before the shared rule this contract had no floating-point allowance and rejected a rounded 0.99.
        assertThatNoException().isThrownBy(() -> responsibility(Map.of(1, 0.33, 2, 0.33, 3, 0.33)));
        assertThatThrownBy(() -> responsibility(Map.of(1, 0.30, 2, 0.30, 3, 0.30)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("must sum to 1");
    }

    @Test
    void theModuleContractUsesTheSameRule() {
        assertThat(ModuleScoredJudgment.DISTRIBUTION_TOLERANCE).isEqualTo(ProbabilityDistributions.TOLERANCE);
        assertThat(ResponsibilityScoredJudgment.DISTRIBUTION_TOLERANCE).isEqualTo(ProbabilityDistributions.TOLERANCE);
    }

    private static DimensionJudgment courseLevel(Map<String, Double> distribution) {
        return new DimensionJudgment("analytical_reasoning", "Analytical reasoning", OptionalDouble.of(2.0),
                Optional.empty(), Optional.empty(), OptionalDouble.of(0.8), distribution);
    }

    private static ResponsibilityScoredJudgment responsibility(Map<Integer, Double> distribution) {
        return new ResponsibilityScoredJudgment(2.0, distribution, OptionalDouble.of(0.8), OptionalInt.empty(),
                Optional.empty());
    }
}
