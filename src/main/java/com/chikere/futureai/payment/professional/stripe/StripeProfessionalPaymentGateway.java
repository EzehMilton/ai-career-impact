package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.*;

import java.util.Optional;

public final class StripeProfessionalPaymentGateway implements ProfessionalPaymentGateway {
    private final StripeCheckoutOperations checkoutOperations;
    private final StripeWebhookEventVerifier webhookVerifier;
    private final String priceId;

    StripeProfessionalPaymentGateway(StripeCheckoutOperations checkoutOperations,
            StripeWebhookEventVerifier webhookVerifier, String priceId) {
        this.checkoutOperations = checkoutOperations;
        this.webhookVerifier = webhookVerifier;
        if (priceId == null || priceId.isBlank()) throw new IllegalArgumentException("Stripe price ID must not be blank");
        this.priceId = priceId.strip();
    }

    @Override
    public ProfessionalCheckoutSession createCheckoutSession(ProfessionalCheckoutRequest request) {
        StripeCheckoutData result = checkoutOperations.create(request);
        return new ProfessionalCheckoutSession(result.sessionId(), result.redirectUrl(), provider(), result.expiresAt());
    }

    @Override
    public Optional<ProfessionalPaymentEvent> verifyPaymentNotification(ProfessionalPaymentNotification notification) {
        return webhookVerifier.verify(notification).map(event -> new ProfessionalPaymentEvent(event.eventId(),
                provider(), event.sessionId(), event.entitlementId(), event.assessmentId(),
                event.paymentReference(), event.occurredAt()));
    }

    @Override public ProfessionalPaymentProvider provider() { return ProfessionalPaymentProvider.STRIPE; }
    @Override public String priceConfigurationKey() { return priceId; }
}
