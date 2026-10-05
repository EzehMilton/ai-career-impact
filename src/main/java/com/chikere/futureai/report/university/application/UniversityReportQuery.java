package com.chikere.futureai.report.university.application;

import java.util.Optional;
import java.util.UUID;

public interface UniversityReportQuery {
    Optional<UniversityReport> assemble(UUID assessmentId, UniversityReportTier tier);
}
