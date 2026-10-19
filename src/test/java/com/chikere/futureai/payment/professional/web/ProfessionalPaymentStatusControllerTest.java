package com.chikere.futureai.payment.professional.web;

import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.report.professional.application.ProfessionalReportQuery;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** The status poll reads the saved entitlement back; only the verified webhook ever changes it. */
@WebMvcTest(ProfessionalPaymentStatusController.class)
@Import(WebSecurityConfiguration.class)
class ProfessionalPaymentStatusControllerTest {
    private static final UUID ID = UUID.fromString("6f1e2d3c-4b5a-4968-8776-a5b4c3d2e1f0");
    private static final String URL = "/professional-assessment/" + ID + "/payment-status";

    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalReportPurchaseUseCase purchaseService;

    @Test
    void pendingIsReportedAsSavedAndOnlyTheStatusNameLeavesTheServer() throws Exception {
        when(purchaseService.accessFor(ID)).thenReturn(access(ProfessionalEntitlementStatus.PENDING));

        String body = mvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).isEqualTo("{\"status\":\"PENDING\"}");
        verify(purchaseService).accessFor(ID);
        verifyNoMoreInteractions(purchaseService);
    }

    @Test
    void activeIsReportedOnceTheWebhookHasActivatedTheEntitlement() throws Exception {
        when(purchaseService.accessFor(ID)).thenReturn(access(ProfessionalEntitlementStatus.ACTIVE));

        assertThat(mvc.perform(get(URL)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEqualTo("{\"status\":\"ACTIVE\"}");
        verify(purchaseService).accessFor(ID);
        verifyNoMoreInteractions(purchaseService);
    }

    @Test
    void aFailedPurchaseIsReportedSoThePageStopsWaiting() throws Exception {
        when(purchaseService.accessFor(ID)).thenReturn(access(ProfessionalEntitlementStatus.FAILED));

        assertThat(mvc.perform(get(URL)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEqualTo("{\"status\":\"FAILED\"}");
    }

    @Test
    void malformedAndUnpurchasedAssessmentsAreNotFoundWithoutDetail() throws Exception {
        mvc.perform(get("/professional-assessment/not-a-uuid/payment-status"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        verifyNoInteractions(purchaseService);

        when(purchaseService.accessFor(ID)).thenReturn(new ProfessionalPurchaseAccess(Optional.empty(), true, Optional.empty()));
        assertThat(mvc.perform(get(URL)).andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString()).isEmpty();
    }

    @Test
    void theStatusRouteAcceptsNoWrites() throws Exception {
        mvc.perform(post(URL)).andExpect(status().isForbidden());
        mvc.perform(post(URL).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(put(URL).with(csrf())).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete(URL).with(csrf())).andExpect(status().isMethodNotAllowed());
        verifyNoInteractions(purchaseService);
    }

    @Test
    void repeatedStatusChecksThroughTheRealServiceNeverActivateOrStartACheckout() {
        var store = mock(ProfessionalPurchaseStore.class);
        var gateway = mock(ProfessionalPaymentGateway.class);
        var reports = mock(ProfessionalReportQuery.class);
        when(store.findEntitlementByAssessmentId(ID)).thenReturn(Optional.of(entitlement(ProfessionalEntitlementStatus.PENDING)));
        var service = new ProfessionalReportPurchaseService(reports, store, Optional.of(gateway),
                URI.create("https://example.test"), Optional.of("£4.99"),
                Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC));
        var controller = new ProfessionalPaymentStatusController(service);

        for (int poll = 0; poll < 20; poll++) {
            var response = controller.status(ID.toString());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(new ProfessionalPaymentStatusController.PaymentStatus("PENDING"));
        }

        verify(store, times(20)).findEntitlementByAssessmentId(ID);
        verify(store, never()).confirmAndFreeze(any(), any());
        verify(store, never()).savePending(any());
        verifyNoMoreInteractions(store);
        verifyNoInteractions(gateway, reports);
    }

    private static ProfessionalPurchaseAccess access(ProfessionalEntitlementStatus status) {
        return new ProfessionalPurchaseAccess(Optional.of(status), false, Optional.empty());
    }

    private static ProfessionalReportEntitlement entitlement(ProfessionalEntitlementStatus status) {
        return new ProfessionalReportEntitlement(UUID.randomUUID(), ID, ProfessionalReportTier.PAID, status,
                ProfessionalPaymentProvider.STRIPE, "cs_test", URI.create("https://checkout.stripe.test/session"),
                "price_test", Optional.empty(), Optional.empty(), Instant.EPOCH, Optional.empty());
    }
}
