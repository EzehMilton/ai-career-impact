package com.chikere.futureai.evidence.alevel.application;

import java.util.Optional;
import java.util.UUID;

public interface ALevelEvidenceGathering {
    Optional<ALevelEvidenceBundle> gatherFor(UUID assessmentId);
}
