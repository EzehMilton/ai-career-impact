package com.chikere.futureai.report.professional.detailed;

import java.util.Optional;
import java.util.UUID;

/** Insert-once storage of assembled detailed reports, one per assessment. */
public interface ProfessionalDetailedReportStore {

    Optional<ProfessionalDetailedReportSnapshot> findByAssessmentId(UUID assessmentId);

    /** Stores the snapshot unless one exists and returns the canonical stored one; never overwrites. */
    ProfessionalDetailedReportSnapshot saveIfAbsent(ProfessionalDetailedReportSnapshot snapshot);
}
