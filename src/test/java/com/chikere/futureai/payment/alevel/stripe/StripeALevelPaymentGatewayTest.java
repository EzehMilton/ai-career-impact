package com.chikere.futureai.payment.alevel.stripe;

import com.chikere.futureai.payment.alevel.application.ALevelCheckoutRequest;
import com.chikere.futureai.payment.alevel.application.ALevelCheckoutSession;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentEvent;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentNotification;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentProvider;
import com.chikere.futureai.payment.alevel.application.InvalidPaymentNotificationException;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class StripeALevelPaymentGatewayTest {

    @Test
    void mapsVendorNeutralCheckoutInOneStripeOperationWithoutCallingStripe() {
        var operations = mock(StripeCheckoutOperations.class);
        var verifier = mock(StripeWebhookEventVerifier.class);
        var gateway = new StripeALevelPaymentGateway(operations, verifier, "price_a_level");
        var request = new ALevelCheckoutRequest(UUID.randomUUID(), UUID.randomUUID(),
                ALevelReportTier.PAID, "price_a_level", URI.create("https://example.test/success"),
                URI.create("https://example.test/cancel"));
        when(operations.create(request)).thenReturn(new StripeCheckoutData("cs_test_1",
                URI.create("https://checkout.stripe.com/c/pay/test"), Optional.empty()));

        assertThat(gateway.createCheckoutSession(request)).isEqualTo(new ALevelCheckoutSession("cs_test_1",
                URI.create("https://checkout.stripe.com/c/pay/test"), ALevelPaymentProvider.STRIPE,
                Optional.empty()));
        assertThat(gateway.provider()).isEqualTo(ALevelPaymentProvider.STRIPE);
        assertThat(gateway.priceConfigurationKey()).isEqualTo("price_a_level");
        verify(operations).create(request);
        verifyNoInteractions(verifier);
    }

    @Test
    void usesItsOwnConfiguredPriceAndNeverAHardCodedOne() {
        var operations = mock(StripeCheckoutOperations.class);
        var verifier = mock(StripeWebhookEventVerifier.class);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StripeALevelPaymentGateway(operations, verifier, " "))
                .withMessageContaining("Stripe A-level price ID");
        assertThat(new StripeALevelPaymentGateway(operations, verifier, "price_from_configuration")
                .priceConfigurationKey()).isEqualTo("price_from_configuration");
    }

    @Test
    void verifiedStripeEventIsMappedWithoutAnyFixtureFallback() {
        var operations = mock(StripeCheckoutOperations.class);
        var verifier = mock(StripeWebhookEventVerifier.class);
        var gateway = new StripeALevelPaymentGateway(operations, verifier, "price_a_level");
        var notification = new ALevelPaymentNotification("payload", "signature");
        var verified = new StripeVerifiedEvent("evt_1", "cs_1", UUID.randomUUID(), UUID.randomUUID(),
                Optional.of("pi_1"), Instant.EPOCH, 499, "gbp");
        when(verifier.verify(notification)).thenReturn(Optional.of(verified));

        assertThat(gateway.verifyPaymentNotification(notification)).contains(new ALevelPaymentEvent(
                "evt_1", ALevelPaymentProvider.STRIPE, "cs_1", verified.entitlementId(),
                verified.assessmentId(), Optional.of("pi_1"), Instant.EPOCH));
        verify(operations).validateCompletedSession(verified, "price_a_level");

        when(verifier.verify(notification)).thenReturn(Optional.empty());
        assertThat(gateway.verifyPaymentNotification(notification)).isEmpty();
    }

    @Test
    void aLevelCheckoutIsTaggedWithItsOwnProductMetadata() {
        assertThat(ALevelStripeMetadata.PRODUCT_KEY).isEqualTo("product");
        assertThat(ALevelStripeMetadata.PRODUCT_VALUE).isEqualTo("a_level_report");
        assertThat(ALevelStripeMetadata.ASSESSMENT_ID).isEqualTo("assessment_id");
        assertThat(ALevelStripeMetadata.ENTITLEMENT_ID).isEqualTo("entitlement_id");
    }

    @Test
    void requiresAConfiguredWebhookSecretAndRejectsTamperedPayloads() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StripeSdkWebhookEventVerifier(" "))
                .withMessageContaining("Stripe webhook secret");
        var verifier = new StripeSdkWebhookEventVerifier("whsec_not_called");
        assertThatThrownBy(() -> verifier.verify(
                new ALevelPaymentNotification("{\"id\":\"evt_1\"}", "t=1,v1=deadbeef")))
                .isInstanceOf(InvalidPaymentNotificationException.class)
                .hasMessage("Stripe webhook signature or payload is invalid");
    }
}
