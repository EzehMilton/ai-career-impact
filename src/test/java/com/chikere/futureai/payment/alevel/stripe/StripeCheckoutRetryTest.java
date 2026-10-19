package com.chikere.futureai.payment.alevel.stripe;

import com.chikere.futureai.payment.alevel.application.ALevelCheckoutRequest;
import com.chikere.futureai.payment.alevel.application.ALevelCheckoutSessionState;
import com.chikere.futureai.payment.alevel.application.PaymentUnavailableException;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** How a retry asks Stripe about a pending session and keys its replacement; no Stripe calls. */
class StripeCheckoutRetryTest {
    private static final UUID ENTITLEMENT = UUID.fromString("0d1e2f3a-4b5c-4d6e-8f7a-8b9c0d1e2f3a");

    @Test
    void aFirstCheckoutKeepsItsOriginalIdempotencyKey() {
        assertThat(StripeSdkCheckoutOperations.idempotencyKey(request(Optional.empty())))
                .isEqualTo("a-level-report-" + ENTITLEMENT);
    }

    @Test
    void aReplacementIsKeyedByTheExpiredSessionSoConcurrentRetriesShareOneNewSession() {
        String first = StripeSdkCheckoutOperations.idempotencyKey(request(Optional.of("cs_expired_1")));
        String again = StripeSdkCheckoutOperations.idempotencyKey(request(Optional.of("cs_expired_1")));
        String later = StripeSdkCheckoutOperations.idempotencyKey(request(Optional.of("cs_expired_2")));

        assertThat(first).isEqualTo(again).isEqualTo("a-level-report-" + ENTITLEMENT + "-replaces-cs_expired_1");
        assertThat(later).isNotEqualTo(first);
        assertThat(first).isNotEqualTo(StripeSdkCheckoutOperations.idempotencyKey(request(Optional.empty())));
    }

    @Test
    void stripeSessionStatusesMapToRetryDecisionsAndAnythingUnknownIsNeverSafeToReplace() {
        assertThat(StripeSdkCheckoutOperations.stateOf("open")).isEqualTo(ALevelCheckoutSessionState.OPEN);
        assertThat(StripeSdkCheckoutOperations.stateOf("complete")).isEqualTo(ALevelCheckoutSessionState.COMPLETE);
        assertThat(StripeSdkCheckoutOperations.stateOf("expired")).isEqualTo(ALevelCheckoutSessionState.EXPIRED);
        for (String unknown : new String[] {null, "", "EXPIRED", "processing"}) {
            assertThatThrownBy(() -> StripeSdkCheckoutOperations.stateOf(unknown))
                    .as("%s", unknown).isInstanceOf(PaymentUnavailableException.class);
        }
    }

    @Test
    void theGatewayAsksStripeAboutTheExactSession() {
        var operations = mock(StripeCheckoutOperations.class);
        when(operations.state("cs_pending")).thenReturn(ALevelCheckoutSessionState.EXPIRED);
        var gateway = new StripeALevelPaymentGateway(operations, mock(StripeWebhookEventVerifier.class), "price_expected");

        assertThat(gateway.checkoutSessionState("cs_pending")).isEqualTo(ALevelCheckoutSessionState.EXPIRED);
    }

    private static ALevelCheckoutRequest request(Optional<String> replaces) {
        return new ALevelCheckoutRequest(ENTITLEMENT, UUID.randomUUID(), ALevelReportTier.PAID, "price_expected",
                URI.create("https://example.test/returned"), URI.create("https://example.test/cancelled"), replaces);
    }
}
