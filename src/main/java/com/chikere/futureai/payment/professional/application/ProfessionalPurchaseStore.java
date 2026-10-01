package com.chikere.futureai.payment.professional.application;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalPurchaseStore {
    void savePending(ProfessionalReportEntitlement entitlement);
    Optional<ProfessionalReportEntitlement> findEntitlementByAssessmentId(UUID assessmentId);
    Optional<ProfessionalReportEntitlement> findEntitlementById(UUID entitlementId);
    Optional<PurchasedProfessionalReportSnapshot> findSnapshotByAssessmentId(UUID assessmentId);
    boolean isEventProcessed(ProfessionalPaymentProvider provider, String eventId);
    PurchaseConfirmationResult confirmAndFreeze(ProfessionalPaymentEvent event,
            PurchasedProfessionalReportSnapshot snapshot);

    enum PurchaseConfirmationResult { ACTIVATED, ALREADY_PROCESSED }
}
