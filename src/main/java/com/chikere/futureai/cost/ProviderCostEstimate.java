package com.chikere.futureai.cost;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** The estimated cost of a set of provider calls, totalled per provider in a stable order. */
public record ProviderCostEstimate(List<ProviderTotal> providers, BigDecimal totalCost, String currency) {

    /** Displayed precision: a millionth of a currency unit, far below a single call's cost. */
    private static final int SCALE = 6;

    public record ProviderTotal(String provider, int calls, int callsWithoutUsage, long inputTokens,
            long outputTokens, BigDecimal cost) { }

    public ProviderCostEstimate {
        providers = List.copyOf(providers);
        Objects.requireNonNull(totalCost, "totalCost must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
    }

    /** Totals {@code usages} per provider, Jev first and OpenAI second, then any other in arrival order. */
    public static ProviderCostEstimate of(List<ProviderUsage> usages, ProviderCostProperties rates) {
        Map<String, List<ProviderUsage>> byProvider = new LinkedHashMap<>();
        byProvider.put(ProviderUsage.JEV, new ArrayList<>());
        byProvider.put(ProviderUsage.OPENAI, new ArrayList<>());
        usages.forEach(usage -> byProvider.computeIfAbsent(usage.provider(), ignored -> new ArrayList<>())
                .add(usage));

        List<ProviderTotal> totals = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (var entry : byProvider.entrySet()) {
            List<ProviderUsage> calls = entry.getValue();
            if (calls.isEmpty()) {
                continue;
            }
            long input = calls.stream().mapToLong(ProviderUsage::inputTokens).sum();
            long output = calls.stream().mapToLong(ProviderUsage::outputTokens).sum();
            int withoutUsage = (int) calls.stream().filter(usage -> !usage.reported()).count();
            BigDecimal cost = rates.ratesFor(entry.getKey()).costOf(input, output);
            totals.add(new ProviderTotal(entry.getKey(), calls.size(), withoutUsage, input, output,
                    cost.setScale(SCALE, RoundingMode.HALF_UP)));
            total = total.add(cost);
        }
        return new ProviderCostEstimate(totals, total.setScale(SCALE, RoundingMode.HALF_UP), rates.currency());
    }

    /** Stable {@code key=value} fields for one log line, matching the rest of the application's logs. */
    String logFields() {
        StringBuilder fields = new StringBuilder();
        for (ProviderTotal provider : providers) {
            String name = provider.provider();
            fields.append(name).append("Calls=").append(provider.calls()).append(' ')
                    .append(name).append("InputTokens=").append(provider.inputTokens()).append(' ')
                    .append(name).append("OutputTokens=").append(provider.outputTokens()).append(' ')
                    .append(name).append("Cost=").append(provider.cost().toPlainString()).append(' ');
            if (provider.callsWithoutUsage() > 0) {
                fields.append(name).append("CallsWithoutUsage=").append(provider.callsWithoutUsage()).append(' ');
            }
        }
        return fields.append("totalCost=").append(totalCost.toPlainString())
                .append(" currency=").append(currency)
                .toString();
    }
}
