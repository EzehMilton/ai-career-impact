package com.chikere.futureai.cost;

import java.util.Objects;

/**
 * The tokens one billable provider call consumed, as the provider itself reported them.
 *
 * <p>{@code reported} is false when the response carried no usage at all. The call still counts,
 * so a log line can say "one call, usage unknown" rather than silently pricing it at zero.</p>
 */
public record ProviderUsage(String provider, String model, long inputTokens, long outputTokens,
        boolean reported) {

    public static final String JEV = "jev";
    public static final String OPENAI = "openai";

    public ProviderUsage {
        Objects.requireNonNull(provider, "provider must not be null");
        model = model == null || model.isBlank() ? "unknown" : model.strip();
        if (inputTokens < 0 || outputTokens < 0) {
            throw new IllegalArgumentException("token counts must not be negative");
        }
    }

    /** Builds usage from a provider's nullable counts; absent or all-zero counts mean "not reported". */
    public static ProviderUsage of(String provider, String model, Integer inputTokens, Integer outputTokens) {
        long input = inputTokens == null ? 0 : Math.max(0, inputTokens);
        long output = outputTokens == null ? 0 : Math.max(0, outputTokens);
        boolean reported = (inputTokens != null || outputTokens != null) && input + output > 0;
        return new ProviderUsage(provider, model, input, output, reported);
    }
}
