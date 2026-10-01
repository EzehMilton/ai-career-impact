package com.chikere.futureai.payment.alevel.application;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceStore;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeStore;
import com.chikere.futureai.report.alevel.application.ALevelReport;
import com.chikere.futureai.report.alevel.application.ALevelReportAssembler;
import com.chikere.futureai.report.alevel.application.ALevelReportService;
import com.chikere.futureai.report.alevel.application.ALevelReportSource;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ALevelReportPurchaseServiceTest {

    private static final UUID ASSESSMENT = UUID.fromString("8b9c0d1e-2f3a-4b4c-8d5e-6f7a8b9c0d1e");
    private static final UUID ENTITLEMENT = UUID.fromString("9c0d1e2f-3a4b-4c5d-8e6f-7a8b9c0d1e2f");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    private final ALevelAssessmentStore assessmentStore = mock(ALevelAssessmentStore.class);
    private final ALevelNarrativeStore narrativeStore = mock(ALevelNarrativeStore.class);
    private final ALevelEvidenceStore evidenceStore = mock(ALevelEvidenceStore.class);
    private final ALevelPurchaseStore purchaseStore = mock(ALevelPurchaseStore.class);
    private final ALevelReportService reportService = new ALevelReportService(assessmentStore,
            narrativeStore, evidenceStore, new ALevelReportAssembler());

    @Test
    void withoutAConfiguredGatewayCheckoutIsUnavailableAndNothingIsPersisted() {
        var service = service(Optional.empty());
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startCheckout(ASSESSMENT))
                .isInstanceOf(PaymentUnavailableException.class);
        assertThat(service.accessFor(ASSESSMENT).checkoutAvailable()).isFalse();
        verify(purchaseStore, never()).savePending(any());
    }

    @Test
    void checkoutCallsTheGatewayOnceThenPersistsAPendingEntitlement() {
        var gateway = mock(ALevelPaymentGateway.class);
        stubAssessment();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        when(gateway.provider()).thenReturn(ALevelPaymentProvider.STRIPE);
        when(gateway.priceConfigurationKey()).thenReturn("price_a_level");
        doReturn(new ALevelCheckoutSession("cs_test", URI.create("https://checkout.stripe.test/x"),
                ALevelPaymentProvider.STRIPE, Optional.empty()))
                .when(gateway).createCheckoutSession(any());

        var session = service(Optional.of(gateway)).startCheckout(ASSESSMENT);

        ArgumentCaptor<ALevelCheckoutRequest> request = ArgumentCaptor.forClass(ALevelCheckoutRequest.class);
        verify(gateway).createCheckoutSession(request.capture());
        assertThat(request.getValue().reportTier()).isEqualTo(ALevelReportTier.PAID);
        assertThat(request.getValue().priceConfigurationKey()).isEqualTo("price_a_level");
        assertThat(request.getValue().successUrl().toString())
                .isEqualTo("http://localhost:8080/a-level-assessment/" + ASSESSMENT + "?checkout=returned");

        ArgumentCaptor<ALevelReportEntitlement> pending =
                ArgumentCaptor.forClass(ALevelReportEntitlement.class);
        verify(purchaseStore).savePending(pending.capture());
        assertThat(pending.getValue().status()).isEqualTo(ALevelEntitlementStatus.PENDING);
        assertThat(pending.getValue().assessmentId()).isEqualTo(ASSESSMENT);
        assertThat(session.checkoutSessionId()).isEqualTo("cs_test");
    }

    @Test
    void anExistingNonFailedPurchaseBlocksAnotherCheckout() {
        var gateway = mock(ALevelPaymentGateway.class);
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(ALevelEntitlementStatus.ACTIVE)));

        var service = service(Optional.of(gateway));

        assertThatThrownBy(() -> service.startCheckout(ASSESSMENT))
                .isInstanceOf(PaymentUnavailableException.class)
                .hasMessageContaining("already exists");
        verify(gateway, never()).createCheckoutSession(any());
    }

    @Test
    void gatewayFailureNeverPersistsAnEntitlementAndNeverFallsBackToAFixture() {
        var gateway = mock(ALevelPaymentGateway.class);
        stubAssessment();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        when(gateway.provider()).thenReturn(ALevelPaymentProvider.STRIPE);
        when(gateway.priceConfigurationKey()).thenReturn("price_a_level");
        doThrow(new IllegalStateException("stripe unavailable")).when(gateway).createCheckoutSession(any());

        var service = service(Optional.of(gateway));

        assertThatThrownBy(() -> service.startCheckout(ASSESSMENT))
                .isInstanceOf(PaymentUnavailableException.class);
        verify(purchaseStore, never()).savePending(any());
    }

    @Test
    void confirmationFreezesTheExactPaidReportAndIsIdempotent() {
        var gateway = mock(ALevelPaymentGateway.class);
        stubAssessment();
        var event = event();
        when(gateway.provider()).thenReturn(ALevelPaymentProvider.STRIPE);
        doReturn(Optional.of(event)).when(gateway).verifyPaymentNotification(any());
        when(purchaseStore.isEventProcessed(ALevelPaymentProvider.STRIPE, "evt_1")).thenReturn(false);
        when(purchaseStore.findEntitlementById(ENTITLEMENT))
                .thenReturn(Optional.of(entitlement(ALevelEntitlementStatus.PENDING)));
        when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        when(purchaseStore.confirmAndFreeze(any(), any()))
                .thenReturn(ALevelPurchaseStore.PurchaseConfirmationResult.ACTIVATED);

        var result = service(Optional.of(gateway))
                .processPaymentNotification(new ALevelPaymentNotification("payload", "signature"));

        assertThat(result).isEqualTo(ALevelPaymentProcessingResult.ACTIVATED);
        ArgumentCaptor<PurchasedALevelReportSnapshot> snapshot =
                ArgumentCaptor.forClass(PurchasedALevelReportSnapshot.class);
        verify(purchaseStore).confirmAndFreeze(any(), snapshot.capture());
        assertThat(snapshot.getValue().reportVersion()).isEqualTo("a-level-report-v1");
        assertThat(snapshot.getValue().report().tier()).isEqualTo(ALevelReportTier.PAID);
        assertThat(snapshot.getValue().report().measures()).hasSize(6);
        assertThat(snapshot.getValue().frozenAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
    }

    @Test
    void aDuplicateOrUnknownEventNeverRebuildsOrDuplicatesTheReport() {
        var gateway = mock(ALevelPaymentGateway.class);
        when(gateway.provider()).thenReturn(ALevelPaymentProvider.STRIPE);
        doReturn(Optional.of(event())).when(gateway).verifyPaymentNotification(any());
        when(purchaseStore.isEventProcessed(ALevelPaymentProvider.STRIPE, "evt_1")).thenReturn(true);

        assertThat(service(Optional.of(gateway))
                .processPaymentNotification(new ALevelPaymentNotification("payload", "signature")))
                .isEqualTo(ALevelPaymentProcessingResult.ALREADY_PROCESSED);
        verify(purchaseStore, never()).confirmAndFreeze(any(), any());

        when(purchaseStore.isEventProcessed(ALevelPaymentProvider.STRIPE, "evt_1")).thenReturn(false);
        when(purchaseStore.findEntitlementById(ENTITLEMENT)).thenReturn(Optional.empty());
        var service = service(Optional.of(gateway));
        assertThatThrownBy(() -> service.processPaymentNotification(
                new ALevelPaymentNotification("payload", "signature")))
                .isInstanceOf(InvalidPaymentNotificationException.class);
    }

    @Test
    void anUnrecognisedEventIsIgnoredRatherThanTreatedAsAnError() {
        var gateway = mock(ALevelPaymentGateway.class);
        when(gateway.provider()).thenReturn(ALevelPaymentProvider.STRIPE);
        doReturn(Optional.empty()).when(gateway).verifyPaymentNotification(any());

        assertThat(service(Optional.of(gateway))
                .processPaymentNotification(new ALevelPaymentNotification("payload", "signature")))
                .isEqualTo(ALevelPaymentProcessingResult.IGNORED);
        verify(purchaseStore, never()).confirmAndFreeze(any(), any());
    }

    @Test
    void onlyAnActiveEntitlementReturnsTheFrozenPurchasedReport() {
        var frozen = frozenSnapshot();
        var service = service(Optional.empty());

        for (ALevelEntitlementStatus status : ALevelEntitlementStatus.values()) {
            when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                    .thenReturn(Optional.of(entitlement(status)));
            when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.of(frozen));

            if (status == ALevelEntitlementStatus.ACTIVE) {
                assertThat(service.findPurchasedReport(ASSESSMENT)).contains(frozen.report());
            }
            else {
                assertThat(service.findPurchasedReport(ASSESSMENT)).as("%s", status).isEmpty();
            }
        }
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        assertThat(service.findPurchasedReport(ASSESSMENT)).isEmpty();
    }

    @Test
    void theFrozenSnapshotIsAuthoritativeEvenWhenTodaysAssemblerWouldFail() {
        var frozen = frozenSnapshot();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(ALevelEntitlementStatus.ACTIVE)));
        when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.of(frozen));
        // The underlying assessment is gone, so re-assembly today is impossible.
        when(assessmentStore.findById(ASSESSMENT)).thenReturn(Optional.empty());

        assertThat(service(Optional.empty()).findPurchasedReport(ASSESSMENT)).contains(frozen.report());
    }

    private ALevelReportPurchaseService service(Optional<ALevelPaymentGateway> gateway) {
        return new ALevelReportPurchaseService(reportService, purchaseStore, gateway,
                URI.create("http://localhost:8080"), Optional.of("£3.99"), CLOCK);
    }

    private void stubAssessment() {
        when(assessmentStore.findById(ASSESSMENT)).thenReturn(Optional.of(snapshot()));
        when(narrativeStore.findByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        when(evidenceStore.findByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
    }

    private ALevelPaymentEvent event() {
        return new ALevelPaymentEvent("evt_1", ALevelPaymentProvider.STRIPE, "cs_test",
                ENTITLEMENT, ASSESSMENT, Optional.of("pi_1"), Instant.parse("2026-10-01T12:00:00Z"));
    }

    private ALevelReportEntitlement entitlement(ALevelEntitlementStatus status) {
        return new ALevelReportEntitlement(ENTITLEMENT, ASSESSMENT, ALevelReportTier.PAID, status,
                ALevelPaymentProvider.STRIPE, "cs_test", URI.create("https://checkout.stripe.test/x"),
                "price_a_level", Optional.empty(),
                status == ALevelEntitlementStatus.ACTIVE
                        ? Optional.of("a-level-report-v1") : Optional.empty(),
                Instant.parse("2026-10-01T11:59:00Z"),
                status == ALevelEntitlementStatus.ACTIVE
                        ? Optional.of(Instant.parse("2026-10-01T12:00:00Z")) : Optional.empty());
    }

    private PurchasedALevelReportSnapshot frozenSnapshot() {
        ALevelReport report = new ALevelReportAssembler().assemble(
                new ALevelReportSource(snapshot(), Optional.empty(), Optional.empty()),
                ALevelReportTier.PAID);
        return new PurchasedALevelReportSnapshot(ENTITLEMENT, ASSESSMENT, report.reportVersion(),
                Instant.parse("2026-10-01T12:00:00Z"), report);
    }

    private StoredALevelAssessment snapshot() {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of(), List.of(), List.of("Economics"), List.of(), Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureALevelJudgmentProvider("maths_economics_geography").judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        return new StoredALevelAssessment(ASSESSMENT, Instant.parse("2026-10-01T11:58:00Z"), state,
                judgments, new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }
}
