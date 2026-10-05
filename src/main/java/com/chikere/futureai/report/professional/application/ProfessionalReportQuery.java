package com.chikere.futureai.report.professional.application;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalReportQuery {
    Optional<ProfessionalReport> assemble(UUID assessmentId, ProfessionalReportTier tier);
}
