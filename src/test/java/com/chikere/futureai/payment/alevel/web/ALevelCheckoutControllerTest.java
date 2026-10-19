package com.chikere.futureai.payment.alevel.web;

import com.chikere.futureai.payment.alevel.application.ALevelCheckoutSession;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentProvider;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import com.chikere.futureai.payment.alevel.application.PaymentUnavailableException;
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

@WebMvcTest(ALevelCheckoutController.class)
@Import({WebSecurityConfiguration.class, RecordingJourneyMetricsConfiguration.class})
class ALevelCheckoutControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ALevelReportPurchaseUseCase purchaseService;
    @Autowired MeterRegistry registry;

    @Test
    void checkoutRequiresCsrfAndRedirectsOnlyToTheGatewaySession() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.startCheckout(id)).thenReturn(new ALevelCheckoutSession("cs_1",
                URI.create("https://checkout.stripe.test/a-level"), ALevelPaymentProvider.STRIPE,
                Optional.empty()));

        mvc.perform(post("/a-level-assessment/" + id + "/checkout")).andExpect(status().isForbidden());
        verifyNoInteractions(purchaseService);

        mvc.perform(post("/a-level-assessment/" + id + "/checkout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://checkout.stripe.test/a-level"));
        verify(purchaseService).startCheckout(id);
    }

    @Test
    void aGatewayFailureShowsOnlyASafePaymentError() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.startCheckout(id))
                .thenThrow(new PaymentUnavailableException("secret Stripe failure"));

        mvc.perform(post("/a-level-assessment/" + id + "/checkout").with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(status().reason("Paid report checkout is currently unavailable."));
        assertThat(registry.get("checkout.failures").tag("journey", "a-level").counter().count()).isEqualTo(1);
    }

    @Test
    void aRetryForACheckoutAlreadyPaidAtStripeWaitsForTheWebhookInsteadOfStartingAnotherPayment() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.startCheckout(id)).thenThrow(
                new com.chikere.futureai.payment.alevel.application.CheckoutAwaitingConfirmationException());

        mvc.perform(post("/a-level-assessment/" + id + "/checkout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/a-level-assessment/" + id + "?checkout=returned"));
    }
}
