package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.InvalidPaymentNotificationException;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentNotification;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

final class StripeSdkWebhookEventVerifier implements StripeWebhookEventVerifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(StripeSdkWebhookEventVerifier.class);
    private static final ObjectMapper JSON = new ObjectMapper();
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
            LOGGER.warn("Professional Stripe webhook rejected reason=INVALID_SIGNATURE_OR_PAYLOAD");
            throw new InvalidPaymentNotificationException("Stripe webhook signature or payload is invalid", invalid);
        }
        if (!CHECKOUT_COMPLETED.equals(event.getType())) {
            LOGGER.info("Professional Stripe webhook ignored eventId={} eventType={}", event.getId(), event.getType());
            return Optional.empty();
        }
        try {
            // The signature authenticates the raw event. getObject() refuses to decode events whose
            // API version differs from stripe-java's pinned version, even when these fields agree.
            JsonNode session = JSON.readTree(event.getDataObjectDeserializer().getRawJson());
            if (!"checkout.session".equals(field(session, "object"))) {
                throw new IllegalArgumentException("WRONG_OBJECT");
            }
            String sessionId = required(field(session, "id"), "checkout session id");
            String paymentStatus = field(session, "payment_status");
            String mode = field(session, "mode");
            if (!"paid".equals(paymentStatus)) throw new IllegalArgumentException("NOT_PAID");
            if (!"payment".equals(mode)) throw new IllegalArgumentException("WRONG_MODE");
            JsonNode metadata = session.get("metadata");
            UUID entitlementId = UUID.fromString(field(metadata, "entitlement_id"));
            UUID assessmentId = UUID.fromString(field(metadata, "assessment_id"));
            if (!entitlementId.toString().equals(field(session, "client_reference_id"))) {
                throw new IllegalArgumentException("WRONG_CLIENT_REFERENCE");
            }
            long amountTotal = session.get("amount_total").longValue();
            String currency = field(session, "currency");
            if (amountTotal <= 0 || currency.isBlank()) throw new IllegalArgumentException("INVALID_AMOUNT_OR_CURRENCY");
            String eventId = required(event.getId(), "event id");
            StripeVerifiedEvent verified = new StripeVerifiedEvent(eventId, sessionId, entitlementId,
                    assessmentId, Optional.ofNullable(session.get("payment_intent"))
                            .filter(value -> !value.isNull()).map(JsonNode::asText),
                    Instant.ofEpochSecond(event.getCreated()), amountTotal, currency);
            LOGGER.info("Professional Stripe checkout verified eventId={} eventType={} assessmentId={} "
                            + "entitlementId={} sessionId={} paymentStatus={} mode={} apiVersion={}",
                    eventId, event.getType(), assessmentId, entitlementId, sessionId,
                    paymentStatus, mode, event.getApiVersion());
            return Optional.of(verified);
        }
        catch (JsonProcessingException | RuntimeException malformed) {
            LOGGER.warn("Professional Stripe checkout rejected eventId={} eventType={} reason={}",
                    event.getId(), event.getType(), reason(malformed));
            throw new InvalidPaymentNotificationException("Stripe checkout metadata is invalid", malformed);
        }
    }

    private static String field(JsonNode object, String name) {
        if (object == null || !object.has(name) || object.get(name).isNull()) {
            throw new IllegalArgumentException("MISSING_" + name.toUpperCase());
        }
        return object.get(name).asText();
    }

    private static String reason(Exception malformed) {
        String message = malformed.getMessage();
        return message != null && message.matches("[A-Z_]+") ? message : "MALFORMED_SESSION";
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
