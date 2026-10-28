package com.chikere.futureai.payment.alevel.application;

import com.chikere.futureai.payment.complimentary.ComplimentaryRedemptionResult;
import com.chikere.futureai.report.alevel.application.ALevelReport;

import java.util.Optional;
import java.util.UUID;

public interface ALevelReportPurchaseUseCase {
    ALevelCheckoutSession startCheckout(UUID assessmentId);

    ALevelPaymentProcessingResult processPaymentNotification(ALevelPaymentNotification notification);

    ALevelPurchaseAccess accessFor(UUID assessmentId);

    Optional<ALevelReport> findPurchasedReport(UUID assessmentId);

    Optional<ALevelReportEntitlement> findEntitlement(UUID assessmentId);

    /**
     * The report version of the assessment's ACTIVE purchase: {@code a-level-report-v1} for a historical v1
     * assessment, {@code a-level-report-v2} for a subject profile. Empty without an ACTIVE entitlement.
     */
    default Optional<String> activePurchaseVersion(UUID assessmentId) {
        return Optional.empty();
    }

    /**
     * Grants the ordinary full report for a valid complimentary access code, without any payment
     * provider. Idempotent per assessment: an already ACTIVE entitlement is left untouched.
     */
    ComplimentaryRedemptionResult redeemComplimentaryAccess(UUID assessmentId, String submittedCode);
}
