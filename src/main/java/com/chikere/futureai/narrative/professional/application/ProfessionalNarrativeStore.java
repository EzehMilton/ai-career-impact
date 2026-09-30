package com.chikere.futureai.narrative.professional.application;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalNarrativeStore {
    void save(StoredProfessionalNarrative narrative);
    Optional<StoredProfessionalNarrative> findByAssessmentId(UUID assessmentId);
}
