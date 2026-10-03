package com.chikere.futureai.payment.professional.stripe;

import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.payment.professional.web.StripeWebhookController;
import com.chikere.futureai.pdf.professional.application.*;
import com.chikere.futureai.report.professional.application.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Signed webhook through activation, purchased report and one persisted PDF; no Stripe API calls. */
class ProfessionalStripeCheckoutFlowTest {
    private static final String SECRET = "whsec_offline_test";
    private static final UUID ASSESSMENT = UUID.randomUUID();
    private static final UUID ENTITLEMENT = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void signedCompletionActivatesOnceAndMakesFrozenReportAndPdfAvailable() throws Exception {
        var operations = mock(StripeCheckoutOperations.class);
        var gateway = new StripeProfessionalPaymentGateway(operations,
                new StripeSdkWebhookEventVerifier(SECRET), "price_expected");
        var store = mock(ProfessionalPurchaseStore.class);
        var reportService = mock(ProfessionalReportService.class);
        var paid = mock(ProfessionalReport.class);
        when(paid.tier()).thenReturn(ProfessionalReportTier.PAID);
        when(paid.reportVersion()).thenReturn("professional-report-v1");
        when(paid.profession()).thenReturn("Software Developer");
        when(reportService.assemble(ASSESSMENT, ProfessionalReportTier.PAID))
                .thenReturn(Optional.of(paid));
        when(store.findEntitlementById(ENTITLEMENT)).thenReturn(Optional.of(entitlement(ProfessionalEntitlementStatus.PENDING)));
        when(store.isEventProcessed(ProfessionalPaymentProvider.STRIPE, "evt_test"))
                .thenReturn(false, true);
        when(store.confirmAndFreeze(any(), any()))
                .thenReturn(ProfessionalPurchaseStore.PurchaseConfirmationResult.ACTIVATED);
        var service = new ProfessionalReportPurchaseService(reportService, store, Optional.of(gateway),
                URI.create("http://localhost:8080"), Optional.of("£4.99"), CLOCK);
        var controller = new StripeWebhookController(service);
        String payload = payload(ENTITLEMENT.toString(), "paid", "payment");
        String signature = signature(payload);

        assertThat(controller.receive(payload, signature).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(controller.receive(payload, signature).getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(store, times(1)).confirmAndFreeze(any(), any());
        verify(reportService, times(1)).assemble(ASSESSMENT, ProfessionalReportTier.PAID);

        var snapshot = new PurchasedProfessionalReportSnapshot(ENTITLEMENT, ASSESSMENT,
                paid.reportVersion(), CLOCK.instant(), paid);
        when(store.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(ProfessionalEntitlementStatus.ACTIVE)));
        when(store.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.of(snapshot));
        assertThat(service.findPurchasedReport(ASSESSMENT)).contains(paid);

        var pdfStore = mock(ProfessionalReportPdfStore.class);
        var renderer = mock(ProfessionalReportPdfRenderer.class);
        byte[] bytes = "%PDF-offline-test".getBytes(StandardCharsets.US_ASCII);
        when(renderer.render(any())).thenReturn(new RenderedProfessionalReportPdf(bytes));
        when(pdfStore.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var pdfService = new ProfessionalReportPdfService(store, pdfStore, renderer,
                new ProfessionalReportPdfModelMapper(), 1_024_000, CLOCK);
        var first = pdfService.getOrCreate(ASSESSMENT);
        when(pdfStore.findByEntitlementId(ENTITLEMENT)).thenReturn(Optional.of(first));
        assertThat(pdfService.getOrCreate(ASSESSMENT).bytes()).isEqualTo(bytes);
        verify(renderer, times(1)).render(any());
        verify(pdfStore, times(1)).saveIfAbsent(any());
    }

    @Test
    void unsignedMalformedAndWrongReferenceEventsDoNotActivate() throws Exception {
        var store = mock(ProfessionalPurchaseStore.class);
        var gateway = new StripeProfessionalPaymentGateway(mock(StripeCheckoutOperations.class),
                new StripeSdkWebhookEventVerifier(SECRET), "price_expected");
        var service = new ProfessionalReportPurchaseService(mock(ProfessionalReportService.class), store,
                Optional.of(gateway), URI.create("http://localhost:8080"), Optional.empty(), CLOCK);
        var controller = new StripeWebhookController(service);
        assertThat(controller.receive(payload(ENTITLEMENT.toString(), "paid", "payment"), null)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(controller.receive("malformed", "bad-signature").getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        String wrong = payload(UUID.randomUUID().toString(), "paid", "payment");
        assertThat(controller.receive(wrong, signature(wrong)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(store, never()).confirmAndFreeze(any(), any());
    }

    @Test
    void signedWrongPriceIsRejectedBeforeEntitlementActivation() throws Exception {
        var operations = mock(StripeCheckoutOperations.class);
        doThrow(new InvalidPaymentNotificationException("STRIPE_PRICE_OR_LINE_AMOUNT_MISMATCH"))
                .when(operations).validateCompletedSession(any(), eq("price_expected"));
        var gateway = new StripeProfessionalPaymentGateway(operations,
                new StripeSdkWebhookEventVerifier(SECRET), "price_expected");
        var store = mock(ProfessionalPurchaseStore.class);
        var service = new ProfessionalReportPurchaseService(mock(ProfessionalReportService.class), store,
                Optional.of(gateway), URI.create("http://localhost:8080"), Optional.empty(), CLOCK);
        String payload = payload(ENTITLEMENT.toString(), "paid", "payment");
        assertThat(new StripeWebhookController(service).receive(payload, signature(payload)).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verify(store, never()).confirmAndFreeze(any(), any());
    }

    private static ProfessionalReportEntitlement entitlement(ProfessionalEntitlementStatus status) {
        return new ProfessionalReportEntitlement(ENTITLEMENT, ASSESSMENT, ProfessionalReportTier.PAID,
                status, ProfessionalPaymentProvider.STRIPE, "cs_test",
                URI.create("https://checkout.stripe.test/session"), "price_expected",
                Optional.empty(), status == ProfessionalEntitlementStatus.ACTIVE
                        ? Optional.of("professional-report-v1") : Optional.empty(), CLOCK.instant(),
                status == ProfessionalEntitlementStatus.ACTIVE ? Optional.of(CLOCK.instant()) : Optional.empty());
    }

    private static String payload(String clientReference, String paymentStatus, String mode) {
        long now = Instant.now().getEpochSecond();
        return "{\"id\":\"evt_test\",\"object\":\"event\",\"api_version\":\"2020-08-27\","
                + "\"created\":" + now + ",\"type\":\"checkout.session.completed\",\"data\":{\"object\":{"
                + "\"id\":\"cs_test\",\"object\":\"checkout.session\",\"mode\":\"" + mode
                + "\",\"payment_status\":\"" + paymentStatus + "\",\"client_reference_id\":\""
                + clientReference + "\",\"payment_intent\":\"pi_test\",\"amount_total\":499,"
                + "\"currency\":\"gbp\",\"metadata\":{\"entitlement_id\":\"" + ENTITLEMENT
                + "\",\"assessment_id\":\"" + ASSESSMENT + "\"}}}}";
    }

    private static String signature(String payload) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(
                mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8)));
    }
}
