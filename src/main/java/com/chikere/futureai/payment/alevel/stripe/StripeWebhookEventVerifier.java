package com.chikere.futureai.payment.alevel.stripe;

import com.chikere.futureai.payment.alevel.application.ALevelPaymentNotification;

import java.util.Optional;

interface StripeWebhookEventVerifier {
    Optional<StripeVerifiedEvent> verify(ALevelPaymentNotification notification);
}
