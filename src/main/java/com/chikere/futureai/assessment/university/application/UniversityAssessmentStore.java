package com.chikere.futureai.assessment.university.application;

import java.util.Optional;
import java.util.UUID;

/** Persistence capability required by the university assessment use case. */
public interface UniversityAssessmentStore {

    void save(StoredUniversityAssessment assessment);

    Optional<StoredUniversityAssessment> findById(UUID id);
}
