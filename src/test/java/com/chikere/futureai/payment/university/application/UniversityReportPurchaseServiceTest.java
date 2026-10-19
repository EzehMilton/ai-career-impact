package com.chikere.futureai.payment.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceStore;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeStore;
import com.chikere.futureai.report.university.application.UniversityReport;
import com.chikere.futureai.report.university.application.UniversityReportAssembler;
import com.chikere.futureai.report.university.application.UniversityReportService;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UniversityReportPurchaseServiceTest {

    private static final UUID ASSESSMENT = UUID.fromString("6e7f8a9b-0c1d-4e2f-8a3b-4c5d6e7f8a9b");
    private static final UUID ENTITLEMENT = UUID.fromString("7f8a9b0c-1d2e-4f3a-8b4c-5d6e7f8a9b0c");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    private final UniversityAssessmentStore assessmentStore = mock(UniversityAssessmentStore.class);
    private final UniversityNarrativeStore narrativeStore = mock(UniversityNarrativeStore.class);
    private final UniversityEvidenceStore evidenceStore = mock(UniversityEvidenceStore.class);
    private final UniversityPurchaseStore purchaseStore = mock(UniversityPurchaseStore.class);
    private final UniversityReportService reportService = new UniversityReportService(assessmentStore,
            narrativeStore, evidenceStore, new UniversityReportAssembler());

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
        var gateway = mock(UniversityPaymentGateway.class);
        stubAssessment();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        when(gateway.provider()).thenReturn(UniversityPaymentProvider.STRIPE);
        when(gateway.priceConfigurationKey()).thenReturn("price_configured");
        doReturn(new UniversityCheckoutSession("cs_test", URI.create("https://checkout.stripe.test/x"),
                UniversityPaymentProvider.STRIPE, Optional.empty()))
                .when(gateway).createCheckoutSession(any());

        var session = service(Optional.of(gateway)).startCheckout(ASSESSMENT);

        ArgumentCaptor<UniversityCheckoutRequest> request =
                ArgumentCaptor.forClass(UniversityCheckoutRequest.class);
        verify(gateway).createCheckoutSession(request.capture());
        assertThat(request.getValue().reportTier()).isEqualTo(UniversityReportTier.PAID);
        assertThat(request.getValue().priceConfigurationKey()).isEqualTo("price_configured");
        assertThat(request.getValue().successUrl().toString())
                .isEqualTo("http://localhost:8080/university-assessment/" + ASSESSMENT + "?checkout=returned");

        ArgumentCaptor<UniversityReportEntitlement> pending =
                ArgumentCaptor.forClass(UniversityReportEntitlement.class);
        verify(purchaseStore).savePending(pending.capture());
        assertThat(pending.getValue().status()).isEqualTo(UniversityEntitlementStatus.PENDING);
        assertThat(pending.getValue().assessmentId()).isEqualTo(ASSESSMENT);
        assertThat(session.checkoutSessionId()).isEqualTo("cs_test");
    }

    @Test
    void anExistingNonFailedPurchaseBlocksAnotherCheckout() {
        var gateway = mock(UniversityPaymentGateway.class);
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.ACTIVE)));

        var service = service(Optional.of(gateway));

        assertThatThrownBy(() -> service.startCheckout(ASSESSMENT))
                .isInstanceOf(PaymentUnavailableException.class)
                .hasMessageContaining("already exists");
        verify(gateway, never()).createCheckoutSession(any());
    }

    @Test
    void gatewayFailureNeverPersistsAnEntitlementAndNeverFallsBackToAFixture() {
        var gateway = mock(UniversityPaymentGateway.class);
        stubAssessment();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        when(gateway.provider()).thenReturn(UniversityPaymentProvider.STRIPE);
        when(gateway.priceConfigurationKey()).thenReturn("price_configured");
        doThrow(new IllegalStateException("stripe unavailable")).when(gateway).createCheckoutSession(any());

        var service = service(Optional.of(gateway));

        assertThatThrownBy(() -> service.startCheckout(ASSESSMENT))
                .isInstanceOf(PaymentUnavailableException.class);
        verify(purchaseStore, never()).savePending(any());
    }

    @Test
    void confirmationFreezesTheExactPaidReportAndIsIdempotent() {
        var gateway = mock(UniversityPaymentGateway.class);
        stubAssessment();
        var event = event();
        when(gateway.provider()).thenReturn(UniversityPaymentProvider.STRIPE);
        doReturn(Optional.of(event)).when(gateway).verifyPaymentNotification(any());
        when(purchaseStore.isEventProcessed(UniversityPaymentProvider.STRIPE, "evt_1")).thenReturn(false);
        when(purchaseStore.findEntitlementById(ENTITLEMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.PENDING)));
        when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        when(purchaseStore.confirmAndFreeze(any(), any()))
                .thenReturn(UniversityPurchaseStore.PurchaseConfirmationResult.ACTIVATED);

        var result = service(Optional.of(gateway))
                .processPaymentNotification(new UniversityPaymentNotification("payload", "signature"));

        assertThat(result).isEqualTo(UniversityPaymentProcessingResult.ACTIVATED);
        ArgumentCaptor<PurchasedUniversityReportSnapshot> snapshot =
                ArgumentCaptor.forClass(PurchasedUniversityReportSnapshot.class);
        verify(purchaseStore).confirmAndFreeze(any(), snapshot.capture());
        assertThat(snapshot.getValue().reportVersion()).isEqualTo("university-report-v1");
        assertThat(snapshot.getValue().report().tier()).isEqualTo(UniversityReportTier.PAID);
        assertThat(snapshot.getValue().report().measures()).hasSize(5);
        assertThat(snapshot.getValue().frozenAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
    }

    @Test
    void aDuplicateOrUnknownEventNeverRebuildsOrDuplicatesTheReport() {
        var gateway = mock(UniversityPaymentGateway.class);
        when(gateway.provider()).thenReturn(UniversityPaymentProvider.STRIPE);
        doReturn(Optional.of(event())).when(gateway).verifyPaymentNotification(any());
        when(purchaseStore.isEventProcessed(UniversityPaymentProvider.STRIPE, "evt_1")).thenReturn(true);

        assertThat(service(Optional.of(gateway))
                .processPaymentNotification(new UniversityPaymentNotification("payload", "signature")))
                .isEqualTo(UniversityPaymentProcessingResult.ALREADY_PROCESSED);
        verify(purchaseStore, never()).confirmAndFreeze(any(), any());

        when(purchaseStore.isEventProcessed(UniversityPaymentProvider.STRIPE, "evt_1")).thenReturn(false);
        when(purchaseStore.findEntitlementById(ENTITLEMENT)).thenReturn(Optional.empty());
        var service = service(Optional.of(gateway));
        assertThatThrownBy(() -> service.processPaymentNotification(
                new UniversityPaymentNotification("payload", "signature")))
                .isInstanceOf(InvalidPaymentNotificationException.class);
    }

    @Test
    void anUnrecognisedEventIsIgnoredRatherThanTreatedAsAnError() {
        var gateway = mock(UniversityPaymentGateway.class);
        when(gateway.provider()).thenReturn(UniversityPaymentProvider.STRIPE);
        doReturn(Optional.empty()).when(gateway).verifyPaymentNotification(any());

        assertThat(service(Optional.of(gateway))
                .processPaymentNotification(new UniversityPaymentNotification("payload", "signature")))
                .isEqualTo(UniversityPaymentProcessingResult.IGNORED);
        verify(purchaseStore, never()).confirmAndFreeze(any(), any());
    }

    @Test
    void onlyAnActiveEntitlementReturnsTheFrozenPurchasedReport() {
        var frozen = frozenSnapshot();
        var service = service(Optional.empty());

        for (UniversityEntitlementStatus status : UniversityEntitlementStatus.values()) {
            when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                    .thenReturn(Optional.of(entitlement(status)));
            when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.of(frozen));

            if (status == UniversityEntitlementStatus.ACTIVE) {
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
    void theFrozenSnapshotIsAuthoritativeEvenWhenTodaysAssemblerWouldDifferOrFail() {
        var frozen = frozenSnapshot();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.ACTIVE)));
        when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.of(frozen));
        // The underlying assessment is gone, so re-assembly today is impossible.
        when(assessmentStore.findById(ASSESSMENT)).thenReturn(Optional.empty());

        assertThat(service(Optional.empty()).findPurchasedReport(ASSESSMENT)).contains(frozen.report());
    }

    @Test
    void retryingAPendingCheckoutWhoseStripeSessionIsStillOpenResumesItWithoutAnyNewSessionOrStateChange() {
        var gateway = stripeGateway();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.PENDING)));
        when(gateway.checkoutSessionState("cs_test")).thenReturn(UniversityCheckoutSessionState.OPEN);

        var session = service(Optional.of(gateway)).startCheckout(ASSESSMENT);

        assertThat(session.checkoutSessionId()).isEqualTo("cs_test");
        assertThat(session.redirectUrl()).isEqualTo(URI.create("https://checkout.stripe.test/x"));
        verify(gateway, never()).createCheckoutSession(any());
        verify(purchaseStore, never()).savePending(any());
        verify(purchaseStore, never()).replaceExpiredCheckout(any(), any(), any(), any(), any());
        verify(purchaseStore, never()).confirmAndFreeze(any(), any());
    }

    @Test
    void retryingAfterTheStripeSessionExpiredReplacesItForTheSameEntitlementWhichStaysPending() {
        var gateway = stripeGateway();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.PENDING)));
        when(gateway.checkoutSessionState("cs_test")).thenReturn(UniversityCheckoutSessionState.EXPIRED);
        var replacement = new UniversityCheckoutSession("cs_retry", URI.create("https://checkout.stripe.test/retry"),
                UniversityPaymentProvider.STRIPE, Optional.empty());
        doReturn(replacement).when(gateway).createCheckoutSession(any());

        var session = service(Optional.of(gateway)).startCheckout(ASSESSMENT);

        assertThat(session).isEqualTo(replacement);
        ArgumentCaptor<UniversityCheckoutRequest> request = ArgumentCaptor.forClass(UniversityCheckoutRequest.class);
        verify(gateway).createCheckoutSession(request.capture());
        assertThat(request.getValue().entitlementId()).isEqualTo(ENTITLEMENT);
        assertThat(request.getValue().replacesCheckoutSessionId()).contains("cs_test");
        assertThat(request.getValue().cancelUrl().toString())
                .isEqualTo("http://localhost:8080/university-assessment/" + ASSESSMENT + "?checkout=cancelled");
        verify(purchaseStore).replaceExpiredCheckout(ENTITLEMENT, "cs_test", replacement, "price_configured",
                Instant.parse("2026-10-01T12:00:00Z"));
        verify(purchaseStore, never()).savePending(any());
        verify(purchaseStore, never()).confirmAndFreeze(any(), any());
    }

    @Test
    void retryingACheckoutAlreadyPaidAtStripeNeverStartsASecondPayment() {
        var gateway = stripeGateway();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.PENDING)));
        when(gateway.checkoutSessionState("cs_test")).thenReturn(UniversityCheckoutSessionState.COMPLETE);

        var service = service(Optional.of(gateway));

        assertThatThrownBy(() -> service.startCheckout(ASSESSMENT))
                .isInstanceOf(CheckoutAwaitingConfirmationException.class);
        verify(gateway, never()).createCheckoutSession(any());
        verify(purchaseStore, never()).replaceExpiredCheckout(any(), any(), any(), any(), any());
        verify(purchaseStore, never()).confirmAndFreeze(any(), any());
    }

    @Test
    void whenStripeCannotSayWhatHappenedToTheSessionNoReplacementIsGuessed() {
        var gateway = stripeGateway();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.PENDING)));
        when(gateway.checkoutSessionState("cs_test")).thenThrow(new IllegalStateException("stripe unavailable"));

        var service = service(Optional.of(gateway));

        assertThatThrownBy(() -> service.startCheckout(ASSESSMENT)).isInstanceOf(PaymentUnavailableException.class);
        verify(gateway, never()).createCheckoutSession(any());
        verify(purchaseStore, never()).replaceExpiredCheckout(any(), any(), any(), any(), any());
    }

    @Test
    void anActivePurchaseNeverAsksStripeOrStartsAnotherCheckout() {
        var gateway = stripeGateway();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.ACTIVE)));

        var service = service(Optional.of(gateway));

        assertThatThrownBy(() -> service.startCheckout(ASSESSMENT))
                .isInstanceOf(PaymentUnavailableException.class).hasMessageContaining("already exists");
        verify(gateway, never()).checkoutSessionState(any());
        verify(gateway, never()).createCheckoutSession(any());
    }

    @Test
    void afterARetryOnlyTheVerifiedEventForTheReplacementSessionActivatesAndOnlyOnce() {
        var gateway = stripeGateway();
        stubAssessment();
        var replaced = new UniversityReportEntitlement(ENTITLEMENT, ASSESSMENT, UniversityReportTier.PAID,
                UniversityEntitlementStatus.PENDING, UniversityPaymentProvider.STRIPE, "cs_retry",
                URI.create("https://checkout.stripe.test/retry"), "price_configured", Optional.empty(),
                Optional.empty(), Instant.parse("2026-10-01T12:00:00Z"), Optional.empty());
        when(purchaseStore.findEntitlementById(ENTITLEMENT)).thenReturn(Optional.of(replaced));
        when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        var expiredSession = new UniversityPaymentEvent("evt_expired", UniversityPaymentProvider.STRIPE, "cs_test",
                ENTITLEMENT, ASSESSMENT, Optional.of("pi_0"), Instant.parse("2026-10-01T12:00:00Z"));
        var retried = new UniversityPaymentEvent("evt_retry", UniversityPaymentProvider.STRIPE, "cs_retry",
                ENTITLEMENT, ASSESSMENT, Optional.of("pi_1"), Instant.parse("2026-10-01T12:00:00Z"));
        var expiredNotification = new UniversityPaymentNotification("expired-session payload", "signature");
        var retriedNotification = new UniversityPaymentNotification("retried-session payload", "signature");
        doReturn(Optional.of(expiredSession)).when(gateway).verifyPaymentNotification(expiredNotification);
        doReturn(Optional.of(retried)).when(gateway).verifyPaymentNotification(retriedNotification);
        when(purchaseStore.isEventProcessed(UniversityPaymentProvider.STRIPE, "evt_expired")).thenReturn(false);
        when(purchaseStore.isEventProcessed(UniversityPaymentProvider.STRIPE, "evt_retry")).thenReturn(false, true);
        when(purchaseStore.confirmAndFreeze(eq(retried), any()))
                .thenReturn(UniversityPurchaseStore.PurchaseConfirmationResult.ACTIVATED);
        var service = service(Optional.of(gateway));

        assertThatThrownBy(() -> service.processPaymentNotification(expiredNotification))
                .isInstanceOf(InvalidPaymentNotificationException.class);
        verify(purchaseStore, never()).confirmAndFreeze(any(), any());

        assertThat(service.processPaymentNotification(retriedNotification))
                .isEqualTo(UniversityPaymentProcessingResult.ACTIVATED);
        assertThat(service.processPaymentNotification(retriedNotification))
                .isEqualTo(UniversityPaymentProcessingResult.ALREADY_PROCESSED);
        verify(purchaseStore, times(1)).confirmAndFreeze(eq(retried), any());
        // Every notification, including the retried one, went through the gateway's verification.
        verify(gateway, times(3)).verifyPaymentNotification(any());
    }

    private UniversityPaymentGateway stripeGateway() {
        var gateway = mock(UniversityPaymentGateway.class);
        when(gateway.provider()).thenReturn(UniversityPaymentProvider.STRIPE);
        when(gateway.priceConfigurationKey()).thenReturn("price_configured");
        return gateway;
    }

    private UniversityReportPurchaseService service(Optional<UniversityPaymentGateway> gateway) {
        return new UniversityReportPurchaseService(reportService, purchaseStore, gateway,
                URI.create("http://localhost:8080"), Optional.of("£4.99"), CLOCK);
    }

    private void stubAssessment() {
        when(assessmentStore.findById(ASSESSMENT)).thenReturn(Optional.of(snapshot()));
        when(narrativeStore.findByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        when(evidenceStore.findByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
    }

    private UniversityPaymentEvent event() {
        return new UniversityPaymentEvent("evt_1", UniversityPaymentProvider.STRIPE, "cs_test",
                ENTITLEMENT, ASSESSMENT, Optional.of("pi_1"), Instant.parse("2026-10-01T12:00:00Z"));
    }

    private UniversityReportEntitlement entitlement(UniversityEntitlementStatus status) {
        return new UniversityReportEntitlement(ENTITLEMENT, ASSESSMENT, UniversityReportTier.PAID, status,
                UniversityPaymentProvider.STRIPE, "cs_test",
                URI.create("https://checkout.stripe.test/x"), "price_configured", Optional.empty(),
                status == UniversityEntitlementStatus.ACTIVE
                        ? Optional.of("university-report-v1") : Optional.empty(),
                Instant.parse("2026-10-01T11:59:00Z"),
                status == UniversityEntitlementStatus.ACTIVE
                        ? Optional.of(Instant.parse("2026-10-01T12:00:00Z")) : Optional.empty());
    }

    private PurchasedUniversityReportSnapshot frozenSnapshot() {
        UniversityReport report = new UniversityReportAssembler().assemble(
                new com.chikere.futureai.report.university.application.UniversityReportSource(snapshot(),
                        Optional.empty(), Optional.empty()), UniversityReportTier.PAID);
        return new PurchasedUniversityReportSnapshot(ENTITLEMENT, ASSESSMENT, report.reportVersion(),
                Instant.parse("2026-10-01T12:00:00Z"), report);
    }

    private StoredUniversityAssessment snapshot() {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of("Software engineering"),
                Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        return new StoredUniversityAssessment(ASSESSMENT, Instant.parse("2026-10-01T11:58:00Z"), state,
                judgments, new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }
}
