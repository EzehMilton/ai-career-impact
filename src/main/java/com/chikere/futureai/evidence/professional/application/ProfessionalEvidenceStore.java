package com.chikere.futureai.evidence.professional.application;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalEvidenceStore {
    void save(ProfessionalEvidenceBundle bundle);
    Optional<ProfessionalEvidenceBundle> findByAssessmentId(UUID assessmentId);
}
