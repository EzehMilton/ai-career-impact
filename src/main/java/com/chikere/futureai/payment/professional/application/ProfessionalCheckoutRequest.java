package com.chikere.futureai.payment.professional.application;

import com.chikere.futureai.report.professional.application.ProfessionalReportTier;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ProfessionalCheckoutRequest(
        UUID entitlementId,
        UUID assessmentId,
        ProfessionalReportTier reportTier,
        String priceConfigurationKey,
        URI successUrl,
        URI cancelUrl,
        Optional<String> replacesCheckoutSessionId) {

    public ProfessionalCheckoutRequest {
        Objects.requireNonNull(entitlementId);
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(reportTier);
        if (reportTier != ProfessionalReportTier.PAID) {
            throw new IllegalArgumentException("checkout is available only for the PAID report tier");
        }
        priceConfigurationKey = required(priceConfigurationKey, "priceConfigurationKey");
        requireHttp(successUrl, "successUrl");
        requireHttp(cancelUrl, "cancelUrl");
        replacesCheckoutSessionId = Objects.requireNonNull(replacesCheckoutSessionId)
                .map(String::strip).filter(value -> !value.isEmpty());
    }

    /** A first checkout for this entitlement, replacing nothing. */
    public ProfessionalCheckoutRequest(UUID entitlementId, UUID assessmentId, ProfessionalReportTier reportTier,
            String priceConfigurationKey, URI successUrl, URI cancelUrl) {
        this(entitlementId, assessmentId, reportTier, priceConfigurationKey, successUrl, cancelUrl, Optional.empty());
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }

    private static void requireHttp(URI value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (!value.isAbsolute() || !("http".equalsIgnoreCase(value.getScheme())
                || "https".equalsIgnoreCase(value.getScheme()))) {
            throw new IllegalArgumentException(name + " must be an absolute HTTP(S) URL");
        }
    }
}
