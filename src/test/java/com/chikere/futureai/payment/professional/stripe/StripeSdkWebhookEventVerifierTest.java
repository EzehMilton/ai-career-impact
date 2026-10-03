package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.InvalidPaymentNotificationException;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentNotification;
import com.stripe.Stripe;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class StripeSdkWebhookEventVerifierTest {
    private static final String SECRET = "whsec_test_secret";

    @Test
    void acceptsValidStripeSignatureAndRejectsInvalidSignature() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        UUID entitlementId = UUID.randomUUID(); UUID assessmentId = UUID.randomUUID();
        String payload = payload(timestamp, Stripe.API_VERSION, entitlementId, assessmentId,
                "paid", "payment", entitlementId.toString());
        String signature = "t=" + timestamp + ",v1=" + hmac(timestamp + "." + payload);
        var verifier = new StripeSdkWebhookEventVerifier(SECRET);

        var event = verifier.verify(new ProfessionalPaymentNotification(payload, signature)).orElseThrow();
        assertThat(event.eventId()).isEqualTo("evt_1");
        assertThat(event.sessionId()).isEqualTo("cs_test_1");
        assertThat(event.entitlementId()).isEqualTo(entitlementId);
        assertThat(event.assessmentId()).isEqualTo(assessmentId);
        assertThat(event.amountTotal()).isEqualTo(499);
        assertThat(event.currency()).isEqualTo("gbp");
        assertThatThrownBy(() -> verifier.verify(new ProfessionalPaymentNotification(payload,
                "t=" + timestamp + ",v1=bad"))).isInstanceOf(InvalidPaymentNotificationException.class);
    }

    @Test
    void acceptsSignedCheckoutFromDifferentStripeApiVersion() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        UUID entitlement = UUID.randomUUID(); UUID assessment = UUID.randomUUID();
        String payload = payload(timestamp, "2020-08-27", entitlement, assessment,
                "paid", "payment", entitlement.toString());
        String signature = "t=" + timestamp + ",v1=" + hmac(timestamp + "." + payload);

        var event = new StripeSdkWebhookEventVerifier(SECRET)
                .verify(new ProfessionalPaymentNotification(payload, signature)).orElseThrow();

        assertThat(event.sessionId()).isEqualTo("cs_test_1");
        assertThat(event.entitlementId()).isEqualTo(entitlement);
    }

    @Test
    void rejectsUnpaidWrongModeAndWrongReferenceEvenWhenSigned() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        UUID entitlement = UUID.randomUUID(); UUID assessment = UUID.randomUUID();
        var verifier = new StripeSdkWebhookEventVerifier(SECRET);
        for (String payload : new String[] {
                payload(timestamp, "2020-08-27", entitlement, assessment, "unpaid", "payment", entitlement.toString()),
                payload(timestamp, "2020-08-27", entitlement, assessment, "paid", "subscription", entitlement.toString()),
                payload(timestamp, "2020-08-27", entitlement, assessment, "paid", "payment", UUID.randomUUID().toString())}) {
            String signature = "t=" + timestamp + ",v1=" + hmac(timestamp + "." + payload);
            assertThatThrownBy(() -> verifier.verify(new ProfessionalPaymentNotification(payload, signature)))
                    .isInstanceOf(InvalidPaymentNotificationException.class);
        }
    }

    private String payload(long timestamp, String apiVersion, UUID entitlement, UUID assessment,
            String paymentStatus, String mode, String clientReference) {
        return "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"" + apiVersion
                + "\",\"created\":" + timestamp
                + ",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                + "\"id\":\"cs_test_1\",\"object\":\"checkout.session\",\"payment_status\":\""
                + paymentStatus + "\",\"mode\":\"" + mode + "\",\"client_reference_id\":\""
                + clientReference + "\",\"amount_total\":499,\"currency\":\"gbp\","
                + "\"payment_intent\":\"pi_1\",\"metadata\":{\"assessment_id\":\"" + assessment
                + "\",\"entitlement_id\":\"" + entitlement + "\"}}}}";
    }

    private String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
