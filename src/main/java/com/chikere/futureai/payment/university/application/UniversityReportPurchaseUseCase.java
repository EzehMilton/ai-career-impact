package com.chikere.futureai.payment.university.application;

import com.chikere.futureai.payment.complimentary.ComplimentaryRedemptionResult;
import com.chikere.futureai.report.university.application.UniversityReport;

import java.util.Optional;
import java.util.UUID;

public interface UniversityReportPurchaseUseCase {
    UniversityCheckoutSession startCheckout(UUID assessmentId);

    UniversityPaymentProcessingResult processPaymentNotification(UniversityPaymentNotification notification);

    UniversityPurchaseAccess accessFor(UUID assessmentId);

    Optional<UniversityReport> findPurchasedReport(UUID assessmentId);

    Optional<UniversityReportEntitlement> findEntitlement(UUID assessmentId);

    /** The report version of an ACTIVE purchase (v1 document or v2 detailed report), or empty without one. */
    default Optional<String> activePurchaseVersion(UUID assessmentId) {
        return Optional.empty();
    }

    /**
     * Grants the ordinary full report for a valid complimentary access code, without any payment
     * provider. Idempotent per assessment: an already ACTIVE entitlement is left untouched.
     */
    ComplimentaryRedemptionResult redeemComplimentaryAccess(UUID assessmentId, String submittedCode);
}
