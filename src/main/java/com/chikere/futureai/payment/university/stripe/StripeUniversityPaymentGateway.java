package com.chikere.futureai.payment.university.stripe;

import com.chikere.futureai.payment.university.application.*;

import java.util.Optional;

public final class StripeUniversityPaymentGateway implements UniversityPaymentGateway {
    private final StripeCheckoutOperations checkoutOperations;
    private final StripeWebhookEventVerifier webhookVerifier;
    private final String priceId;

    StripeUniversityPaymentGateway(StripeCheckoutOperations checkoutOperations,
            StripeWebhookEventVerifier webhookVerifier, String priceId) {
        this.checkoutOperations = checkoutOperations;
        this.webhookVerifier = webhookVerifier;
        if (priceId == null || priceId.isBlank()) {
            throw new IllegalArgumentException("Stripe university price ID must not be blank");
        }
        this.priceId = priceId.strip();
    }

    @Override
    public UniversityCheckoutSession createCheckoutSession(UniversityCheckoutRequest request) {
        StripeCheckoutData result = checkoutOperations.create(request);
        return new UniversityCheckoutSession(result.sessionId(), result.redirectUrl(), provider(),
                result.expiresAt());
    }

    @Override
    public Optional<UniversityPaymentEvent> verifyPaymentNotification(
            UniversityPaymentNotification notification) {
        return webhookVerifier.verify(notification).map(event -> new UniversityPaymentEvent(event.eventId(),
                provider(), event.sessionId(), event.entitlementId(), event.assessmentId(),
                event.paymentReference(), event.occurredAt()));
    }

    @Override
    public UniversityPaymentProvider provider() {
        return UniversityPaymentProvider.STRIPE;
    }

    @Override
    public String priceConfigurationKey() {
        return priceId;
    }
}
