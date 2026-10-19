package com.chikere.futureai.payment.professional.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface ProfessionalPurchaseStore {
    void savePending(ProfessionalReportEntitlement entitlement);

    /**
     * Points a still-PENDING entitlement at a new checkout session after its previous session
     * expired unpaid. The status is never changed here.
     */
    void replaceExpiredCheckout(UUID entitlementId, String expiredCheckoutSessionId,
            ProfessionalCheckoutSession replacement, String priceConfigurationKey, Instant replacedAt);
    Optional<ProfessionalReportEntitlement> findEntitlementByAssessmentId(UUID assessmentId);
    Optional<ProfessionalReportEntitlement> findEntitlementById(UUID entitlementId);
    Optional<PurchasedProfessionalReportSnapshot> findSnapshotByAssessmentId(UUID assessmentId);
    boolean isEventProcessed(ProfessionalPaymentProvider provider, String eventId);
    PurchaseConfirmationResult confirmAndFreeze(ProfessionalPaymentEvent event,
            PurchasedProfessionalReportSnapshot snapshot);

    /**
     * Creates an already ACTIVE complimentary entitlement together with its frozen report, in one
     * transaction. Fails if the assessment already has an entitlement of any kind.
     */
    void saveComplimentary(ProfessionalReportEntitlement entitlement, PurchasedProfessionalReportSnapshot snapshot);

    enum PurchaseConfirmationResult { ACTIVATED, ALREADY_PROCESSED }
}
