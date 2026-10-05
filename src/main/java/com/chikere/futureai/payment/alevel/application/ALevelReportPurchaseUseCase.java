package com.chikere.futureai.payment.alevel.application;

import com.chikere.futureai.report.alevel.application.ALevelReport;

import java.util.Optional;
import java.util.UUID;

public interface ALevelReportPurchaseUseCase {
    ALevelCheckoutSession startCheckout(UUID assessmentId);

    ALevelPaymentProcessingResult processPaymentNotification(ALevelPaymentNotification notification);

    ALevelPurchaseAccess accessFor(UUID assessmentId);

    Optional<ALevelReport> findPurchasedReport(UUID assessmentId);

    Optional<ALevelReportEntitlement> findEntitlement(UUID assessmentId);
}
