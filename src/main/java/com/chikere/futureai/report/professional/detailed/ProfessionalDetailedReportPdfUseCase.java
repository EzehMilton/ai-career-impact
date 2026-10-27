package com.chikere.futureai.report.professional.detailed;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalDetailedReportPdfUseCase {

    /**
     * The v5 PDF of the detailed report: for a stored report, its stored bytes (rendered and stored on first request);
     * for a provisional report, rendered on demand and never stored. Empty when the assessment has no detailed report
     * (the role-level PDF then applies); throws when the entitlement is not ACTIVE.
     */
    Optional<DetailedReportPdfDownload> findOrCreate(UUID assessmentId);

    record DetailedReportPdfDownload(String filename, byte[] bytes, boolean provisional) {
        public DetailedReportPdfDownload {
            java.util.Objects.requireNonNull(filename);
            bytes = java.util.Objects.requireNonNull(bytes).clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }
    }
}
