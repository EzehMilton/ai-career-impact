package com.chikere.futureai.narrative.professional.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Persisted lifecycle and provenance for one assessment's explanation. */
public record StoredProfessionalNarrative(
        UUID assessmentId,
        NarrativeStatus status,
        Optional<ProfessionalNarrative> narrative,
        NarrativeSource source,
        String providerIdentifier,
        Optional<String> modelIdentifier,
        String promptSchemaVersion,
        Instant attemptedAt,
        Optional<Instant> generatedAt) {

    public StoredProfessionalNarrative {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(narrative, "narrative must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (providerIdentifier == null || providerIdentifier.isBlank()) {
            throw new IllegalArgumentException("providerIdentifier must not be blank");
        }
        providerIdentifier = providerIdentifier.strip();
        Objects.requireNonNull(modelIdentifier, "modelIdentifier must not be null");
        if (promptSchemaVersion == null || promptSchemaVersion.isBlank()) {
            throw new IllegalArgumentException("promptSchemaVersion must not be blank");
        }
        promptSchemaVersion = promptSchemaVersion.strip();
        Objects.requireNonNull(attemptedAt, "attemptedAt must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        boolean available = status == NarrativeStatus.AVAILABLE;
        if (available != narrative.isPresent() || available != generatedAt.isPresent()) {
            throw new IllegalArgumentException("only AVAILABLE narratives have content and a generated timestamp");
        }
        if (status == NarrativeStatus.NOT_REQUESTED) {
            throw new IllegalArgumentException("NOT_REQUESTED is represented by no stored narrative row");
        }
    }
}
