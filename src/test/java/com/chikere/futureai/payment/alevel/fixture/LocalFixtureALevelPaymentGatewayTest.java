package com.chikere.futureai.payment.alevel.fixture;

import com.chikere.futureai.payment.alevel.application.ALevelCheckoutRequest;
import com.chikere.futureai.payment.alevel.application.ALevelEntitlementStatus;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentNotification;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentProvider;
import com.chikere.futureai.payment.alevel.application.ALevelReportEntitlement;
import com.chikere.futureai.payment.alevel.application.InvalidPaymentNotificationException;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFixtureALevelPaymentGatewayTest {

    private static final UUID ASSESSMENT = UUID.fromString("0d1e2f3a-4b5c-4d6e-8f7a-8b9c0d1e2f3a");
    private static final UUID ENTITLEMENT = UUID.fromString("1e2f3a4b-5c6d-4e7f-8a8b-9c0d1e2f3a4b");

    private final LocalFixtureALevelPaymentGateway gateway = new LocalFixtureALevelPaymentGateway(
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void makesZeroNetworkCallsAndNeverClaimsStripeProvenance() {
        var session = gateway.createCheckoutSession(new ALevelCheckoutRequest(ENTITLEMENT, ASSESSMENT,
                ALevelReportTier.PAID, "ignored",
                URI.create("http://localhost:8080/a-level-assessment/" + ASSESSMENT + "?checkout=returned"),
                URI.create("http://localhost:8080/a-level-assessment/" + ASSESSMENT + "?checkout=cancelled")));

        assertThat(gateway.provider()).isEqualTo(ALevelPaymentProvider.LOCAL_FIXTURE);
        assertThat(gateway.priceConfigurationKey()).isEqualTo("local-fixture-a-level-paid-report");
        assertThat(session.provider()).isEqualTo(ALevelPaymentProvider.LOCAL_FIXTURE);
        assertThat(session.checkoutSessionId()).isEqualTo("local-a-level-checkout-" + ENTITLEMENT);
        assertThat(session.redirectUrl().getHost()).isEqualTo("localhost");
    }

    @Test
    void confirmationRequiresAPendingLocalEntitlementAndAValidLocalSignature() {
        var pending = entitlement(ALevelEntitlementStatus.PENDING);

        ALevelPaymentNotification notification = gateway.successfulNotification(pending);
        var event = gateway.verifyPaymentNotification(notification).orElseThrow();

        assertThat(event.provider()).isEqualTo(ALevelPaymentProvider.LOCAL_FIXTURE);
        assertThat(event.entitlementId()).isEqualTo(ENTITLEMENT);
        assertThat(event.assessmentId()).isEqualTo(ASSESSMENT);
        assertThat(event.checkoutSessionId()).isEqualTo("local-a-level-session");

        assertThatThrownBy(() -> gateway.verifyPaymentNotification(
                new ALevelPaymentNotification(notification.payload(), "tampered")))
                .isInstanceOf(InvalidPaymentNotificationException.class);
        assertThatThrownBy(() -> gateway.successfulNotification(
                entitlement(ALevelEntitlementStatus.ACTIVE)))
                .isInstanceOf(InvalidPaymentNotificationException.class);
    }

    @Test
    void itsSignatureIsDistinctFromEveryOtherJourneysLocalGateway() {
        var notification = gateway.successfulNotification(entitlement(ALevelEntitlementStatus.PENDING));
        Clock clock = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);
        var mapper = new ObjectMapper().findAndRegisterModules();
        var professional = new com.chikere.futureai.payment.professional.fixture
                .LocalFixtureProfessionalPaymentGateway(mapper, clock);
        var university = new com.chikere.futureai.payment.university.fixture
                .LocalFixtureUniversityPaymentGateway(mapper, clock);

        // An A-level payload signed for A-level must not verify for any other product.
        assertThatThrownBy(() -> professional.verifyPaymentNotification(
                new com.chikere.futureai.payment.professional.application.ProfessionalPaymentNotification(
                        notification.payload(), notification.signature())))
                .isInstanceOf(com.chikere.futureai.payment.professional.application
                        .InvalidPaymentNotificationException.class);
        assertThatThrownBy(() -> university.verifyPaymentNotification(
                new com.chikere.futureai.payment.university.application.UniversityPaymentNotification(
                        notification.payload(), notification.signature())))
                .isInstanceOf(com.chikere.futureai.payment.university.application
                        .InvalidPaymentNotificationException.class);
    }

    private ALevelReportEntitlement entitlement(ALevelEntitlementStatus status) {
        return new ALevelReportEntitlement(ENTITLEMENT, ASSESSMENT, ALevelReportTier.PAID, status,
                ALevelPaymentProvider.LOCAL_FIXTURE, "local-a-level-session",
                URI.create("http://localhost:8080/a-level-assessment/" + ASSESSMENT + "/checkout/local"),
                "local-fixture-a-level-paid-report", Optional.empty(),
                status == ALevelEntitlementStatus.ACTIVE
                        ? Optional.of("a-level-report-v1") : Optional.empty(),
                Instant.parse("2026-10-01T11:59:00Z"),
                status == ALevelEntitlementStatus.ACTIVE
                        ? Optional.of(Instant.parse("2026-10-01T12:00:00Z")) : Optional.empty());
    }
}
