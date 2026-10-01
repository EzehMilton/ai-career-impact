package com.chikere.futureai.payment.university.fixture;

import com.chikere.futureai.payment.university.application.InvalidPaymentNotificationException;
import com.chikere.futureai.payment.university.application.UniversityCheckoutRequest;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityPaymentNotification;
import com.chikere.futureai.payment.university.application.UniversityPaymentProvider;
import com.chikere.futureai.payment.university.application.UniversityReportEntitlement;
import com.chikere.futureai.report.university.application.UniversityReportTier;
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

class LocalFixtureUniversityPaymentGatewayTest {

    private static final UUID ASSESSMENT = UUID.fromString("4c5d6e7f-8a9b-4c0d-8e1f-2a3b4c5d6e7f");
    private static final UUID ENTITLEMENT = UUID.fromString("5d6e7f8a-9b0c-4d1e-8f2a-3b4c5d6e7f8a");

    private final LocalFixtureUniversityPaymentGateway gateway = new LocalFixtureUniversityPaymentGateway(
            new ObjectMapper().findAndRegisterModules(),
            Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC));

    @Test
    void makesZeroNetworkCallsAndNeverClaimsStripeProvenance() {
        var session = gateway.createCheckoutSession(new UniversityCheckoutRequest(ENTITLEMENT, ASSESSMENT,
                UniversityReportTier.PAID, "ignored",
                URI.create("http://localhost:8080/university-assessment/" + ASSESSMENT + "?checkout=returned"),
                URI.create("http://localhost:8080/university-assessment/" + ASSESSMENT + "?checkout=cancelled")));

        assertThat(gateway.provider()).isEqualTo(UniversityPaymentProvider.LOCAL_FIXTURE);
        assertThat(gateway.priceConfigurationKey()).isEqualTo("local-fixture-university-paid-report");
        assertThat(session.provider()).isEqualTo(UniversityPaymentProvider.LOCAL_FIXTURE);
        assertThat(session.checkoutSessionId()).isEqualTo("local-university-checkout-" + ENTITLEMENT);
        assertThat(session.redirectUrl().toString())
                .isEqualTo("http://localhost:8080/university-assessment/" + ASSESSMENT
                        + "/checkout/local?session=local-university-checkout-" + ENTITLEMENT);
        assertThat(session.redirectUrl().getHost()).isEqualTo("localhost");
    }

    @Test
    void confirmationRequiresAPendingLocalEntitlementAndAValidLocalSignature() {
        var pending = entitlement(UniversityEntitlementStatus.PENDING);

        UniversityPaymentNotification notification = gateway.successfulNotification(pending);
        var event = gateway.verifyPaymentNotification(notification).orElseThrow();

        assertThat(event.provider()).isEqualTo(UniversityPaymentProvider.LOCAL_FIXTURE);
        assertThat(event.entitlementId()).isEqualTo(ENTITLEMENT);
        assertThat(event.assessmentId()).isEqualTo(ASSESSMENT);
        assertThat(event.checkoutSessionId()).isEqualTo("local-university-session");

        assertThatThrownBy(() -> gateway.verifyPaymentNotification(
                new UniversityPaymentNotification(notification.payload(), "tampered")))
                .isInstanceOf(InvalidPaymentNotificationException.class);
        assertThatThrownBy(() -> gateway.successfulNotification(
                entitlement(UniversityEntitlementStatus.ACTIVE)))
                .isInstanceOf(InvalidPaymentNotificationException.class);
    }

    @Test
    void itsSignatureIsDistinctFromAnyOtherProductsLocalGateway() {
        var notification = gateway.successfulNotification(entitlement(UniversityEntitlementStatus.PENDING));
        var professionalGateway = new com.chikere.futureai.payment.professional.fixture
                .LocalFixtureProfessionalPaymentGateway(new ObjectMapper().findAndRegisterModules(),
                Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC));

        // A university payload signed for university must not verify as a professional payment.
        assertThatThrownBy(() -> professionalGateway.verifyPaymentNotification(
                new com.chikere.futureai.payment.professional.application.ProfessionalPaymentNotification(
                        notification.payload(), notification.signature())))
                .isInstanceOf(com.chikere.futureai.payment.professional.application
                        .InvalidPaymentNotificationException.class);
    }

    private UniversityReportEntitlement entitlement(UniversityEntitlementStatus status) {
        return new UniversityReportEntitlement(ENTITLEMENT, ASSESSMENT, UniversityReportTier.PAID, status,
                UniversityPaymentProvider.LOCAL_FIXTURE, "local-university-session",
                URI.create("http://localhost:8080/university-assessment/" + ASSESSMENT + "/checkout/local"),
                "local-fixture-university-paid-report", Optional.empty(),
                status == UniversityEntitlementStatus.ACTIVE
                        ? Optional.of("university-report-v1") : Optional.empty(),
                Instant.parse("2026-10-01T11:59:00Z"),
                status == UniversityEntitlementStatus.ACTIVE
                        ? Optional.of(Instant.parse("2026-10-01T12:00:00Z")) : Optional.empty());
    }
}
