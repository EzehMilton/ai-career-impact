package com.chikere.futureai.narrative.professional.paid;

import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of one paid-narrative request: either the canonical stored narrative (AVAILABLE or final FALLBACK) or a
 * retryable fallback that was deliberately not stored, with the reason and the number of provider calls made.
 */
public record PaidNarrativeResult(Optional<StoredProfessionalPaidNarrative> stored, Optional<String> retryReason,
        int attempts) {

    public PaidNarrativeResult {
        Objects.requireNonNull(stored);
        Objects.requireNonNull(retryReason);
        if (stored.isPresent() == retryReason.isPresent()) {
            throw new IllegalArgumentException("a result is either stored or retryable");
        }
    }

    static PaidNarrativeResult stored(StoredProfessionalPaidNarrative narrative) {
        return new PaidNarrativeResult(Optional.of(narrative), Optional.empty(), narrative.attempts());
    }

    static PaidNarrativeResult retryable(String reason, int attempts) {
        return new PaidNarrativeResult(Optional.empty(), Optional.of(reason), attempts);
    }

    public PaidNarrativeStatus status() {
        return stored.map(StoredProfessionalPaidNarrative::status).orElse(PaidNarrativeStatus.RETRYABLE_FALLBACK);
    }

    public boolean retryable() {
        return stored.isEmpty();
    }
}
