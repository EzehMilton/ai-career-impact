package com.chikere.futureai.payment.professional.web;

import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.payment.professional.fixture.LocalProfessionalPaymentSimulator;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(LocalProfessionalCheckoutController.class)
@ActiveProfiles("dev")
@Import(WebSecurityConfiguration.class)
class LocalProfessionalCheckoutControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalReportPurchaseUseCase purchaseService;
    @MockitoBean LocalProfessionalPaymentSimulator simulator;

    @Test
    void localSimulationIsExplicitClearlyLabelledAndCsrfProtected() throws Exception {
        UUID assessmentId = UUID.randomUUID(); UUID entitlementId = UUID.randomUUID();
        var entitlement = new ProfessionalReportEntitlement(entitlementId, assessmentId,
                ProfessionalReportTier.PAID, ProfessionalEntitlementStatus.PENDING,
                ProfessionalPaymentProvider.LOCAL_FIXTURE, "local-session",
                URI.create("http://localhost/local"), "fixture-price", Optional.empty(), Optional.empty(),
                Instant.EPOCH, Optional.empty());
        when(purchaseService.findEntitlement(assessmentId)).thenReturn(Optional.of(entitlement));
        String html = mvc.perform(get("/professional-assessment/" + assessmentId
                        + "/checkout/local?session=local-session"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("DEVELOPMENT MODE — LOCAL PAYMENT SIMULATION",
                "No real Stripe checkout or payment has occurred");
        mvc.perform(post("/professional-assessment/" + assessmentId + "/checkout/local/confirm")
                        .param("session", "local-session"))
                .andExpect(status().isForbidden());
        var notification = new ProfessionalPaymentNotification("payload", "signature");
        when(simulator.successfulNotification(entitlement)).thenReturn(notification);
        when(purchaseService.processPaymentNotification(notification))
                .thenReturn(ProfessionalPaymentProcessingResult.ACTIVATED);
        mvc.perform(post("/professional-assessment/" + assessmentId + "/checkout/local/confirm")
                        .with(csrf()).param("session", "local-session"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/professional-assessment/" + assessmentId + "/report"));
    }
}
