package com.chikere.futureai.report.university.detailed;

import java.util.Optional;
import java.util.UUID;

/** Insert-only storage for {@code university-report-v2} snapshots. */
public interface UniversityDetailedReportStore {

    Optional<UniversityDetailedReportSnapshot> findByAssessmentId(UUID assessmentId);

    /**
     * Stores the snapshot unless the assessment already has one, and returns the canonical stored snapshot either
     * way. An existing snapshot is never overwritten; when two callers race, the first committed wins.
     */
    UniversityDetailedReportSnapshot saveIfAbsent(UniversityDetailedReportSnapshot snapshot);
}
