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
        String payload = "{\"id\":\"evt_1\",\"object\":\"event\",\"api_version\":\"" + Stripe.API_VERSION
                + "\",\"created\":" + timestamp
                + ",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                + "\"id\":\"cs_test_1\",\"object\":\"checkout.session\",\"payment_status\":\"paid\","
                + "\"payment_intent\":\"pi_1\",\"metadata\":{\"assessment_id\":\"" + assessmentId
                + "\",\"entitlement_id\":\"" + entitlementId + "\"}}}}";
        String signature = "t=" + timestamp + ",v1=" + hmac(timestamp + "." + payload);
        var verifier = new StripeSdkWebhookEventVerifier(SECRET);

        var event = verifier.verify(new ProfessionalPaymentNotification(payload, signature)).orElseThrow();
        assertThat(event.eventId()).isEqualTo("evt_1");
        assertThat(event.sessionId()).isEqualTo("cs_test_1");
        assertThat(event.entitlementId()).isEqualTo(entitlementId);
        assertThat(event.assessmentId()).isEqualTo(assessmentId);
        assertThatThrownBy(() -> verifier.verify(new ProfessionalPaymentNotification(payload,
                "t=" + timestamp + ",v1=bad"))).isInstanceOf(InvalidPaymentNotificationException.class);
    }

    private String hmac(String value) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    }
}
