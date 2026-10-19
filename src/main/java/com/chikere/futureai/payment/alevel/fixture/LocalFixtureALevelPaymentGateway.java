package com.chikere.futureai.payment.alevel.fixture;

import com.chikere.futureai.payment.alevel.application.*;
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

/** Zero-network development gateway. It never represents itself as Stripe provenance. */
public final class LocalFixtureALevelPaymentGateway
        implements ALevelPaymentGateway, LocalALevelPaymentSimulator {

    public static final String PRICE_KEY = "local-fixture-a-level-paid-report";
    private static final String SIGNATURE_PREFIX = "local-fixture-a-level:";
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LocalFixtureALevelPaymentGateway(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public ALevelCheckoutSession createCheckoutSession(ALevelCheckoutRequest request) {
        String sessionId = "local-a-level-checkout-" + request.entitlementId();
        URI result = request.successUrl();
        URI localUrl = URI.create(result.getScheme() + "://" + result.getAuthority()
                + "/a-level-assessment/" + request.assessmentId() + "/checkout/local?session=" + sessionId);
        return new ALevelCheckoutSession(sessionId, localUrl, provider(),
                Optional.of(Instant.now(clock).plus(30, ChronoUnit.MINUTES)));
    }

    /** A local session never expires or completes on its own; the simulator completes it. */
    @Override
    public ALevelCheckoutSessionState checkoutSessionState(String checkoutSessionId) {
        return ALevelCheckoutSessionState.OPEN;
    }

    @Override
    public ALevelPaymentNotification successfulNotification(ALevelReportEntitlement entitlement) {
        if (entitlement.paymentProvider() != provider()
                || entitlement.status() != ALevelEntitlementStatus.PENDING) {
            throw new InvalidPaymentNotificationException("local entitlement is not pending");
        }
        var payload = new LocalEvent("local-a-level-event-" + entitlement.providerCheckoutSessionId(),
                entitlement.providerCheckoutSessionId(), entitlement.entitlementId(),
                entitlement.assessmentId(), "local-a-level-payment-" + entitlement.entitlementId(),
                Instant.now(clock));
        try {
            String json = objectMapper.writeValueAsString(payload);
            return new ALevelPaymentNotification(json, signature(json));
        }
        catch (JsonProcessingException failure) {
            throw new IllegalStateException("could not create local payment event", failure);
        }
    }

    @Override
    public Optional<ALevelPaymentEvent> verifyPaymentNotification(ALevelPaymentNotification notification) {
        if (!signature(notification.payload()).equals(notification.signature())) {
            throw new InvalidPaymentNotificationException("local payment notification signature is invalid");
        }
        try {
            LocalEvent event = objectMapper.readValue(notification.payload(), LocalEvent.class);
            return Optional.of(new ALevelPaymentEvent(event.eventId(), provider(), event.sessionId(),
                    event.entitlementId(), event.assessmentId(), Optional.of(event.paymentReference()),
                    event.occurredAt()));
        }
        catch (JsonProcessingException failure) {
            throw new InvalidPaymentNotificationException("local payment notification payload is invalid", failure);
        }
    }

    @Override
    public ALevelPaymentProvider provider() {
        return ALevelPaymentProvider.LOCAL_FIXTURE;
    }

    @Override
    public String priceConfigurationKey() {
        return PRICE_KEY;
    }

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
