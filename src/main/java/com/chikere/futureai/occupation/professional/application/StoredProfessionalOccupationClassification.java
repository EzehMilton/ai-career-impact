package com.chikere.futureai.occupation.professional.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StoredProfessionalOccupationClassification(
        UUID assessmentId,
        ProfessionalOccupationClassification classification,
        Instant classifiedAt) {

    public StoredProfessionalOccupationClassification {
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(classification);
        Objects.requireNonNull(classifiedAt);
    }
}
