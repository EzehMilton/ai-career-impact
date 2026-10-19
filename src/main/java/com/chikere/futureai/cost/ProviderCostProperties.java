package com.chikere.futureai.cost;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The rates used to turn reported token usage into an estimated cost.
 *
 * <p>These are operator-supplied list prices, not invoices. A missing rate prices that provider
 * at zero, so an unconfigured provider shows up as tokens with no cost rather than as a guess.</p>
 */
@ConfigurationProperties("app.cost")
public record ProviderCostProperties(String currency, ProviderRates openai, ProviderRates jev) {

    public ProviderCostProperties {
        currency = currency == null || currency.isBlank() ? "USD" : currency.strip();
        openai = openai == null ? ProviderRates.UNPRICED : openai;
        jev = jev == null ? ProviderRates.UNPRICED : jev;
    }

    ProviderRates ratesFor(String provider) {
        return switch (provider) {
            case ProviderUsage.OPENAI -> openai;
            case ProviderUsage.JEV -> jev;
            default -> ProviderRates.UNPRICED;
        };
    }
}
