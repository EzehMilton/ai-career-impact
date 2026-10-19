package com.chikere.futureai.cost;

import java.math.BigDecimal;

/** One provider's list price, per million input and per million output tokens. */
public record ProviderRates(BigDecimal inputPerMillionTokens, BigDecimal outputPerMillionTokens) {

    private static final BigDecimal ONE_MILLION = BigDecimal.valueOf(1_000_000);

    public static final ProviderRates UNPRICED = new ProviderRates(BigDecimal.ZERO, BigDecimal.ZERO);

    public ProviderRates {
        inputPerMillionTokens = nonNegative(inputPerMillionTokens, "input");
        outputPerMillionTokens = nonNegative(outputPerMillionTokens, "output");
    }

    BigDecimal costOf(long inputTokens, long outputTokens) {
        return inputPerMillionTokens.multiply(BigDecimal.valueOf(inputTokens))
                .add(outputPerMillionTokens.multiply(BigDecimal.valueOf(outputTokens)))
                .divide(ONE_MILLION);
    }

    private static BigDecimal nonNegative(BigDecimal rate, String side) {
        if (rate == null) {
            return BigDecimal.ZERO;
        }
        if (rate.signum() < 0) {
            throw new IllegalArgumentException(side + " rate must not be negative");
        }
        return rate;
    }
}
