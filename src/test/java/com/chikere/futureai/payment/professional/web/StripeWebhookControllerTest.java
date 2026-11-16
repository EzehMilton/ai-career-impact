package com.chikere.futureai.payment.professional.web;

import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = StripeWebhookController.class, properties = "app.stripe.enabled=true")
@ActiveProfiles("dev-no-jev")
@Import(WebSecurityConfiguration.class)
class StripeWebhookControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalReportPurchaseUseCase purchaseService;

    @Test
    void exactWebhookPathUsesSignatureRatherThanBrowserCsrf() throws Exception {
        var notification = new ProfessionalPaymentNotification("payload", "signature");
        when(purchaseService.processPaymentNotification(notification))
                .thenReturn(ProfessionalPaymentProcessingResult.ACTIVATED);
        mvc.perform(post("/payments/stripe/webhook").content("payload")
                        .header("Stripe-Signature", "signature"))
                .andExpect(status().isOk());
        verify(purchaseService).processPaymentNotification(notification);
    }

    @Test
    void missingOrInvalidSignatureIsRejectedWithoutDetails() throws Exception {
        mvc.perform(post("/payments/stripe/webhook").content("payload")).andExpect(status().isBadRequest());
        var notification = new ProfessionalPaymentNotification("payload", "invalid");
        when(purchaseService.processPaymentNotification(notification))
                .thenThrow(new InvalidPaymentNotificationException("secret signing diagnosis"));
        mvc.perform(post("/payments/stripe/webhook").content("payload")
                        .header("Stripe-Signature", "invalid"))
                .andExpect(status().isBadRequest());
    }
}
