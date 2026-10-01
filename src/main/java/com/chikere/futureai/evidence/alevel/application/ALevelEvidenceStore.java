package com.chikere.futureai.evidence.alevel.application;

import java.util.Optional;
import java.util.UUID;

public interface ALevelEvidenceStore {
    void save(ALevelEvidenceBundle bundle);

    Optional<ALevelEvidenceBundle> findByAssessmentId(UUID assessmentId);
}
