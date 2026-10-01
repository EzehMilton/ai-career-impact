package com.chikere.futureai.payment.university.stripe;

import com.chikere.futureai.payment.university.application.InvalidPaymentNotificationException;
import com.chikere.futureai.payment.university.application.UniversityPaymentNotification;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeSdkUniversityWebhookEventVerifierTest {

    @Test
    void requiresAConfiguredWebhookSecret() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new StripeSdkWebhookEventVerifier(" "))
                .withMessageContaining("Stripe webhook secret");
    }

    @Test
    void rejectsAnUnsignedOrTamperedPayloadWithoutExposingProviderDetail() {
        var verifier = new StripeSdkWebhookEventVerifier("whsec_not_called");

        assertThatThrownBy(() -> verifier.verify(
                new UniversityPaymentNotification("{\"id\":\"evt_1\"}", "t=1,v1=deadbeef")))
                .isInstanceOf(InvalidPaymentNotificationException.class)
                .hasMessage("Stripe webhook signature or payload is invalid");
    }
}
