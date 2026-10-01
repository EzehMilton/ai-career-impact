package com.chikere.futureai.payment.alevel.application;

import com.chikere.futureai.report.alevel.application.ALevelReportTier;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

public record ALevelCheckoutRequest(
        UUID entitlementId,
        UUID assessmentId,
        ALevelReportTier reportTier,
        String priceConfigurationKey,
        URI successUrl,
        URI cancelUrl) {

    public ALevelCheckoutRequest {
        Objects.requireNonNull(entitlementId);
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(reportTier);
        if (reportTier != ALevelReportTier.PAID) {
            throw new IllegalArgumentException("checkout is available only for the PAID report tier");
        }
        priceConfigurationKey = required(priceConfigurationKey, "priceConfigurationKey");
        requireHttp(successUrl, "successUrl");
        requireHttp(cancelUrl, "cancelUrl");
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
