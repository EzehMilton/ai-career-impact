package com.chikere.futureai.cost;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProviderCostEstimateTest {

    private static final ProviderCostProperties RATES = new ProviderCostProperties("USD",
            new ProviderRates(new BigDecimal("0.15"), new BigDecimal("0.60")),
            new ProviderRates(new BigDecimal("0.042"), BigDecimal.ZERO));

    @Test
    void pricesEachProviderAtItsOwnRatesAndTotalsThem() {
        var estimate = ProviderCostEstimate.of(List.of(
                ProviderUsage.of(ProviderUsage.OPENAI, "gpt-4o-mini", 3_000, 800),
                ProviderUsage.of(ProviderUsage.JEV, "jev-latest", 2_000, 50)), RATES);

        // Jev: 2,000 × 0.042 / 1M = 0.000084, output free. OpenAI: 3,000 × 0.15 / 1M + 800 × 0.60 / 1M.
        assertThat(estimate.providers()).extracting(ProviderCostEstimate.ProviderTotal::provider)
                .containsExactly("jev", "openai");
        assertThat(estimate.providers().get(0).cost()).isEqualByComparingTo("0.000084");
        assertThat(estimate.providers().get(1).cost()).isEqualByComparingTo("0.000930");
        assertThat(estimate.totalCost()).isEqualByComparingTo("0.001014");
        assertThat(estimate.logFields()).isEqualTo("jevCalls=1 jevInputTokens=2000 jevOutputTokens=50 "
                + "jevCost=0.000084 openaiCalls=1 openaiInputTokens=3000 openaiOutputTokens=800 "
                + "openaiCost=0.000930 totalCost=0.001014 currency=USD");
    }

    @Test
    void sumsSeveralCallsToTheSameProvider() {
        var estimate = ProviderCostEstimate.of(List.of(
                ProviderUsage.of(ProviderUsage.OPENAI, "gpt-4o-mini", 1_000, 100),
                ProviderUsage.of(ProviderUsage.OPENAI, "gpt-4o-mini", 2_000, 200)), RATES);

        var openai = estimate.providers().getFirst();
        assertThat(openai.calls()).isEqualTo(2);
        assertThat(openai.inputTokens()).isEqualTo(3_000);
        assertThat(openai.outputTokens()).isEqualTo(300);
    }

    @Test
    void aCallWithoutReportedUsageIsCountedButNotPriced() {
        var estimate = ProviderCostEstimate.of(List.of(
                ProviderUsage.of(ProviderUsage.JEV, "jev-latest", null, null)), RATES);

        assertThat(estimate.totalCost()).isEqualByComparingTo("0");
        assertThat(estimate.logFields()).contains("jevCalls=1", "jevCallsWithoutUsage=1");
    }

    @Test
    void anUnconfiguredProviderIsPricedAtZeroRatherThanGuessed() {
        var unpriced = new ProviderCostProperties(null, null, null);
        var estimate = ProviderCostEstimate.of(List.of(
                ProviderUsage.of(ProviderUsage.OPENAI, "gpt-4o-mini", 3_000, 800)), unpriced);

        assertThat(estimate.totalCost()).isEqualByComparingTo("0");
        assertThat(estimate.currency()).isEqualTo("USD");
        assertThat(estimate.logFields()).contains("openaiInputTokens=3000");
    }

    @Test
    void negativeRatesAreRefused() {
        assertThatThrownBy(() -> new ProviderRates(new BigDecimal("-1"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
