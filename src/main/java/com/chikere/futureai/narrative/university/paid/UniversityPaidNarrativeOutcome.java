package com.chikere.futureai.narrative.university.paid;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of one paid-narrative attempt: AVAILABLE with validated prose, a final FALLBACK (prose failed
 * validation and its repair), or a RETRYABLE_FALLBACK (no usable provider response). Only the first two may be frozen
 * into a report.
 *
 * @param attempts provider calls made for this outcome (0 when no provider was configured)
 * @param reason why the narrative is not available, for diagnostics; never shown to customers
 */
public record UniversityPaidNarrativeOutcome(
        UniversityPaidNarrativeStatus status,
        Optional<UniversityPaidNarrative> narrative,
        String narrativeVersion,
        String provider,
        Optional<String> model,
        Optional<String> providerRequestId,
        int attempts,
        Optional<String> reason) {

    public UniversityPaidNarrativeOutcome {
        Objects.requireNonNull(status);
        Objects.requireNonNull(narrative);
        Objects.requireNonNull(narrativeVersion);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(model);
        Objects.requireNonNull(providerRequestId);
        Objects.requireNonNull(reason);
        if ((status == UniversityPaidNarrativeStatus.AVAILABLE) != narrative.isPresent()) {
            throw new IllegalArgumentException("only an AVAILABLE outcome carries narrative text");
        }
        if (status != UniversityPaidNarrativeStatus.AVAILABLE && reason.isEmpty()) {
            throw new IllegalArgumentException("a fallback records why");
        }
    }

    public boolean retryable() {
        return status == UniversityPaidNarrativeStatus.RETRYABLE_FALLBACK;
    }
}
