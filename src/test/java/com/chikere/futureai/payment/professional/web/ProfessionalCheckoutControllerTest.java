package com.chikere.futureai.payment.professional.web;

import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfessionalCheckoutController.class)
@Import(WebSecurityConfiguration.class)
class ProfessionalCheckoutControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalReportPurchaseService purchaseService;

    @Test
    void checkoutPostRequiresCsrfAndRedirectsOnlyToGatewaySession() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.startCheckout(id)).thenReturn(new ProfessionalCheckoutSession("cs_1",
                URI.create("https://checkout.stripe.test/session"), ProfessionalPaymentProvider.STRIPE,
                Optional.empty()));
        mvc.perform(post("/professional-assessment/" + id + "/checkout")).andExpect(status().isForbidden());
        verifyNoInteractions(purchaseService);
        mvc.perform(post("/professional-assessment/" + id + "/checkout").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("https://checkout.stripe.test/session"));
        verify(purchaseService).startCheckout(id);
    }

    @Test
    void gatewayFailureShowsOnlySafePaymentError() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.startCheckout(id)).thenThrow(new PaymentUnavailableException("secret Stripe failure"));
        mvc.perform(post("/professional-assessment/" + id + "/checkout").with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(status().reason("Paid report checkout is currently unavailable."));
    }
}
