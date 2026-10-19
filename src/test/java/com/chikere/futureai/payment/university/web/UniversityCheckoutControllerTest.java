package com.chikere.futureai.payment.university.web;

import com.chikere.futureai.payment.university.application.PaymentUnavailableException;
import com.chikere.futureai.payment.university.application.UniversityCheckoutSession;
import com.chikere.futureai.payment.university.application.UniversityPaymentProvider;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.configuration.RecordingJourneyMetricsConfiguration;
import com.chikere.futureai.security.WebSecurityConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UniversityCheckoutController.class)
@Import({WebSecurityConfiguration.class, RecordingJourneyMetricsConfiguration.class})
class UniversityCheckoutControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UniversityReportPurchaseUseCase purchaseService;
    @Autowired MeterRegistry registry;

    @Test
    void checkoutRequiresCsrfAndRedirectsOnlyToTheGatewaySession() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.startCheckout(id)).thenReturn(new UniversityCheckoutSession("cs_1",
                URI.create("https://checkout.stripe.test/university"), UniversityPaymentProvider.STRIPE,
                Optional.empty()));

        mvc.perform(post("/university-assessment/" + id + "/checkout")).andExpect(status().isForbidden());
        verifyNoInteractions(purchaseService);

        mvc.perform(post("/university-assessment/" + id + "/checkout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://checkout.stripe.test/university"));
        verify(purchaseService).startCheckout(id);
    }

    @Test
    void aGatewayFailureShowsOnlyASafePaymentError() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.startCheckout(id))
                .thenThrow(new PaymentUnavailableException("secret Stripe failure"));

        mvc.perform(post("/university-assessment/" + id + "/checkout").with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(status().reason("Paid report checkout is currently unavailable."));
        assertThat(registry.get("checkout.failures").tag("journey", "university").counter().count()).isEqualTo(1);
    }

    @Test
    void aRetryForACheckoutAlreadyPaidAtStripeWaitsForTheWebhookInsteadOfStartingAnotherPayment() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.startCheckout(id)).thenThrow(
                new com.chikere.futureai.payment.university.application.CheckoutAwaitingConfirmationException());

        mvc.perform(post("/university-assessment/" + id + "/checkout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/university-assessment/" + id + "?checkout=returned"));
    }
}
