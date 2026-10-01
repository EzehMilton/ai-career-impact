package com.chikere.futureai.product.application;

import java.util.Objects;
import java.util.Optional;

/**
 * Whether the application is running in explicit local development mode, and which fixtures are
 * active if it is.
 *
 * <p>This exists so the product shell can label development output honestly. It is derived from the
 * {@code local-fixture} Spring profile only — never from a request, a header or a query parameter —
 * so no production deployment can be persuaded to render development chrome.</p>
 */
public record DevelopmentModeStatus(boolean active, Optional<String> fixtureSummary) {

    public DevelopmentModeStatus {
        fixtureSummary = Objects.requireNonNull(fixtureSummary, "fixtureSummary must not be null")
                .map(String::strip).filter(value -> !value.isEmpty());
        if (!active && fixtureSummary.isPresent()) {
            throw new IllegalArgumentException("only active development mode may describe fixtures");
        }
    }

    /** The normal, non-development state. */
    public static DevelopmentModeStatus inactive() {
        return new DevelopmentModeStatus(false, Optional.empty());
    }

    public static DevelopmentModeStatus active(String fixtureSummary) {
        return new DevelopmentModeStatus(true, Optional.ofNullable(fixtureSummary));
    }
}
