package com.chikere.futureai.payment.university.application;

import java.util.Optional;
import java.util.UUID;

public interface UniversityPurchaseStore {
    void savePending(UniversityReportEntitlement entitlement);

    Optional<UniversityReportEntitlement> findEntitlementByAssessmentId(UUID assessmentId);

    Optional<UniversityReportEntitlement> findEntitlementById(UUID entitlementId);

    Optional<PurchasedUniversityReportSnapshot> findSnapshotByAssessmentId(UUID assessmentId);

    boolean isEventProcessed(UniversityPaymentProvider provider, String eventId);

    PurchaseConfirmationResult confirmAndFreeze(UniversityPaymentEvent event,
            PurchasedUniversityReportSnapshot snapshot);

    enum PurchaseConfirmationResult { ACTIVATED, ALREADY_PROCESSED }
}
