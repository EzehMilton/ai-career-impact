package com.chikere.futureai.assessment.professional.application;

import java.util.Optional;
import java.util.UUID;

/** Persistence capability required by the professional assessment use case. */
public interface ProfessionalAssessmentStore {

    void save(StoredProfessionalAssessment assessment);

    Optional<StoredProfessionalAssessment> findById(UUID id);
}
