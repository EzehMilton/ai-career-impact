package com.chikere.futureai.payment.professional.application;

import com.chikere.futureai.payment.complimentary.ComplimentaryRedemptionResult;
import com.chikere.futureai.report.professional.application.ProfessionalReport;

import java.util.Optional;
import java.util.UUID;

public interface ProfessionalReportPurchaseUseCase {
    ProfessionalCheckoutSession startCheckout(UUID assessmentId);

    ProfessionalPaymentProcessingResult processPaymentNotification(ProfessionalPaymentNotification notification);

    ProfessionalPurchaseAccess accessFor(UUID assessmentId);

    Optional<ProfessionalReport> findPurchasedReport(UUID assessmentId);

    Optional<ProfessionalReportEntitlement> findEntitlement(UUID assessmentId);

    /**
     * Grants the ordinary full report for a valid complimentary access code, without any payment
     * provider. Idempotent per assessment: an already ACTIVE entitlement is left untouched.
     */
    ComplimentaryRedemptionResult redeemComplimentaryAccess(UUID assessmentId, String submittedCode);
}
