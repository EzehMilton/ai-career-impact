package com.chikere.futureai.narrative.professional.paid;

import java.util.Optional;
import java.util.UUID;

/** Insert-once storage of paid narratives, one per assessment. */
public interface ProfessionalPaidNarrativeStore {

    Optional<StoredProfessionalPaidNarrative> findByAssessmentId(UUID assessmentId);

    /** Stores the narrative unless one exists and returns the canonical stored one; never overwrites. */
    StoredProfessionalPaidNarrative saveIfAbsent(StoredProfessionalPaidNarrative narrative);
}
