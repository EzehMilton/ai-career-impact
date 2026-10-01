package com.chikere.futureai.payment.alevel.application;

import java.util.Optional;
import java.util.UUID;

public interface ALevelPurchaseStore {
    void savePending(ALevelReportEntitlement entitlement);

    Optional<ALevelReportEntitlement> findEntitlementByAssessmentId(UUID assessmentId);

    Optional<ALevelReportEntitlement> findEntitlementById(UUID entitlementId);

    Optional<PurchasedALevelReportSnapshot> findSnapshotByAssessmentId(UUID assessmentId);

    boolean isEventProcessed(ALevelPaymentProvider provider, String eventId);

    PurchaseConfirmationResult confirmAndFreeze(ALevelPaymentEvent event,
            PurchasedALevelReportSnapshot snapshot);

    enum PurchaseConfirmationResult { ACTIVATED, ALREADY_PROCESSED }
}
