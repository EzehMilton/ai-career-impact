package com.chikere.futureai.assessment.alevel.application;

import java.util.Optional;
import java.util.UUID;

/** Persistence capability required by the A-level assessment use case. */
public interface ALevelAssessmentStore {

    void save(StoredALevelAssessment assessment);

    Optional<StoredALevelAssessment> findById(UUID id);
}
