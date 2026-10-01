package com.chikere.futureai.payment.professional.application;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ProfessionalCheckoutSession(
        String checkoutSessionId,
        URI redirectUrl,
        ProfessionalPaymentProvider provider,
        Optional<Instant> expiresAt) {

    public ProfessionalCheckoutSession {
        if (checkoutSessionId == null || checkoutSessionId.isBlank()) {
            throw new IllegalArgumentException("checkoutSessionId must not be blank");
        }
        checkoutSessionId = checkoutSessionId.strip();
        Objects.requireNonNull(redirectUrl);
        if (!redirectUrl.isAbsolute() || !("http".equalsIgnoreCase(redirectUrl.getScheme())
                || "https".equalsIgnoreCase(redirectUrl.getScheme()))) {
            throw new IllegalArgumentException("redirectUrl must be an absolute HTTP(S) URL");
        }
        Objects.requireNonNull(provider);
        Objects.requireNonNull(expiresAt);
    }
}
