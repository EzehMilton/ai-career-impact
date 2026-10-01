package com.chikere.futureai.narrative.university.application;

import java.util.Optional;
import java.util.UUID;

public interface UniversityNarrativeStore {
    void save(StoredUniversityNarrative narrative);

    Optional<StoredUniversityNarrative> findByAssessmentId(UUID assessmentId);
}
