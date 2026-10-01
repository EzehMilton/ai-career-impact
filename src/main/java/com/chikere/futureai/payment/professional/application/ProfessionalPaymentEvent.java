package com.chikere.futureai.payment.professional.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record ProfessionalPaymentEvent(
        String eventId,
        ProfessionalPaymentProvider provider,
        String checkoutSessionId,
        UUID entitlementId,
        UUID assessmentId,
        Optional<String> paymentReference,
        Instant occurredAt) {

    public ProfessionalPaymentEvent {
        eventId = required(eventId, "eventId");
        Objects.requireNonNull(provider);
        checkoutSessionId = required(checkoutSessionId, "checkoutSessionId");
        Objects.requireNonNull(entitlementId);
        Objects.requireNonNull(assessmentId);
        paymentReference = Objects.requireNonNull(paymentReference).map(String::strip).filter(value -> !value.isEmpty());
        Objects.requireNonNull(occurredAt);
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
