package com.chikere.futureai.cost;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

import java.util.Comparator;
import java.util.Map;

/**
 * The rates used to turn reported token usage into an estimated cost.
 *
 * <p>These are operator-supplied list prices, not invoices. A missing rate prices that provider
 * at zero, so an unconfigured provider shows up as tokens with no cost rather than as a guess.</p>
 *
 * <p>Journeys use different OpenAI models, so an OpenAI call is priced at its own model's rate from
 * {@code openaiModels}, keyed by model name and matched against the model the provider reported (a dated snapshot
 * such as {@code gpt-6-luna-2026-10-01} matches {@code gpt-6-luna}). {@code openai} is the rate for any other model.</p>
 */
@ConfigurationProperties("app.cost")
public record ProviderCostProperties(String currency, ProviderRates openai, ProviderRates jev,
        Map<String, ProviderRates> openaiModels) {

    @ConstructorBinding
    public ProviderCostProperties {
        currency = currency == null || currency.isBlank() ? "USD" : currency.strip();
        openai = openai == null ? ProviderRates.UNPRICED : openai;
        jev = jev == null ? ProviderRates.UNPRICED : jev;
        openaiModels = openaiModels == null ? Map.of() : Map.copyOf(openaiModels);
    }

    /** Rates with no per-model OpenAI prices. */
    public ProviderCostProperties(String currency, ProviderRates openai, ProviderRates jev) {
        this(currency, openai, jev, Map.of());
    }

    ProviderRates ratesFor(String provider) {
        return switch (provider) {
            case ProviderUsage.OPENAI -> openai;
            case ProviderUsage.JEV -> jev;
            default -> ProviderRates.UNPRICED;
        };
    }

    /** One call's rate: an OpenAI call at its model's configured rate when there is one, else the provider's. */
    ProviderRates ratesFor(String provider, String model) {
        if (!ProviderUsage.OPENAI.equals(provider) || model == null) {
            return ratesFor(provider);
        }
        return openaiModels.entrySet().stream()
                .filter(entry -> model.equals(entry.getKey()) || model.startsWith(entry.getKey() + "-"))
                .max(Comparator.comparingInt(entry -> entry.getKey().length()))
                .map(Map.Entry::getValue)
                .orElse(openai);
    }
}
