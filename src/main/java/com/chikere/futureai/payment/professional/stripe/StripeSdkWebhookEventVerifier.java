package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.InvalidPaymentNotificationException;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentNotification;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

final class StripeSdkWebhookEventVerifier implements StripeWebhookEventVerifier {
    private static final String CHECKOUT_COMPLETED = "checkout.session.completed";
    private final String webhookSecret;

    StripeSdkWebhookEventVerifier(String webhookSecret) {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            throw new IllegalArgumentException("Stripe webhook secret must not be blank");
        }
        this.webhookSecret = webhookSecret.strip();
    }

    @Override
    public Optional<StripeVerifiedEvent> verify(ProfessionalPaymentNotification notification) {
        final Event event;
        try {
            event = Webhook.constructEvent(notification.payload(), notification.signature(), webhookSecret);
        }
        catch (SignatureVerificationException | RuntimeException invalid) {
            throw new InvalidPaymentNotificationException("Stripe webhook signature or payload is invalid", invalid);
        }
        if (!CHECKOUT_COMPLETED.equals(event.getType())) return Optional.empty();
        StripeObject object = event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> new InvalidPaymentNotificationException("Stripe checkout event could not be decoded"));
        if (!(object instanceof Session session)) {
            throw new InvalidPaymentNotificationException("Stripe event does not contain a checkout session");
        }
        if (!"paid".equals(session.getPaymentStatus())) {
            throw new InvalidPaymentNotificationException("Stripe checkout is not paid");
        }
        try {
            String entitlement = session.getMetadata().get("entitlement_id");
            String assessment = session.getMetadata().get("assessment_id");
            return Optional.of(new StripeVerifiedEvent(required(event.getId(), "event id"),
                    required(session.getId(), "checkout session id"), UUID.fromString(entitlement),
                    UUID.fromString(assessment), Optional.ofNullable(session.getPaymentIntent()),
                    Instant.ofEpochSecond(event.getCreated())));
        }
        catch (RuntimeException malformed) {
            throw new InvalidPaymentNotificationException("Stripe checkout metadata is invalid", malformed);
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
