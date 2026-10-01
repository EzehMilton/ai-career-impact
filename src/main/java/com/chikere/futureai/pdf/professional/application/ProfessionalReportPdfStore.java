package com.chikere.futureai.pdf.professional.application;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalReportPdfStore {
    Optional<PurchasedProfessionalReportPdf> findByEntitlementId(UUID entitlementId);

    /** Atomically stores the candidate when absent and always returns the single persisted winner. */
    PurchasedProfessionalReportPdf saveIfAbsent(PurchasedProfessionalReportPdf candidate);
}
