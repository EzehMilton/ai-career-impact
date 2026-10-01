package com.chikere.futureai.evidence.university.application;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record UniversityEvidenceBundle(
        UUID assessmentId,
        String evidenceVersion,
        EvidenceSource source,
        String providerIdentifier,
        EvidenceStatus status,
        Instant retrievedAt,
        List<UniversityEvidenceItem> items) {

    public UniversityEvidenceBundle {
        Objects.requireNonNull(assessmentId);
        evidenceVersion = required(evidenceVersion, "evidenceVersion");
        Objects.requireNonNull(source);
        providerIdentifier = required(providerIdentifier, "providerIdentifier");
        Objects.requireNonNull(status);
        Objects.requireNonNull(retrievedAt);
        items = List.copyOf(Objects.requireNonNull(items));
        if ((status == EvidenceStatus.AVAILABLE || status == EvidenceStatus.PARTIAL) && items.isEmpty()) {
            throw new IllegalArgumentException(status + " evidence must contain at least one item");
        }
        if ((status == EvidenceStatus.PENDING || status == EvidenceStatus.FAILED) && !items.isEmpty()) {
            throw new IllegalArgumentException(status + " evidence must not contain items");
        }
        if (status == EvidenceStatus.NOT_REQUESTED) {
            throw new IllegalArgumentException("NOT_REQUESTED is represented by no stored bundle");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
