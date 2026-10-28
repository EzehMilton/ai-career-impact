package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;

import java.util.Optional;
import java.util.UUID;

public interface ALevelReportQuery {

    /** The free or paid a-level-report-v1 of a historical v1 assessment; empty for a v2 subject profile. */
    Optional<ALevelReport> assemble(UUID assessmentId, ALevelReportTier tier);

    /** The stored assessment, whichever policy decided it, so callers can route by its stored version. */
    default Optional<StoredALevelAssessment> stored(UUID assessmentId) {
        return Optional.empty();
    }

    /** The free subject profile of an {@code a-level-decision-v2} assessment; empty for a historical v1 one. */
    default Optional<ALevelSubjectProfile> subjectProfile(UUID assessmentId) {
        return Optional.empty();
    }
}
