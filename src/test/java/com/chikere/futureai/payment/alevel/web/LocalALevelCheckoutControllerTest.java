package com.chikere.futureai.payment.alevel.web;

import com.chikere.futureai.payment.alevel.application.ALevelEntitlementStatus;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentNotification;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentProcessingResult;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentProvider;
import com.chikere.futureai.payment.alevel.application.ALevelReportEntitlement;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import com.chikere.futureai.payment.alevel.fixture.LocalALevelPaymentSimulator;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LocalALevelCheckoutController.class)
@ActiveProfiles("local-fixture")
@Import(WebSecurityConfiguration.class)
class LocalALevelCheckoutControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ALevelReportPurchaseUseCase purchaseService;
    @MockitoBean LocalALevelPaymentSimulator simulator;

    @Test
    void localSimulationIsExplicitClearlyLabelledAndCsrfProtected() throws Exception {
        UUID assessmentId = UUID.randomUUID();
        var entitlement = entitlement(assessmentId, ALevelEntitlementStatus.PENDING);
        when(purchaseService.findEntitlement(assessmentId)).thenReturn(Optional.of(entitlement));

        String html = mvc.perform(get("/a-level-assessment/" + assessmentId
                        + "/checkout/local?session=local-a-level-session"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("DEVELOPMENT MODE — LOCAL PAYMENT SIMULATION",
                "No real Stripe checkout or payment has occurred");

        mvc.perform(post("/a-level-assessment/" + assessmentId + "/checkout/local/confirm")
                        .param("session", "local-a-level-session"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(simulator);

        var notification = new ALevelPaymentNotification("payload", "signature");
        when(simulator.successfulNotification(entitlement)).thenReturn(notification);
        when(purchaseService.processPaymentNotification(notification))
                .thenReturn(ALevelPaymentProcessingResult.ACTIVATED);

        mvc.perform(post("/a-level-assessment/" + assessmentId + "/checkout/local/confirm")
                        .with(csrf()).param("session", "local-a-level-session"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/a-level-assessment/" + assessmentId + "/report"));
    }

    @Test
    void aMismatchedSessionOrNonPendingEntitlementIsNotFound() throws Exception {
        UUID assessmentId = UUID.randomUUID();
        when(purchaseService.findEntitlement(assessmentId))
                .thenReturn(Optional.of(entitlement(assessmentId, ALevelEntitlementStatus.PENDING)));

        mvc.perform(get("/a-level-assessment/" + assessmentId + "/checkout/local?session=wrong"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/a-level-assessment/" + assessmentId + "/checkout/local/confirm")
                        .with(csrf()).param("session", "wrong"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(simulator);
    }

    private ALevelReportEntitlement entitlement(UUID assessmentId, ALevelEntitlementStatus status) {
        return new ALevelReportEntitlement(UUID.randomUUID(), assessmentId, ALevelReportTier.PAID,
                status, ALevelPaymentProvider.LOCAL_FIXTURE, "local-a-level-session",
                URI.create("http://localhost/local"), "fixture-price", Optional.empty(),
                Optional.empty(), Instant.EPOCH, Optional.empty());
    }
}
