package com.chikere.futureai.narrative.alevel.application;

import java.util.Optional;
import java.util.UUID;

public interface ALevelNarrativeStore {
    void save(StoredALevelNarrative narrative);

    Optional<StoredALevelNarrative> findByAssessmentId(UUID assessmentId);
}
