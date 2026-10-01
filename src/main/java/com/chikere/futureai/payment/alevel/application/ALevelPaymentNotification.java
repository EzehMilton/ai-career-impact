package com.chikere.futureai.payment.alevel.application;

public record ALevelPaymentNotification(String payload, String signature) {
    public ALevelPaymentNotification {
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("payload must not be blank");
        if (signature == null || signature.isBlank()) {
            throw new IllegalArgumentException("signature must not be blank");
        }
    }
}
