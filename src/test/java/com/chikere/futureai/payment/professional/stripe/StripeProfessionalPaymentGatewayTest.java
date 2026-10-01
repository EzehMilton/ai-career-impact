package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class StripeProfessionalPaymentGatewayTest {
    @Test
    void mapsVendorNeutralCheckoutInOneStripeOperation() {
        var operations = mock(StripeCheckoutOperations.class);
        var verifier = mock(StripeWebhookEventVerifier.class);
        var gateway = new StripeProfessionalPaymentGateway(operations, verifier, "price_configured");
        var request = new ProfessionalCheckoutRequest(UUID.randomUUID(), UUID.randomUUID(),
                ProfessionalReportTier.PAID, "price_configured", URI.create("https://example.test/success"),
                URI.create("https://example.test/cancel"));
        when(operations.create(request)).thenReturn(new StripeCheckoutData("cs_test_1",
                URI.create("https://checkout.stripe.com/c/pay/test"), Optional.empty()));

        assertThat(gateway.createCheckoutSession(request)).isEqualTo(new ProfessionalCheckoutSession("cs_test_1",
                URI.create("https://checkout.stripe.com/c/pay/test"), ProfessionalPaymentProvider.STRIPE,
                Optional.empty()));
        verify(operations).create(request);
        verifyNoInteractions(verifier);
    }

    @Test
    void verifiedStripeEventIsMappedWithoutFixtureFallback() {
        var operations = mock(StripeCheckoutOperations.class);
        var verifier = mock(StripeWebhookEventVerifier.class);
        var gateway = new StripeProfessionalPaymentGateway(operations, verifier, "price_configured");
        var notification = new ProfessionalPaymentNotification("payload", "signature");
        var verified = new StripeVerifiedEvent("evt_1", "cs_1", UUID.randomUUID(), UUID.randomUUID(),
                Optional.of("pi_1"), Instant.EPOCH);
        when(verifier.verify(notification)).thenReturn(Optional.of(verified));

        assertThat(gateway.verifyPaymentNotification(notification)).contains(new ProfessionalPaymentEvent(
                "evt_1", ProfessionalPaymentProvider.STRIPE, "cs_1", verified.entitlementId(),
                verified.assessmentId(), Optional.of("pi_1"), Instant.EPOCH));
        verifyNoInteractions(operations);
    }
}
