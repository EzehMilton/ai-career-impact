package com.chikere.futureai.payment.university.fixture;

import com.chikere.futureai.payment.university.application.*;
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
public final class LocalFixtureUniversityPaymentGateway
        implements UniversityPaymentGateway, LocalUniversityPaymentSimulator {

    public static final String PRICE_KEY = "local-fixture-university-paid-report";
    private static final String SIGNATURE_PREFIX = "local-fixture-university:";
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public LocalFixtureUniversityPaymentGateway(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public UniversityCheckoutSession createCheckoutSession(UniversityCheckoutRequest request) {
        String sessionId = "local-university-checkout-" + request.entitlementId();
        URI result = request.successUrl();
        URI localUrl = URI.create(result.getScheme() + "://" + result.getAuthority()
                + "/university-assessment/" + request.assessmentId()
                + "/checkout/local?session=" + sessionId);
        return new UniversityCheckoutSession(sessionId, localUrl, provider(),
                Optional.of(Instant.now(clock).plus(30, ChronoUnit.MINUTES)));
    }

    @Override
    public UniversityPaymentNotification successfulNotification(UniversityReportEntitlement entitlement) {
        if (entitlement.paymentProvider() != provider()
                || entitlement.status() != UniversityEntitlementStatus.PENDING) {
            throw new InvalidPaymentNotificationException("local entitlement is not pending");
        }
        var payload = new LocalEvent("local-university-event-" + entitlement.providerCheckoutSessionId(),
                entitlement.providerCheckoutSessionId(), entitlement.entitlementId(),
                entitlement.assessmentId(), "local-university-payment-" + entitlement.entitlementId(),
                Instant.now(clock));
        try {
            String json = objectMapper.writeValueAsString(payload);
            return new UniversityPaymentNotification(json, signature(json));
        }
        catch (JsonProcessingException failure) {
            throw new IllegalStateException("could not create local payment event", failure);
        }
    }

    @Override
    public Optional<UniversityPaymentEvent> verifyPaymentNotification(
            UniversityPaymentNotification notification) {
        if (!signature(notification.payload()).equals(notification.signature())) {
            throw new InvalidPaymentNotificationException("local payment notification signature is invalid");
        }
        try {
            LocalEvent event = objectMapper.readValue(notification.payload(), LocalEvent.class);
            return Optional.of(new UniversityPaymentEvent(event.eventId(), provider(), event.sessionId(),
                    event.entitlementId(), event.assessmentId(), Optional.of(event.paymentReference()),
                    event.occurredAt()));
        }
        catch (JsonProcessingException failure) {
            throw new InvalidPaymentNotificationException("local payment notification payload is invalid", failure);
        }
    }

    @Override
    public UniversityPaymentProvider provider() {
        return UniversityPaymentProvider.LOCAL_FIXTURE;
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
