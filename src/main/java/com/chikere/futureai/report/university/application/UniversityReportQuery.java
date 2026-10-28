package com.chikere.futureai.report.university.application;

import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;

import java.util.Optional;
import java.util.UUID;

public interface UniversityReportQuery {

    /** The free or paid university-report-v1 of a historical v1 assessment; empty for a v2 course profile. */
    Optional<UniversityReport> assemble(UUID assessmentId, UniversityReportTier tier);

    /** The stored assessment, whichever policy decided it, so callers can route by its stored version. */
    default Optional<StoredUniversityAssessment> stored(UUID assessmentId) {
        return Optional.empty();
    }

    /** The free course profile of a {@code university-decision-v2} assessment; empty for a historical v1 one. */
    default Optional<UniversityCourseProfile> courseProfile(UUID assessmentId) {
        return Optional.empty();
    }
}
