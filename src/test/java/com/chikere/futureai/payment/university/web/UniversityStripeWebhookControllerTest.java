package com.chikere.futureai.payment.university.web;

import com.chikere.futureai.payment.university.application.InvalidPaymentNotificationException;
import com.chikere.futureai.payment.university.application.UniversityPaymentProcessingResult;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UniversityStripeWebhookController.class)
@ActiveProfiles("dev-no-jev")
@Import(WebSecurityConfiguration.class)
class UniversityStripeWebhookControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UniversityReportPurchaseUseCase purchaseService;

    @Test
    void itsOwnPathIsCsrfExemptButStillRequiresAStripeSignature() throws Exception {
        mvc.perform(post("/payments/stripe/university-webhook").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(purchaseService);

        when(purchaseService.processPaymentNotification(any()))
                .thenReturn(UniversityPaymentProcessingResult.ACTIVATED);
        mvc.perform(post("/payments/stripe/university-webhook").content("{}")
                        .header("Stripe-Signature", "t=1,v1=abc"))
                .andExpect(status().isOk());
    }

    @Test
    void anInvalidSignatureIsRejectedAndAProcessingFailureIsRetryable() throws Exception {
        doThrow(new InvalidPaymentNotificationException("secret signature detail"))
                .when(purchaseService).processPaymentNotification(any());
        mvc.perform(post("/payments/stripe/university-webhook").content("{}")
                        .header("Stripe-Signature", "t=1,v1=abc"))
                .andExpect(status().isBadRequest());

        doThrow(new IllegalStateException("database unavailable"))
                .when(purchaseService).processPaymentNotification(any());
        mvc.perform(post("/payments/stripe/university-webhook").content("{}")
                        .header("Stripe-Signature", "t=1,v1=abc"))
                .andExpect(status().isInternalServerError());
    }
}
