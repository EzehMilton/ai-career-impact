package com.chikere.futureai.payment.university.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UniversityPurchaseStore {
    void savePending(UniversityReportEntitlement entitlement);

    /**
     * Points a still-PENDING entitlement at a new checkout session after its previous session
     * expired unpaid. The status is never changed here.
     */
    void replaceExpiredCheckout(UUID entitlementId, String expiredCheckoutSessionId,
            UniversityCheckoutSession replacement, String priceConfigurationKey, Instant replacedAt);

    Optional<UniversityReportEntitlement> findEntitlementByAssessmentId(UUID assessmentId);

    Optional<UniversityReportEntitlement> findEntitlementById(UUID entitlementId);

    Optional<PurchasedUniversityReportSnapshot> findSnapshotByAssessmentId(UUID assessmentId);

    boolean isEventProcessed(UniversityPaymentProvider provider, String eventId);

    PurchaseConfirmationResult confirmAndFreeze(UniversityPaymentEvent event,
            PurchasedUniversityReportSnapshot snapshot);

    enum PurchaseConfirmationResult { ACTIVATED, ALREADY_PROCESSED }
}
