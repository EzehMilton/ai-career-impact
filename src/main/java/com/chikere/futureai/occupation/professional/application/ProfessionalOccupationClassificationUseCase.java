package com.chikere.futureai.occupation.professional.application;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalOccupationClassificationUseCase {
    Optional<StoredProfessionalOccupationClassification> classifyFor(UUID assessmentId);
}
