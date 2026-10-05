package com.chikere.futureai.evidence.university.application;

import java.util.Optional;
import java.util.UUID;

public interface UniversityEvidenceGathering {
    Optional<UniversityEvidenceBundle> gatherFor(UUID assessmentId);
}
