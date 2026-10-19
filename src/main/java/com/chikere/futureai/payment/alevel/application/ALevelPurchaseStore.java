package com.chikere.futureai.payment.alevel.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ALevelPurchaseStore {
    void savePending(ALevelReportEntitlement entitlement);

    /**
     * Points a still-PENDING entitlement at a new checkout session after its previous session
     * expired unpaid. The status is never changed here.
     */
    void replaceExpiredCheckout(UUID entitlementId, String expiredCheckoutSessionId,
            ALevelCheckoutSession replacement, String priceConfigurationKey, Instant replacedAt);

    Optional<ALevelReportEntitlement> findEntitlementByAssessmentId(UUID assessmentId);

    Optional<ALevelReportEntitlement> findEntitlementById(UUID entitlementId);

    Optional<PurchasedALevelReportSnapshot> findSnapshotByAssessmentId(UUID assessmentId);

    boolean isEventProcessed(ALevelPaymentProvider provider, String eventId);

    PurchaseConfirmationResult confirmAndFreeze(ALevelPaymentEvent event,
            PurchasedALevelReportSnapshot snapshot);

    /**
     * Creates an already ACTIVE complimentary entitlement together with its frozen report, in one
     * transaction. Fails if the assessment already has an entitlement of any kind.
     */
    void saveComplimentary(ALevelReportEntitlement entitlement, PurchasedALevelReportSnapshot snapshot);

    enum PurchaseConfirmationResult { ACTIVATED, ALREADY_PROCESSED }
}
