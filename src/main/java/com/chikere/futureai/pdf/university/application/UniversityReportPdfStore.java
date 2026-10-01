package com.chikere.futureai.pdf.university.application;

import java.util.Optional;
import java.util.UUID;

public interface UniversityReportPdfStore {
    Optional<PurchasedUniversityReportPdf> findByEntitlementId(UUID entitlementId);

    /** Atomically stores the candidate when absent and always returns the single persisted winner. */
    PurchasedUniversityReportPdf saveIfAbsent(PurchasedUniversityReportPdf candidate);
}
