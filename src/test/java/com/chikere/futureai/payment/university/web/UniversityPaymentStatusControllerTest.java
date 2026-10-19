package com.chikere.futureai.payment.university.web;

import com.chikere.futureai.payment.university.application.*;
import com.chikere.futureai.report.university.application.UniversityReportQuery;
import com.chikere.futureai.report.university.application.UniversityReportTier;
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
@WebMvcTest(UniversityPaymentStatusController.class)
@Import(WebSecurityConfiguration.class)
class UniversityPaymentStatusControllerTest {
    private static final UUID ID = UUID.fromString("7a2b3c4d-5e6f-4a1b-8c2d-3e4f5a6b7c8d");
    private static final String URL = "/university-assessment/" + ID + "/payment-status";

    @Autowired MockMvc mvc;
    @MockitoBean UniversityReportPurchaseUseCase purchaseService;

    @Test
    void pendingIsReportedAsSavedAndOnlyTheStatusNameLeavesTheServer() throws Exception {
        when(purchaseService.accessFor(ID)).thenReturn(access(UniversityEntitlementStatus.PENDING));

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
        when(purchaseService.accessFor(ID)).thenReturn(access(UniversityEntitlementStatus.ACTIVE));

        assertThat(mvc.perform(get(URL)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEqualTo("{\"status\":\"ACTIVE\"}");
        verify(purchaseService).accessFor(ID);
        verifyNoMoreInteractions(purchaseService);
    }

    @Test
    void aFailedPurchaseIsReportedSoThePageStopsWaiting() throws Exception {
        when(purchaseService.accessFor(ID)).thenReturn(access(UniversityEntitlementStatus.FAILED));

        assertThat(mvc.perform(get(URL)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).isEqualTo("{\"status\":\"FAILED\"}");
    }

    @Test
    void malformedAndUnpurchasedAssessmentsAreNotFoundWithoutDetail() throws Exception {
        mvc.perform(get("/university-assessment/not-a-uuid/payment-status"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        verifyNoInteractions(purchaseService);

        when(purchaseService.accessFor(ID)).thenReturn(new UniversityPurchaseAccess(Optional.empty(), true, Optional.empty()));
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
        var store = mock(UniversityPurchaseStore.class);
        var gateway = mock(UniversityPaymentGateway.class);
        var reports = mock(UniversityReportQuery.class);
        when(store.findEntitlementByAssessmentId(ID)).thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.PENDING)));
        var service = new UniversityReportPurchaseService(reports, store, Optional.of(gateway),
                URI.create("https://example.test"), Optional.of("£4.99"),
                Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC));
        var controller = new UniversityPaymentStatusController(service);

        for (int poll = 0; poll < 20; poll++) {
            var response = controller.status(ID.toString());
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(new UniversityPaymentStatusController.PaymentStatus("PENDING"));
        }

        verify(store, times(20)).findEntitlementByAssessmentId(ID);
        verify(store, never()).confirmAndFreeze(any(), any());
        verify(store, never()).savePending(any());
        verifyNoMoreInteractions(store);
        verifyNoInteractions(gateway, reports);
    }

    private static UniversityPurchaseAccess access(UniversityEntitlementStatus status) {
        return new UniversityPurchaseAccess(Optional.of(status), false, Optional.empty());
    }

    private static UniversityReportEntitlement entitlement(UniversityEntitlementStatus status) {
        return new UniversityReportEntitlement(UUID.randomUUID(), ID, UniversityReportTier.PAID, status,
                UniversityPaymentProvider.STRIPE, "cs_test", URI.create("https://checkout.stripe.test/session"),
                "price_test", Optional.empty(), Optional.empty(), Instant.EPOCH, Optional.empty());
    }
}
