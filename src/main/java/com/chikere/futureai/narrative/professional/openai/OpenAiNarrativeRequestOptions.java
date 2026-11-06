package com.chikere.futureai.narrative.professional.openai;

import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The model and sampling settings sent with every OpenAI narrative request (all three journeys, free and paid),
 * chosen for the journey's configured model. Each field is sent only when present; an absent field is omitted from
 * the request, never replaced by a value the model may reject. The model is always sent explicitly, so a request
 * never falls back to Spring AI's global default model.
 *
 * <p>Reasoning models (the GPT-6 family, or any model given an explicit reasoning effort) accept only their default
 * temperature, so they get a reasoning effort and no temperature. Other models keep the generator's low custom
 * temperature. A narrative's authority depends on neither: its prompt contract and validator decide what may be said.
 * These settings are request parameters, not part of any narrative version's contract.</p>
 */
public record OpenAiNarrativeRequestOptions(Optional<Double> temperature, Optional<String> reasoningEffort,
        Optional<String> model) {

    /** The reasoning effort for GPT-6 models when none is configured: a short explanation needs no deliberation. */
    public static final String DEFAULT_REASONING_EFFORT = "none";

    public OpenAiNarrativeRequestOptions {
        Objects.requireNonNull(temperature);
        Objects.requireNonNull(reasoningEffort);
        Objects.requireNonNull(model);
        if (temperature.isPresent() && reasoningEffort.isPresent()) {
            throw new IllegalArgumentException("a reasoning request must not carry a custom temperature");
        }
    }

    /** Settings without a model: the request then carries none of its own. */
    public OpenAiNarrativeRequestOptions(Optional<Double> temperature, Optional<String> reasoningEffort) {
        this(temperature, reasoningEffort, Optional.empty());
    }

    /**
     * @param model the configured model, if any
     * @param configuredReasoningEffort an explicit reasoning effort; when present it is used for any model
     * @param customTemperature the generator's temperature for models that accept a custom value
     */
    public static OpenAiNarrativeRequestOptions forModel(Optional<String> model, Optional<String> configuredReasoningEffort,
            double customTemperature) {
        return resolve(model, configuredReasoningEffort, OptionalDouble.of(customTemperature));
    }

    /**
     * For a generator with no custom temperature of its own (the free University and A-level narratives): a GPT-6
     * model gets the same reasoning effort as every other narrative, and any other model is sent no sampling setting.
     */
    public static OpenAiNarrativeRequestOptions forModel(Optional<String> model, Optional<String> configuredReasoningEffort) {
        return resolve(model, configuredReasoningEffort, OptionalDouble.empty());
    }

    private static OpenAiNarrativeRequestOptions resolve(Optional<String> model,
            Optional<String> configuredReasoningEffort, OptionalDouble customTemperature) {
        Optional<String> name = model.map(String::strip).filter(value -> !value.isEmpty());
        Optional<String> effort = configuredReasoningEffort.map(String::strip).filter(value -> !value.isEmpty());
        if (effort.isPresent()) {
            return new OpenAiNarrativeRequestOptions(Optional.empty(), effort, name);
        }
        boolean reasoningModel = name.map(value -> value.toLowerCase(Locale.ROOT).startsWith("gpt-6")).orElse(false);
        if (reasoningModel) {
            return new OpenAiNarrativeRequestOptions(Optional.empty(), Optional.of(DEFAULT_REASONING_EFFORT), name);
        }
        Optional<Double> temperature = customTemperature.isPresent()
                ? Optional.of(customTemperature.getAsDouble()) : Optional.empty();
        return new OpenAiNarrativeRequestOptions(temperature, Optional.empty(), name);
    }

    /** A fresh builder per request, carrying only the present settings, the model always among them when known. */
    public OpenAiChatOptions.Builder toChatOptions() {
        var builder = OpenAiChatOptions.builder();
        model.ifPresent(builder::model);
        temperature.ifPresent(builder::temperature);
        reasoningEffort.ifPresent(builder::reasoningEffort);
        return builder;
    }
}
