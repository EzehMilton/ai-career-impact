package com.chikere.futureai.occupation.professional.application;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalOccupationClassificationStore {
    void save(StoredProfessionalOccupationClassification classification);
    Optional<StoredProfessionalOccupationClassification> findByAssessmentId(UUID assessmentId);
}
