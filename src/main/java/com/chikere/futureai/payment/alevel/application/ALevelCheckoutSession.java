package com.chikere.futureai.payment.alevel.application;

import java.net.URI;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ALevelCheckoutSession(
        String checkoutSessionId,
        URI redirectUrl,
        ALevelPaymentProvider provider,
        Optional<Instant> expiresAt) {

    public ALevelCheckoutSession {
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
