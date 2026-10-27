package com.chikere.futureai.responsibility.professional.guidance;

import java.util.Optional;
import java.util.UUID;

/** Storage for immutable guidance snapshots, one per assessment. */
public interface ProfessionalResponsibilityGuidanceStore {

    Optional<ProfessionalResponsibilityGuidanceSnapshot> findByAssessmentId(UUID assessmentId);

    /** Stores the snapshot unless one exists, and returns the canonical stored snapshot; never overwrites. */
    ProfessionalResponsibilityGuidanceSnapshot saveIfAbsent(ProfessionalResponsibilityGuidanceSnapshot snapshot);
}
