package com.chikere.futureai.narrative.professional.paid;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One immutable, canonical paid-narrative outcome for an assessment (AVAILABLE or final FALLBACK), tied to the
 * analysis and guidance it explains. Stored once; a later request returns it unchanged and makes no provider call.
 */
public record StoredProfessionalPaidNarrative(
        UUID narrativeId,
        UUID assessmentId,
        UUID analysisId,
        UUID guidanceId,
        String narrativeVersion,
        PaidNarrativeStatus status,
        String provider,
        Optional<String> model,
        Optional<String> providerRequestId,
        int attempts,
        Optional<String> fallbackReason,
        Instant generatedAt,
        Optional<ProfessionalPaidNarrative> narrative) {

    public StoredProfessionalPaidNarrative {
        Objects.requireNonNull(narrativeId);
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(analysisId);
        Objects.requireNonNull(guidanceId);
        Objects.requireNonNull(narrativeVersion);
        Objects.requireNonNull(status);
        Objects.requireNonNull(provider);
        Objects.requireNonNull(model);
        Objects.requireNonNull(providerRequestId);
        Objects.requireNonNull(fallbackReason);
        Objects.requireNonNull(generatedAt);
        Objects.requireNonNull(narrative);
        if (attempts < 0 || attempts > 2) {
            throw new IllegalArgumentException("a paid narrative makes at most one call and one repair");
        }
        if (status == PaidNarrativeStatus.RETRYABLE_FALLBACK) {
            throw new IllegalArgumentException("a retryable fallback is never stored");
        }
        if ((status == PaidNarrativeStatus.AVAILABLE) != narrative.isPresent()) {
            throw new IllegalArgumentException("only an AVAILABLE paid narrative carries narrative text");
        }
        if (status == PaidNarrativeStatus.FALLBACK && fallbackReason.isEmpty()) {
            throw new IllegalArgumentException("a FALLBACK paid narrative records why");
        }
    }
}
