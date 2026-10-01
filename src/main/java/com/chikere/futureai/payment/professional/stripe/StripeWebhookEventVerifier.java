package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.ProfessionalPaymentNotification;

import java.util.Optional;

interface StripeWebhookEventVerifier {
    Optional<StripeVerifiedEvent> verify(ProfessionalPaymentNotification notification);
}
