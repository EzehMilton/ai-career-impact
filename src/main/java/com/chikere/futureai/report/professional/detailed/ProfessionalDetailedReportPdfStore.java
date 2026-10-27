package com.chikere.futureai.report.professional.detailed;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalDetailedReportPdfStore {

    Optional<StoredProfessionalDetailedReportPdf> findByReportId(UUID reportId);

    /** Stores the candidate when absent and always returns the single persisted PDF. */
    StoredProfessionalDetailedReportPdf saveIfAbsent(StoredProfessionalDetailedReportPdf candidate);
}
