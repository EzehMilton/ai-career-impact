package com.chikere.futureai.cost;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
    void eachOpenAiCallIsPricedAtTheRateOfTheModelThatServedIt() {
        var rates = new ProviderCostProperties("USD", ProviderRates.UNPRICED, ProviderRates.UNPRICED, Map.of(
                "gpt-6-luna", new ProviderRates(new BigDecimal("0.10"), new BigDecimal("0.50")),
                "gpt-6.1-sol", new ProviderRates(new BigDecimal("2.00"), new BigDecimal("10.00"))));
        var estimate = ProviderCostEstimate.of(List.of(
                // A University call reported as a dated snapshot, a Professional call, and an unpriced model.
                ProviderUsage.of(ProviderUsage.OPENAI, "gpt-6-luna-2026-10-01", 10_000, 2_000),
                ProviderUsage.of(ProviderUsage.OPENAI, "gpt-6.1-sol", 10_000, 2_000),
                ProviderUsage.of(ProviderUsage.OPENAI, "gpt-6-lunar", 10_000, 2_000)), rates);

        // Luna: 10,000 × 0.10 / 1M + 2,000 × 0.50 / 1M = 0.002. Sol: 10,000 × 2 / 1M + 2,000 × 10 / 1M = 0.04.
        // "gpt-6-lunar" is not a snapshot of gpt-6-luna, so it takes the (unpriced) provider rate.
        var openai = estimate.providers().getFirst();
        assertThat(openai.calls()).isEqualTo(3);
        assertThat(openai.cost()).isEqualByComparingTo("0.042");
    }

    @Test
    void perModelRatesBindFromApplicationPropertiesDespiteTheDotsInModelNames() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withUserConfiguration(ProviderCostConfiguration.class)
                .withPropertyValues("app.cost.openai-models[gpt-6.1-sol].input-per-million-tokens=2.00",
                        "app.cost.openai-models[gpt-6.1-sol].output-per-million-tokens=10.00",
                        "app.cost.openai-models[gpt-6-luna].input-per-million-tokens=0.10")
                .run(context -> {
                    var rates = context.getBean(ProviderCostProperties.class);
                    assertThat(rates.openaiModels()).containsOnlyKeys("gpt-6.1-sol", "gpt-6-luna");
                    assertThat(rates.ratesFor(ProviderUsage.OPENAI, "gpt-6.1-sol").outputPerMillionTokens())
                            .isEqualByComparingTo("10.00");
                    assertThat(rates.ratesFor(ProviderUsage.JEV, "gpt-6.1-sol")).isEqualTo(rates.jev());
                });
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
