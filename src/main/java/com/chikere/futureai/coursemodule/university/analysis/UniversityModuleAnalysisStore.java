package com.chikere.futureai.coursemodule.university.analysis;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Storage for completed University module-analysis snapshots and failed attempts. Snapshots are insert-only. */
public interface UniversityModuleAnalysisStore {

    Optional<UniversityModuleAnalysisSnapshot> findCompletedByAssessmentId(UUID assessmentId);

    /**
     * Stores the snapshot unless the assessment already has one, and returns the canonical stored snapshot either
     * way. An existing snapshot is never overwritten; when two callers race, the first committed wins and the other
     * receives it.
     */
    UniversityModuleAnalysisSnapshot saveCompletedIfAbsent(UniversityModuleAnalysisSnapshot snapshot);

    void recordFailure(UniversityModuleAnalysisFailure failure);

    List<UniversityModuleAnalysisFailure> findFailuresByAssessmentId(UUID assessmentId);
}
