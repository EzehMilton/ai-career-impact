package com.chikere.futureai.payment.university.application;

import com.chikere.futureai.report.university.application.UniversityReport;

import java.util.Optional;
import java.util.UUID;

public interface UniversityReportPurchaseUseCase {
    UniversityCheckoutSession startCheckout(UUID assessmentId);

    UniversityPaymentProcessingResult processPaymentNotification(UniversityPaymentNotification notification);

    UniversityPurchaseAccess accessFor(UUID assessmentId);

    Optional<UniversityReport> findPurchasedReport(UUID assessmentId);

    Optional<UniversityReportEntitlement> findEntitlement(UUID assessmentId);
}
