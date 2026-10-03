package com.chikere.futureai.payment.university.stripe;

import com.chikere.futureai.payment.university.application.UniversityCheckoutRequest;
import com.chikere.futureai.payment.university.application.UniversityCheckoutSession;
import com.chikere.futureai.payment.university.application.UniversityPaymentEvent;
import com.chikere.futureai.payment.university.application.UniversityPaymentNotification;
import com.chikere.futureai.payment.university.application.UniversityPaymentProvider;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StripeUniversityPaymentGatewayTest {

    @Test
    void mapsVendorNeutralCheckoutInOneStripeOperationWithoutCallingStripe() {
        var operations = mock(StripeCheckoutOperations.class);
        var verifier = mock(StripeWebhookEventVerifier.class);
        var gateway = new StripeUniversityPaymentGateway(operations, verifier, "price_university");
        var request = new UniversityCheckoutRequest(UUID.randomUUID(), UUID.randomUUID(),
                UniversityReportTier.PAID, "price_university", URI.create("https://example.test/success"),
                URI.create("https://example.test/cancel"));
        when(operations.create(request)).thenReturn(new StripeCheckoutData("cs_test_1",
                URI.create("https://checkout.stripe.com/c/pay/test"), Optional.empty()));

        assertThat(gateway.createCheckoutSession(request)).isEqualTo(new UniversityCheckoutSession("cs_test_1",
                URI.create("https://checkout.stripe.com/c/pay/test"), UniversityPaymentProvider.STRIPE,
                Optional.empty()));
        assertThat(gateway.provider()).isEqualTo(UniversityPaymentProvider.STRIPE);
        assertThat(gateway.priceConfigurationKey()).isEqualTo("price_university");
        verify(operations).create(request);
        verifyNoInteractions(verifier);
    }

    @Test
    void usesItsOwnConfiguredPriceAndNeverAHardCodedOne() {
        var operations = mock(StripeCheckoutOperations.class);
        var verifier = mock(StripeWebhookEventVerifier.class);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StripeUniversityPaymentGateway(operations, verifier, " "))
                .withMessageContaining("Stripe university price ID");
        assertThat(new StripeUniversityPaymentGateway(operations, verifier, "price_from_configuration")
                .priceConfigurationKey()).isEqualTo("price_from_configuration");
    }

    @Test
    void verifiedStripeEventIsMappedWithoutAnyFixtureFallback() {
        var operations = mock(StripeCheckoutOperations.class);
        var verifier = mock(StripeWebhookEventVerifier.class);
        var gateway = new StripeUniversityPaymentGateway(operations, verifier, "price_university");
        var notification = new UniversityPaymentNotification("payload", "signature");
        var verified = new StripeVerifiedEvent("evt_1", "cs_1", UUID.randomUUID(), UUID.randomUUID(),
                Optional.of("pi_1"), Instant.EPOCH, 499, "gbp");
        when(verifier.verify(notification)).thenReturn(Optional.of(verified));

        assertThat(gateway.verifyPaymentNotification(notification)).contains(new UniversityPaymentEvent(
                "evt_1", UniversityPaymentProvider.STRIPE, "cs_1", verified.entitlementId(),
                verified.assessmentId(), Optional.of("pi_1"), Instant.EPOCH));
        verify(operations).validateCompletedSession(verified, "price_university");

        when(verifier.verify(notification)).thenReturn(Optional.empty());
        assertThat(gateway.verifyPaymentNotification(notification)).isEmpty();
    }

    @Test
    void universityCheckoutIsTaggedWithItsOwnProductMetadata() {
        assertThat(UniversityStripeMetadata.PRODUCT_KEY).isEqualTo("product");
        assertThat(UniversityStripeMetadata.PRODUCT_VALUE).isEqualTo("university_report");
        assertThat(UniversityStripeMetadata.ASSESSMENT_ID).isEqualTo("assessment_id");
        assertThat(UniversityStripeMetadata.ENTITLEMENT_ID).isEqualTo("entitlement_id");
    }
}
