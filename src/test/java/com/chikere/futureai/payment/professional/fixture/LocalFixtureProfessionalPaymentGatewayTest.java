package com.chikere.futureai.payment.professional.fixture;

import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class LocalFixtureProfessionalPaymentGatewayTest {
    @Test
    void deterministicCheckoutAndExplicitConfirmationRequireNoNetwork() {
        Instant now = Instant.parse("2026-09-30T12:00:00Z");
        var mapper = new ObjectMapper().registerModules(new Jdk8Module(), new JavaTimeModule());
        var gateway = new LocalFixtureProfessionalPaymentGateway(mapper, Clock.fixed(now, ZoneOffset.UTC));
        UUID entitlementId = UUID.randomUUID(); UUID assessmentId = UUID.randomUUID();
        var request = new ProfessionalCheckoutRequest(entitlementId, assessmentId, ProfessionalReportTier.PAID,
                gateway.priceConfigurationKey(), URI.create("http://localhost:8080/success"),
                URI.create("http://localhost:8080/cancel"));

        assertThat(gateway.createCheckoutSession(request)).isEqualTo(gateway.createCheckoutSession(request));
        var session = gateway.createCheckoutSession(request);
        var entitlement = new ProfessionalReportEntitlement(entitlementId, assessmentId, ProfessionalReportTier.PAID,
                ProfessionalEntitlementStatus.PENDING, ProfessionalPaymentProvider.LOCAL_FIXTURE,
                session.checkoutSessionId(), session.redirectUrl(), gateway.priceConfigurationKey(), Optional.empty(),
                Optional.empty(), now, Optional.empty());
        var notification = gateway.successfulNotification(entitlement);
        var event = gateway.verifyPaymentNotification(notification).orElseThrow();
        assertThat(event.provider()).isEqualTo(ProfessionalPaymentProvider.LOCAL_FIXTURE);
        assertThat(event.entitlementId()).isEqualTo(entitlementId);
        assertThat(event.assessmentId()).isEqualTo(assessmentId);
        assertThat(event.eventId()).startsWith("local-event-");
        assertThatThrownBy(() -> gateway.verifyPaymentNotification(
                new ProfessionalPaymentNotification(notification.payload(), "invalid")))
                .isInstanceOf(InvalidPaymentNotificationException.class);
    }
}
