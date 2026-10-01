package com.chikere.futureai.evidence.university.application;

import java.util.Optional;
import java.util.UUID;

public interface UniversityEvidenceStore {
    void save(UniversityEvidenceBundle bundle);

    Optional<UniversityEvidenceBundle> findByAssessmentId(UUID assessmentId);
}
