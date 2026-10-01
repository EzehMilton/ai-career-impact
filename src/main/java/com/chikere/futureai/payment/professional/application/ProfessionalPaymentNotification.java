package com.chikere.futureai.payment.professional.application;

public record ProfessionalPaymentNotification(String payload, String signature) {
    public ProfessionalPaymentNotification {
        if (payload == null || payload.isBlank()) throw new IllegalArgumentException("payload must not be blank");
        if (signature == null || signature.isBlank()) throw new IllegalArgumentException("signature must not be blank");
    }
}
