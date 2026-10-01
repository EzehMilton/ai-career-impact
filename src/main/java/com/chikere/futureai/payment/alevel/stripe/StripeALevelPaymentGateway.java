package com.chikere.futureai.payment.alevel.stripe;

import com.chikere.futureai.payment.alevel.application.*;

import java.util.Optional;

public final class StripeALevelPaymentGateway implements ALevelPaymentGateway {
    private final StripeCheckoutOperations checkoutOperations;
    private final StripeWebhookEventVerifier webhookVerifier;
    private final String priceId;

    StripeALevelPaymentGateway(StripeCheckoutOperations checkoutOperations,
            StripeWebhookEventVerifier webhookVerifier, String priceId) {
        this.checkoutOperations = checkoutOperations;
        this.webhookVerifier = webhookVerifier;
        if (priceId == null || priceId.isBlank()) {
            throw new IllegalArgumentException("Stripe A-level price ID must not be blank");
        }
        this.priceId = priceId.strip();
    }

    @Override
    public ALevelCheckoutSession createCheckoutSession(ALevelCheckoutRequest request) {
        StripeCheckoutData result = checkoutOperations.create(request);
        return new ALevelCheckoutSession(result.sessionId(), result.redirectUrl(), provider(),
                result.expiresAt());
    }

    @Override
    public Optional<ALevelPaymentEvent> verifyPaymentNotification(ALevelPaymentNotification notification) {
        return webhookVerifier.verify(notification).map(event -> new ALevelPaymentEvent(event.eventId(),
                provider(), event.sessionId(), event.entitlementId(), event.assessmentId(),
                event.paymentReference(), event.occurredAt()));
    }

    @Override
    public ALevelPaymentProvider provider() {
        return ALevelPaymentProvider.STRIPE;
    }

    @Override
    public String priceConfigurationKey() {
        return priceId;
    }
}
