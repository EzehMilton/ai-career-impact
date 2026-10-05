package com.chikere.futureai.evidence.professional.application;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalEvidenceGathering {
    Optional<ProfessionalEvidenceBundle> gatherFor(UUID assessmentId);
}
