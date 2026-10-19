package com.chikere.futureai.payment.alevel.web;

import com.chikere.futureai.payment.alevel.application.*;
import com.chikere.futureai.report.alevel.application.ALevelReportQuery;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
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
@WebMvcTest(ALevelPaymentStatusController.class)
@Import(WebSecurityConfiguration.class)
class ALevelPaymentStatusControllerTest {
    private static final UUID ID = UUID.fromString("8b3c4d5e-6f7a-4b2c-9d3e-4f5a6b7c8d9e");
    private static final String URL = "/a-level-assessment/" + ID + "/payment-status";

    @Autowired MockMvc mvc;
    @MockitoBean ALevelReportPurchaseUseCase purchaseService;

    @Test
    void pendingIsReportedAsSavedAndOnlyTheStatusNameLeavesTheServer() throws Exception {
        when(purchaseService.accessFor(ID)).thenReturn(access(ALevelEntitlementStatus.PENDING));

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
        when(purchaseService.accessFor(ID)).thenReturn(access(ALevelEntitlementStatus.ACTIVE));

        assertThat(mvc.perform(get(URL)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEqualTo("{\"status\":\"ACTIVE\"}");
        verify(purchaseService).accessFor(ID);
        verifyNoMoreInteractions(purchaseService);
    }

    @Test
    void aFailedPurchaseIsReportedSoThePageStopsWaiting() throws Exception {
        when(purchaseService.accessFor(ID)).thenReturn(access(ALevelEntitlementStatus.FAILED));

        assertThat(mvc.perform(get(URL)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEqualTo("{\"status\":\"FAILED\"}");
    }

    @Test
    void malformedAndUnpurchasedAssessmentsAreNotFoundWithoutDetail() throws Exception {
        mvc.perform(get("/a-level-assessment/not-a-uuid/payment-status"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        verifyNoInteractions(purchaseService);

        when(purchaseService.accessFor(ID)).thenReturn(new ALevelPurchaseAccess(Optional.empty(), true, Optional.empty()));
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
        var store = mock(ALevelPurchaseStore.class);
        var gateway = mock(ALevelPaymentGateway.class);
        var reports = mock(ALevelReportQuery.class);
        when(store.findEntitlementByAssessmentId(ID)).thenReturn(Optional.of(entitlement(ALevelEntitlementStatus.PENDING)));
        var service = new ALevelReportPurchaseService(reports, store, Optional.of(gateway),
                URI.create("https://example.test"), Optional.of("£4.99"),
                Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC));
        var controller = new ALevelPaymentStatusController(service);

        for (int poll = 0; poll < 20; poll++) {
            var response = controller.status(ID.toString());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(new ALevelPaymentStatusController.PaymentStatus("PENDING"));
        }

        verify(store, times(20)).findEntitlementByAssessmentId(ID);
        verify(store, never()).confirmAndFreeze(any(), any());
        verify(store, never()).savePending(any());
        verifyNoMoreInteractions(store);
        verifyNoInteractions(gateway, reports);
    }

    private static ALevelPurchaseAccess access(ALevelEntitlementStatus status) {
        return new ALevelPurchaseAccess(Optional.of(status), false, Optional.empty());
    }

    private static ALevelReportEntitlement entitlement(ALevelEntitlementStatus status) {
        return new ALevelReportEntitlement(UUID.randomUUID(), ID, ALevelReportTier.PAID, status,
                ALevelPaymentProvider.STRIPE, "cs_test", URI.create("https://checkout.stripe.test/session"),
                "price_test", Optional.empty(), Optional.empty(), Instant.EPOCH, Optional.empty());
    }
}
