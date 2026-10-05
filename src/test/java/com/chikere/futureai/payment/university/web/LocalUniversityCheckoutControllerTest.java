package com.chikere.futureai.payment.university.web;

import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityPaymentNotification;
import com.chikere.futureai.payment.university.application.UniversityPaymentProcessingResult;
import com.chikere.futureai.payment.university.application.UniversityPaymentProvider;
import com.chikere.futureai.payment.university.application.UniversityReportEntitlement;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.payment.university.fixture.LocalUniversityPaymentSimulator;
import com.chikere.futureai.report.university.application.UniversityReportTier;
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

@WebMvcTest(LocalUniversityCheckoutController.class)
@ActiveProfiles("local-fixture")
@Import(WebSecurityConfiguration.class)
class LocalUniversityCheckoutControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean UniversityReportPurchaseUseCase purchaseService;
    @MockitoBean LocalUniversityPaymentSimulator simulator;

    @Test
    void localSimulationIsExplicitClearlyLabelledAndCsrfProtected() throws Exception {
        UUID assessmentId = UUID.randomUUID();
        var entitlement = entitlement(assessmentId, UniversityEntitlementStatus.PENDING);
        when(purchaseService.findEntitlement(assessmentId)).thenReturn(Optional.of(entitlement));

        String html = mvc.perform(get("/university-assessment/" + assessmentId
                        + "/checkout/local?session=local-university-session"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("DEVELOPMENT MODE — LOCAL PAYMENT SIMULATION",
                "No real Stripe checkout or payment has occurred");

        mvc.perform(post("/university-assessment/" + assessmentId + "/checkout/local/confirm")
                        .param("session", "local-university-session"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(simulator);

        var notification = new UniversityPaymentNotification("payload", "signature");
        when(simulator.successfulNotification(entitlement)).thenReturn(notification);
        when(purchaseService.processPaymentNotification(notification))
                .thenReturn(UniversityPaymentProcessingResult.ACTIVATED);

        mvc.perform(post("/university-assessment/" + assessmentId + "/checkout/local/confirm")
                        .with(csrf()).param("session", "local-university-session"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/university-assessment/" + assessmentId + "/report"));
    }

    @Test
    void aMismatchedSessionOrNonPendingEntitlementIsNotFound() throws Exception {
        UUID assessmentId = UUID.randomUUID();
        when(purchaseService.findEntitlement(assessmentId))
                .thenReturn(Optional.of(entitlement(assessmentId, UniversityEntitlementStatus.PENDING)));

        mvc.perform(get("/university-assessment/" + assessmentId + "/checkout/local?session=wrong"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/university-assessment/" + assessmentId + "/checkout/local/confirm")
                        .with(csrf()).param("session", "wrong"))
                .andExpect(status().isNotFound());
        verifyNoInteractions(simulator);
    }

    private UniversityReportEntitlement entitlement(UUID assessmentId, UniversityEntitlementStatus status) {
        return new UniversityReportEntitlement(UUID.randomUUID(), assessmentId, UniversityReportTier.PAID,
                status, UniversityPaymentProvider.LOCAL_FIXTURE, "local-university-session",
                URI.create("http://localhost/local"), "fixture-price", Optional.empty(), Optional.empty(),
                Instant.EPOCH, Optional.empty());
    }
}
