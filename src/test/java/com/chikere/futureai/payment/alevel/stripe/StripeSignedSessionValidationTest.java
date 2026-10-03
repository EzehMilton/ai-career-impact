package com.chikere.futureai.payment.alevel.stripe;

import com.chikere.futureai.payment.alevel.application.InvalidPaymentNotificationException;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentNotification;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeSignedSessionValidationTest {
    private static final String SECRET = "whsec_test_only";
    private final StripeSdkWebhookEventVerifier verifier = new StripeSdkWebhookEventVerifier(SECRET);
    private final long timestamp = Instant.now().getEpochSecond();
    private final UUID entitlement = UUID.randomUUID();
    private final UUID assessment = UUID.randomUUID();

    @Test
    void acceptsSignedPaidSessionAcrossApiVersions() throws Exception {
        var event = verifier.verify(notification(payload("2020-08-27", "a_level_report",
                "paid", "payment", entitlement.toString()))).orElseThrow();
        assertThat(event.eventId()).isEqualTo("evt_1");
        assertThat(event.entitlementId()).isEqualTo(entitlement);
        assertThat(event.assessmentId()).isEqualTo(assessment);
        assertThat(event.amountTotal()).isEqualTo(499);
        assertThat(event.currency()).isEqualTo("gbp");
    }

    @Test
    void rejectsUnsignedUnpaidWrongModeAndWrongReference() throws Exception {
        String valid = payload("2020-08-27", "a_level_report", "paid", "payment", entitlement.toString());
        assertThatThrownBy(() -> verifier.verify(new ALevelPaymentNotification(valid, "t=1,v1=bad")))
                .isInstanceOf(InvalidPaymentNotificationException.class);
        for (String body : new String[] {
                payload("2020-08-27", "a_level_report", "unpaid", "payment", entitlement.toString()),
                payload("2020-08-27", "a_level_report", "paid", "subscription", entitlement.toString()),
                payload("2020-08-27", "a_level_report", "paid", "payment", UUID.randomUUID().toString())}) {
            assertThatThrownBy(() -> verifier.verify(notification(body)))
                    .isInstanceOf(InvalidPaymentNotificationException.class);
        }
    }

    @Test
    void ignoresOtherProductWithoutActivatingAnything() throws Exception {
        assertThat(verifier.verify(notification(payload("2020-08-27", "other_product",
                "paid", "payment", entitlement.toString())))).isEmpty();
    }

    private ALevelPaymentNotification notification(String payload) throws Exception {
        String signature = "t=" + timestamp + ",v1=" + hmac(timestamp + "." + payload);
        return new ALevelPaymentNotification(payload, signature);
    }

    private String payload(String apiVersion, String product, String paymentStatus,
            String mode, String reference) {
        return "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"" + apiVersion
                + "\",\"created\":" + timestamp
                + ",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                + "\"id\":\"cs_test_1\",\"object\":\"checkout.session\",\"payment_status\":\""
                + paymentStatus + "\",\"mode\":\"" + mode + "\",\"client_reference_id\":\""
                + reference + "\",\"amount_total\":499,\"currency\":\"gbp\","
                + "\"payment_intent\":\"pi_1\",\"metadata\":{\"product\":\"" + product
                + "\",\"assessment_id\":\"" + assessment
                + "\",\"entitlement_id\":\"" + entitlement + "\"}}}}";
    }

    private static String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
