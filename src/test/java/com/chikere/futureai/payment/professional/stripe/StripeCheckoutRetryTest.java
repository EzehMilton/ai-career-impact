package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.ProfessionalCheckoutRequest;
import com.chikere.futureai.payment.professional.application.ProfessionalCheckoutSessionState;
import com.chikere.futureai.payment.professional.application.PaymentUnavailableException;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
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
                .isEqualTo("professional-report-" + ENTITLEMENT);
    }

    @Test
    void aReplacementIsKeyedByTheExpiredSessionSoConcurrentRetriesShareOneNewSession() {
        String first = StripeSdkCheckoutOperations.idempotencyKey(request(Optional.of("cs_expired_1")));
        String again = StripeSdkCheckoutOperations.idempotencyKey(request(Optional.of("cs_expired_1")));
        String later = StripeSdkCheckoutOperations.idempotencyKey(request(Optional.of("cs_expired_2")));

        assertThat(first).isEqualTo(again).isEqualTo("professional-report-" + ENTITLEMENT + "-replaces-cs_expired_1");
        assertThat(later).isNotEqualTo(first);
        assertThat(first).isNotEqualTo(StripeSdkCheckoutOperations.idempotencyKey(request(Optional.empty())));
    }

    @Test
    void stripeSessionStatusesMapToRetryDecisionsAndAnythingUnknownIsNeverSafeToReplace() {
        assertThat(StripeSdkCheckoutOperations.stateOf("open")).isEqualTo(ProfessionalCheckoutSessionState.OPEN);
        assertThat(StripeSdkCheckoutOperations.stateOf("complete")).isEqualTo(ProfessionalCheckoutSessionState.COMPLETE);
        assertThat(StripeSdkCheckoutOperations.stateOf("expired")).isEqualTo(ProfessionalCheckoutSessionState.EXPIRED);
        for (String unknown : new String[] {null, "", "EXPIRED", "processing"}) {
            assertThatThrownBy(() -> StripeSdkCheckoutOperations.stateOf(unknown))
                    .as("%s", unknown).isInstanceOf(PaymentUnavailableException.class);
        }
    }

    @Test
    void theGatewayAsksStripeAboutTheExactSession() {
        var operations = mock(StripeCheckoutOperations.class);
        when(operations.state("cs_pending")).thenReturn(ProfessionalCheckoutSessionState.EXPIRED);
        var gateway = new StripeProfessionalPaymentGateway(operations, mock(StripeWebhookEventVerifier.class), "price_expected");

        assertThat(gateway.checkoutSessionState("cs_pending")).isEqualTo(ProfessionalCheckoutSessionState.EXPIRED);
    }

    private static ProfessionalCheckoutRequest request(Optional<String> replaces) {
        return new ProfessionalCheckoutRequest(ENTITLEMENT, UUID.randomUUID(), ProfessionalReportTier.PAID, "price_expected",
                URI.create("https://example.test/returned"), URI.create("https://example.test/cancelled"), replaces);
    }
}
