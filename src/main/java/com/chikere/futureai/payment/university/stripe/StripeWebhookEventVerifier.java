package com.chikere.futureai.payment.university.stripe;

import com.chikere.futureai.payment.university.application.UniversityPaymentNotification;

import java.util.Optional;

interface StripeWebhookEventVerifier {
    Optional<StripeVerifiedEvent> verify(UniversityPaymentNotification notification);
}
