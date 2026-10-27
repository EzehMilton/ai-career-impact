package com.chikere.futureai.narrative.professional.openai;

import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * The sampling settings sent with a Professional narrative request (role-level professional-narrative-v7 and
 * professional-paid-narrative-v1), chosen for the configured model. Each field is sent only when present; an absent
 * field is omitted from the request, never replaced by a value the model may reject.
 *
 * <p>Reasoning models (the GPT-6 family, or any model given an explicit reasoning effort) accept only their default
 * temperature, so they get a reasoning effort and no temperature. Other models keep the generator's low custom
 * temperature. A narrative's authority depends on neither: its prompt contract and validator decide what may be said.
 * These settings are request parameters, not part of any narrative version's contract.</p>
 */
public record OpenAiNarrativeRequestOptions(Optional<Double> temperature, Optional<String> reasoningEffort) {

    /** The reasoning effort for GPT-6 models when none is configured: a short explanation needs no deliberation. */
    public static final String DEFAULT_REASONING_EFFORT = "none";

    public OpenAiNarrativeRequestOptions {
        Objects.requireNonNull(temperature);
        Objects.requireNonNull(reasoningEffort);
        if (temperature.isPresent() && reasoningEffort.isPresent()) {
            throw new IllegalArgumentException("a reasoning request must not carry a custom temperature");
        }
    }

    /**
     * @param model the configured model, if any
     * @param configuredReasoningEffort an explicit reasoning effort; when present it is used for any model
     * @param customTemperature the generator's temperature for models that accept a custom value
     */
    public static OpenAiNarrativeRequestOptions forModel(Optional<String> model, Optional<String> configuredReasoningEffort,
            double customTemperature) {
        Optional<String> effort = configuredReasoningEffort.map(String::strip).filter(value -> !value.isEmpty());
        if (effort.isPresent()) {
            return new OpenAiNarrativeRequestOptions(Optional.empty(), effort);
        }
        boolean reasoningModel = model.map(name -> name.strip().toLowerCase(Locale.ROOT).startsWith("gpt-6"))
                .orElse(false);
        return reasoningModel
                ? new OpenAiNarrativeRequestOptions(Optional.empty(), Optional.of(DEFAULT_REASONING_EFFORT))
                : new OpenAiNarrativeRequestOptions(Optional.of(customTemperature), Optional.empty());
    }

    /** A fresh builder per request, carrying only the present settings. */
    public OpenAiChatOptions.Builder toChatOptions() {
        var builder = OpenAiChatOptions.builder();
        temperature.ifPresent(builder::temperature);
        reasoningEffort.ifPresent(builder::reasoningEffort);
        return builder;
    }
}
