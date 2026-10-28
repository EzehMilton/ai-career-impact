package com.chikere.futureai.narrative.university.application;

import java.util.Optional;
import java.util.UUID;

public interface UniversityNarrativeGeneration {
    StoredUniversityNarrative generateFor(UUID assessmentId);

    /**
     * The free narrative of a historical v1 assessment, generated or reused; empty when the assessment's decision
     * has no free narrative (a v2 profile), in which case no provider is called and nothing is stored.
     */
    default Optional<StoredUniversityNarrative> generateIfApplicable(UUID assessmentId) {
        return Optional.of(generateFor(assessmentId));
    }
}
