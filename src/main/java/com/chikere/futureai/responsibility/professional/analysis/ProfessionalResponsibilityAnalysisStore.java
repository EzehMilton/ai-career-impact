package com.chikere.futureai.responsibility.professional.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage for completed responsibility-analysis snapshots and failed attempts. */
public interface ProfessionalResponsibilityAnalysisStore {

    Optional<ProfessionalResponsibilityAnalysisSnapshot> findCompletedByAssessmentId(UUID assessmentId);

    /**
     * Stores the snapshot unless the assessment already has one, and returns the canonical stored snapshot either
     * way. An existing snapshot is never overwritten; when two callers race, the first committed wins and the other
     * receives it.
     */
    ProfessionalResponsibilityAnalysisSnapshot saveCompletedIfAbsent(ProfessionalResponsibilityAnalysisSnapshot snapshot);

    void recordFailure(ResponsibilityAnalysisFailure failure);

    List<ResponsibilityAnalysisFailure> findFailuresByAssessmentId(UUID assessmentId);
}
