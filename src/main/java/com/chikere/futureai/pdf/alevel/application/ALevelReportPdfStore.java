package com.chikere.futureai.pdf.alevel.application;

import java.util.Optional;
import java.util.UUID;

public interface ALevelReportPdfStore {
    Optional<PurchasedALevelReportPdf> findByEntitlementId(UUID entitlementId);

    /** Atomically stores the candidate when absent and always returns the single persisted winner. */
    PurchasedALevelReportPdf saveIfAbsent(PurchasedALevelReportPdf candidate);
}
