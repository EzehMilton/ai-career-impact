package com.chikere.futureai.report.alevel.detailed;

import java.util.Optional;
import java.util.UUID;

/** Insert-only storage for versioned A-level detailed-report snapshots. */
public interface ALevelDetailedReportStore {

    Optional<ALevelDetailedReportSnapshot> findByAssessmentId(UUID assessmentId);

    /**
     * Stores the snapshot unless the assessment already has one, and returns the canonical stored snapshot either
     * way. An existing snapshot is never overwritten; when two callers race, the first committed wins.
     */
    ALevelDetailedReportSnapshot saveIfAbsent(ALevelDetailedReportSnapshot snapshot);
}
