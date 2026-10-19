package com.chikere.futureai.payment.professional.fixture;

import com.chikere.futureai.payment.professional.application.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

public final class LocalFixtureProfessionalPaymentGateway
        implements ProfessionalPaymentGateway, LocalProfessionalPaymentSimulator {
    public static final String PRICE_KEY = "local-fixture-professional-paid-report";
    private static final String SIGNATURE_PREFIX = "local-fixture:";
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LocalFixtureProfessionalPaymentGateway(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ProfessionalCheckoutSession createCheckoutSession(ProfessionalCheckoutRequest request) {
        String sessionId = "local-checkout-" + request.entitlementId();
        URI result = request.successUrl();
        URI localUrl = URI.create(result.getScheme() + "://" + result.getAuthority()
                + "/professional-assessment/" + request.assessmentId()
                + "/checkout/local?session=" + sessionId);
        return new ProfessionalCheckoutSession(sessionId, localUrl, provider(),
                Optional.of(Instant.now(clock).plus(30, ChronoUnit.MINUTES)));
    }

    /** A local session never expires or completes on its own; the simulator completes it. */
    @Override
    public ProfessionalCheckoutSessionState checkoutSessionState(String checkoutSessionId) {
        return ProfessionalCheckoutSessionState.OPEN;
    }

    @Override
    public ProfessionalPaymentNotification successfulNotification(ProfessionalReportEntitlement entitlement) {
        if (entitlement.paymentProvider() != provider()
                || entitlement.status() != ProfessionalEntitlementStatus.PENDING) {
            throw new InvalidPaymentNotificationException("local entitlement is not pending");
        }
        var payload = new LocalEvent("local-event-" + entitlement.providerCheckoutSessionId(),
                entitlement.providerCheckoutSessionId(), entitlement.entitlementId(), entitlement.assessmentId(),
                "local-payment-" + entitlement.entitlementId(), Instant.now(clock));
        try {
            String json = objectMapper.writeValueAsString(payload);
            return new ProfessionalPaymentNotification(json, signature(json));
        }
        catch (JsonProcessingException failure) {
            throw new IllegalStateException("could not create local payment event", failure);
        }
    }

    @Override
    public Optional<ProfessionalPaymentEvent> verifyPaymentNotification(ProfessionalPaymentNotification notification) {
        if (!signature(notification.payload()).equals(notification.signature())) {
            throw new InvalidPaymentNotificationException("local payment notification signature is invalid");
        }
        try {
            LocalEvent event = objectMapper.readValue(notification.payload(), LocalEvent.class);
            return Optional.of(new ProfessionalPaymentEvent(event.eventId(), provider(), event.sessionId(),
                    event.entitlementId(), event.assessmentId(), Optional.of(event.paymentReference()),
                    event.occurredAt()));
        }
        catch (JsonProcessingException failure) {
            throw new InvalidPaymentNotificationException("local payment notification payload is invalid", failure);
        }
    }

    @Override public ProfessionalPaymentProvider provider() { return ProfessionalPaymentProvider.LOCAL_FIXTURE; }
    @Override public String priceConfigurationKey() { return PRICE_KEY; }

    private String signature(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((SIGNATURE_PREFIX + payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        }
        catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record LocalEvent(String eventId, String sessionId, UUID entitlementId, UUID assessmentId,
                              String paymentReference, Instant occurredAt) { }
}
