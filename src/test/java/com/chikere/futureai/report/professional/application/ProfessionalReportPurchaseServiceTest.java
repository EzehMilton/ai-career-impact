package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.payment.professional.application.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfessionalReportPurchaseServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void startsPaidCheckoutOnceThenPersistsPendingEntitlementWithoutReassessment() {
        var source = ProfessionalReportAssemblerTest.source(true, true, true);
        UUID assessmentId = source.assessment().id();
        var paid = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        var reports = mock(ProfessionalReportQuery.class);
        var store = mock(ProfessionalPurchaseStore.class);
        var gateway = mock(ProfessionalPaymentGateway.class);
        when(store.findEntitlementByAssessmentId(assessmentId)).thenReturn(Optional.empty());
        when(reports.assemble(assessmentId, ProfessionalReportTier.PAID)).thenReturn(Optional.of(paid));
        when(gateway.provider()).thenReturn(ProfessionalPaymentProvider.STRIPE);
        when(gateway.priceConfigurationKey()).thenReturn("price_configured");
        when(gateway.createCheckoutSession(any())).thenAnswer(invocation -> {
            ProfessionalCheckoutRequest request = invocation.getArgument(0);
            return new ProfessionalCheckoutSession("cs_test_123", URI.create("https://checkout.stripe.test/session"),
                    ProfessionalPaymentProvider.STRIPE, Optional.of(NOW.plusSeconds(1800)));
        });
        var service = service(reports, store, Optional.of(gateway));

        assertThat(service.startCheckout(assessmentId).checkoutSessionId()).isEqualTo("cs_test_123");

        var request = ArgumentCaptor.forClass(ProfessionalCheckoutRequest.class);
        verify(gateway).createCheckoutSession(request.capture());
        assertThat(request.getValue().assessmentId()).isEqualTo(assessmentId);
        assertThat(request.getValue().reportTier()).isEqualTo(ProfessionalReportTier.PAID);
        assertThat(request.getValue().priceConfigurationKey()).isEqualTo("price_configured");
        var pending = ArgumentCaptor.forClass(ProfessionalReportEntitlement.class);
        verify(store).savePending(pending.capture());
        assertThat(pending.getValue().status()).isEqualTo(ProfessionalEntitlementStatus.PENDING);
        assertThat(pending.getValue().purchasedReportVersion()).isEmpty();
        verify(reports).assemble(assessmentId, ProfessionalReportTier.PAID);
        verify(gateway).provider();
        verify(gateway).priceConfigurationKey();
        verifyNoMoreInteractions(gateway);
    }

    @Test
    void gatewayFailureDoesNotCreateEntitlementOrFallBack() {
        var source = ProfessionalReportAssemblerTest.source(true, true, true);
        var reports = mock(ProfessionalReportQuery.class);
        var store = mock(ProfessionalPurchaseStore.class);
        var gateway = mock(ProfessionalPaymentGateway.class);
        when(store.findEntitlementByAssessmentId(source.assessment().id())).thenReturn(Optional.empty());
        when(reports.assemble(source.assessment().id(), ProfessionalReportTier.PAID))
                .thenReturn(Optional.of(new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID)));
        when(gateway.priceConfigurationKey()).thenReturn("price_configured");
        when(gateway.createCheckoutSession(any())).thenThrow(new IllegalStateException("Stripe unavailable"));

        assertThatThrownBy(() -> service(reports, store, Optional.of(gateway))
                .startCheckout(source.assessment().id())).isInstanceOf(PaymentUnavailableException.class)
                .hasMessage("checkout could not be created");
        verify(store, never()).savePending(any());
    }

    @Test
    void verifiedEventFreezesPaidReportOnlyAtConfirmationAndDuplicateDoesNotReassemble() {
        var source = ProfessionalReportAssemblerTest.source(true, true, true);
        var report = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        UUID entitlementId = UUID.randomUUID();
        var entitlement = pending(entitlementId, source.assessment().id());
        var event = new ProfessionalPaymentEvent("evt_1", ProfessionalPaymentProvider.STRIPE, "cs_1",
                entitlementId, source.assessment().id(), Optional.of("pi_1"), NOW);
        var reports = mock(ProfessionalReportQuery.class);
        var store = mock(ProfessionalPurchaseStore.class);
        var gateway = mock(ProfessionalPaymentGateway.class);
        var notification = new ProfessionalPaymentNotification("signed payload", "signature");
        when(gateway.provider()).thenReturn(ProfessionalPaymentProvider.STRIPE);
        when(gateway.verifyPaymentNotification(notification)).thenReturn(Optional.of(event));
        when(store.isEventProcessed(ProfessionalPaymentProvider.STRIPE, "evt_1")).thenReturn(false, true);
        when(store.findEntitlementById(entitlementId)).thenReturn(Optional.of(entitlement));
        when(store.findSnapshotByAssessmentId(source.assessment().id())).thenReturn(Optional.empty());
        when(reports.assemble(source.assessment().id(), ProfessionalReportTier.PAID)).thenReturn(Optional.of(report));
        when(store.confirmAndFreeze(eq(event), any())).thenReturn(ProfessionalPurchaseStore.PurchaseConfirmationResult.ACTIVATED);
        var service = service(reports, store, Optional.of(gateway));

        assertThat(service.processPaymentNotification(notification))
                .isEqualTo(ProfessionalPaymentProcessingResult.ACTIVATED);
        var snapshot = ArgumentCaptor.forClass(PurchasedProfessionalReportSnapshot.class);
        verify(store).confirmAndFreeze(eq(event), snapshot.capture());
        assertThat(snapshot.getValue().report()).isEqualTo(report);
        assertThat(snapshot.getValue().reportVersion()).isEqualTo("professional-report-v1");
        assertThat(service.processPaymentNotification(notification))
                .isEqualTo(ProfessionalPaymentProcessingResult.ALREADY_PROCESSED);
        verify(reports, times(1)).assemble(source.assessment().id(), ProfessionalReportTier.PAID);
        verify(store, times(1)).confirmAndFreeze(eq(event), any());
    }

    @Test
    void activeAccessReturnsFrozenSnapshotWithoutCallingAssembler() {
        var source = ProfessionalReportAssemblerTest.source(true, true, true);
        var report = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        UUID entitlementId = UUID.randomUUID();
        var active = new ProfessionalReportEntitlement(entitlementId, source.assessment().id(),
                ProfessionalReportTier.PAID, ProfessionalEntitlementStatus.ACTIVE,
                ProfessionalPaymentProvider.STRIPE, "cs_1", URI.create("https://checkout.stripe.test/session"),
                "price_configured", Optional.of("pi_1"), Optional.of(report.reportVersion()), NOW, Optional.of(NOW));
        var snapshot = new PurchasedProfessionalReportSnapshot(entitlementId, source.assessment().id(),
                report.reportVersion(), NOW, report);
        var reports = mock(ProfessionalReportQuery.class);
        var store = mock(ProfessionalPurchaseStore.class);
        when(store.findEntitlementByAssessmentId(source.assessment().id())).thenReturn(Optional.of(active));
        when(store.findSnapshotByAssessmentId(source.assessment().id())).thenReturn(Optional.of(snapshot));

        assertThat(service(reports, store, Optional.empty()).findPurchasedReport(source.assessment().id()))
                .contains(report);
        verifyNoInteractions(reports);
    }

    @Test
    void mismatchedVerifiedMetadataIsRejectedBeforeSnapshotAssembly() {
        var reports = mock(ProfessionalReportQuery.class);
        var store = mock(ProfessionalPurchaseStore.class);
        var gateway = mock(ProfessionalPaymentGateway.class);
        UUID assessmentId = UUID.randomUUID(); UUID entitlementId = UUID.randomUUID();
        var entitlement = pending(entitlementId, assessmentId);
        var event = new ProfessionalPaymentEvent("evt_bad", ProfessionalPaymentProvider.STRIPE, "different",
                entitlementId, assessmentId, Optional.empty(), NOW);
        var notification = new ProfessionalPaymentNotification("payload", "signature");
        when(gateway.provider()).thenReturn(ProfessionalPaymentProvider.STRIPE);
        when(gateway.verifyPaymentNotification(notification)).thenReturn(Optional.of(event));
        when(store.findEntitlementById(entitlementId)).thenReturn(Optional.of(entitlement));

        assertThatThrownBy(() -> service(reports, store, Optional.of(gateway))
                .processPaymentNotification(notification)).isInstanceOf(InvalidPaymentNotificationException.class);
        verifyNoInteractions(reports);
        verify(store, never()).confirmAndFreeze(any(), any());
    }

    @Test
    void snapshotPersistenceFailureDoesNotExposeActiveAccessAndRemainsRetryable() {
        var source = ProfessionalReportAssemblerTest.source(true, true, true);
        var report = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        UUID entitlementId = UUID.randomUUID();
        var entitlement = pending(entitlementId, source.assessment().id());
        var event = new ProfessionalPaymentEvent("evt_retry", ProfessionalPaymentProvider.STRIPE, "cs_1",
                entitlementId, source.assessment().id(), Optional.of("pi_1"), NOW);
        var notification = new ProfessionalPaymentNotification("payload", "signature");
        var reports = mock(ProfessionalReportQuery.class);
        var store = mock(ProfessionalPurchaseStore.class);
        var gateway = mock(ProfessionalPaymentGateway.class);
        when(gateway.provider()).thenReturn(ProfessionalPaymentProvider.STRIPE);
        when(gateway.verifyPaymentNotification(notification)).thenReturn(Optional.of(event));
        when(store.isEventProcessed(ProfessionalPaymentProvider.STRIPE, "evt_retry")).thenReturn(false);
        when(store.findEntitlementById(entitlementId)).thenReturn(Optional.of(entitlement));
        when(store.findEntitlementByAssessmentId(source.assessment().id())).thenReturn(Optional.of(entitlement));
        when(store.findSnapshotByAssessmentId(source.assessment().id())).thenReturn(Optional.empty());
        when(reports.assemble(source.assessment().id(), ProfessionalReportTier.PAID)).thenReturn(Optional.of(report));
        when(store.confirmAndFreeze(eq(event), any())).thenThrow(new IllegalStateException("database unavailable"));
        var service = service(reports, store, Optional.of(gateway));

        assertThatThrownBy(() -> service.processPaymentNotification(notification))
                .isInstanceOf(IllegalStateException.class);
        assertThat(service.findPurchasedReport(source.assessment().id())).isEmpty();
        verify(store).findSnapshotByAssessmentId(source.assessment().id());
    }

    @Test
    void failedEntitlementCanStartAReplacementSessionWithoutChangingItsIdentity() {
        var source = ProfessionalReportAssemblerTest.source(true, true, true);
        UUID assessmentId = source.assessment().id(); UUID entitlementId = UUID.randomUUID();
        var failed = new ProfessionalReportEntitlement(entitlementId, assessmentId, ProfessionalReportTier.PAID,
                ProfessionalEntitlementStatus.FAILED, ProfessionalPaymentProvider.STRIPE, "cs_failed",
                URI.create("https://checkout.stripe.test/failed"), "price_configured", Optional.empty(),
                Optional.empty(), NOW.minusSeconds(60), Optional.empty());
        var paid = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        var reports = mock(ProfessionalReportQuery.class); var store = mock(ProfessionalPurchaseStore.class);
        var gateway = mock(ProfessionalPaymentGateway.class);
        when(store.findEntitlementByAssessmentId(assessmentId)).thenReturn(Optional.of(failed));
        when(reports.assemble(assessmentId, ProfessionalReportTier.PAID)).thenReturn(Optional.of(paid));
        when(gateway.provider()).thenReturn(ProfessionalPaymentProvider.STRIPE);
        when(gateway.priceConfigurationKey()).thenReturn("price_configured");
        when(gateway.createCheckoutSession(any())).thenReturn(new ProfessionalCheckoutSession("cs_retry",
                URI.create("https://checkout.stripe.test/retry"), ProfessionalPaymentProvider.STRIPE,
                Optional.empty()));

        service(reports, store, Optional.of(gateway)).startCheckout(assessmentId);

        var request = ArgumentCaptor.forClass(ProfessionalCheckoutRequest.class);
        verify(gateway).createCheckoutSession(request.capture());
        assertThat(request.getValue().entitlementId()).isEqualTo(entitlementId);
        verify(store).savePending(argThat(value -> value.entitlementId().equals(entitlementId)
                && value.status() == ProfessionalEntitlementStatus.PENDING
                && value.providerCheckoutSessionId().equals("cs_retry")));
    }

    private ProfessionalReportPurchaseService service(ProfessionalReportQuery reports,
            ProfessionalPurchaseStore store, Optional<ProfessionalPaymentGateway> gateway) {
        return new ProfessionalReportPurchaseService(reports, store, gateway,
                URI.create("https://example.test"), Optional.of("Configured price"), CLOCK);
    }

    private ProfessionalReportEntitlement pending(UUID entitlementId, UUID assessmentId) {
        return new ProfessionalReportEntitlement(entitlementId, assessmentId, ProfessionalReportTier.PAID,
                ProfessionalEntitlementStatus.PENDING, ProfessionalPaymentProvider.STRIPE, "cs_1",
                URI.create("https://checkout.stripe.test/session"), "price_configured", Optional.empty(),
                Optional.empty(), NOW, Optional.empty());
    }
}
