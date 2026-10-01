package com.chikere.futureai.payment.university.application;

public record UniversityPaymentNotification(String payload, String signature) {
    public UniversityPaymentNotification {
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("payload must not be blank");
        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("signature must not be blank");
        }
    }
}
