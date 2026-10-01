package com.chikere.futureai.payment.professional.application;

import com.chikere.futureai.report.professional.application.ProfessionalReportTier;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ProfessionalReportEntitlement(
        UUID entitlementId,
        UUID assessmentId,
        ProfessionalReportTier reportTier,
        ProfessionalEntitlementStatus status,
        ProfessionalPaymentProvider paymentProvider,
        String providerCheckoutSessionId,
        URI checkoutUrl,
        String priceConfigurationKey,
        Optional<String> providerPaymentReference,
        Optional<String> purchasedReportVersion,
        Instant createdAt,
        Optional<Instant> confirmedAt) {

    public ProfessionalReportEntitlement {
        Objects.requireNonNull(entitlementId); Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(reportTier); Objects.requireNonNull(status); Objects.requireNonNull(paymentProvider);
        providerCheckoutSessionId = required(providerCheckoutSessionId, "providerCheckoutSessionId");
        Objects.requireNonNull(checkoutUrl);
        if (!checkoutUrl.isAbsolute() || !("http".equalsIgnoreCase(checkoutUrl.getScheme())
                || "https".equalsIgnoreCase(checkoutUrl.getScheme()))) {
            throw new IllegalArgumentException("checkoutUrl must be an absolute HTTP(S) URL");
        }
        priceConfigurationKey = required(priceConfigurationKey, "priceConfigurationKey");
        providerPaymentReference = clean(providerPaymentReference);
        purchasedReportVersion = clean(purchasedReportVersion);
        Objects.requireNonNull(createdAt); Objects.requireNonNull(confirmedAt);
        if (reportTier != ProfessionalReportTier.PAID) throw new IllegalArgumentException("entitlement must be PAID");
        if (status == ProfessionalEntitlementStatus.ACTIVE
                && (purchasedReportVersion.isEmpty() || confirmedAt.isEmpty())) {
            throw new IllegalArgumentException("active entitlement requires report version and confirmation time");
        }
    }

    private static Optional<String> clean(Optional<String> value) {
        return Objects.requireNonNull(value).map(String::strip).filter(text -> !text.isEmpty());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
