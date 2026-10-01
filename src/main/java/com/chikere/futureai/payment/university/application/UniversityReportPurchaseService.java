package com.chikere.futureai.payment.university.application;

import com.chikere.futureai.report.university.application.UniversityReport;
import com.chikere.futureai.report.university.application.UniversityReportService;
import com.chikere.futureai.report.university.application.UniversityReportTier;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Payment grants access to presentation only.
 *
 * <p>It never participates in Jev judgments, the Java decision policy, narrative generation,
 * evidence retrieval or report composition. A university entitlement is entirely separate from a
 * professional one: neither grants access to the other.</p>
 */
public final class UniversityReportPurchaseService {
    private final UniversityReportService reportService;
    private final UniversityPurchaseStore purchaseStore;
    private final Optional<UniversityPaymentGateway> paymentGateway;
    private final URI applicationBaseUrl;
    private final Optional<String> displayPrice;
    private final Clock clock;

    public UniversityReportPurchaseService(UniversityReportService reportService,
            UniversityPurchaseStore purchaseStore, Optional<UniversityPaymentGateway> paymentGateway,
            URI applicationBaseUrl, Optional<String> displayPrice, Clock clock) {
        this.reportService = Objects.requireNonNull(reportService);
        this.purchaseStore = Objects.requireNonNull(purchaseStore);
        this.paymentGateway = Objects.requireNonNull(paymentGateway);
        this.applicationBaseUrl = requireHttpBase(applicationBaseUrl);
        this.displayPrice = Objects.requireNonNull(displayPrice)
                .map(String::strip).filter(value -> !value.isEmpty());
        this.clock = Objects.requireNonNull(clock);
    }

    /** Calls the external gateway before opening the short pending-entitlement database transaction. */
    public UniversityCheckoutSession startCheckout(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        UniversityPaymentGateway gateway = paymentGateway.orElseThrow(() ->
                new PaymentUnavailableException("paid report checkout is not configured"));
        var existing = purchaseStore.findEntitlementByAssessmentId(assessmentId);
        if (existing.isPresent() && existing.orElseThrow().status() != UniversityEntitlementStatus.FAILED) {
            throw new PaymentUnavailableException("a purchase already exists for this assessment");
        }
        UniversityReport paidReport = reportService.assemble(assessmentId, UniversityReportTier.PAID)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        if (paidReport.measures().size() != 5) {
            throw new PaymentUnavailableException("the assessment is not complete enough for a paid report");
        }
        UUID entitlementId = existing.map(UniversityReportEntitlement::entitlementId).orElseGet(UUID::randomUUID);
        UniversityPaymentProvider provider = gateway.provider();
        String priceConfigurationKey = gateway.priceConfigurationKey();
        String resultPath = "/university-assessment/" + assessmentId;
        var request = new UniversityCheckoutRequest(entitlementId, assessmentId, UniversityReportTier.PAID,
                priceConfigurationKey, applicationBaseUrl.resolve(resultPath + "?checkout=returned"),
                applicationBaseUrl.resolve(resultPath + "?checkout=cancelled"));
        UniversityCheckoutSession session;
        try {
            session = gateway.createCheckoutSession(request);
        }
        catch (RuntimeException failure) {
            throw new PaymentUnavailableException("checkout could not be created", failure);
        }
        if (session.provider() != provider) {
            throw new PaymentUnavailableException("payment gateway returned unexpected provenance");
        }
        var pending = new UniversityReportEntitlement(entitlementId, assessmentId,
                UniversityReportTier.PAID, UniversityEntitlementStatus.PENDING, session.provider(),
                session.checkoutSessionId(), session.redirectUrl(), priceConfigurationKey,
                Optional.empty(), Optional.empty(), Instant.now(clock), Optional.empty());
        purchaseStore.savePending(pending);
        return session;
    }

    public UniversityPaymentProcessingResult processPaymentNotification(
            UniversityPaymentNotification notification) {
        UniversityPaymentGateway gateway = paymentGateway.orElseThrow(() ->
                new PaymentUnavailableException("payment notifications are not configured"));
        Optional<UniversityPaymentEvent> verified = gateway.verifyPaymentNotification(notification);
        if (verified.isEmpty()) {
            return UniversityPaymentProcessingResult.IGNORED;
        }
        UniversityPaymentEvent event = verified.orElseThrow();
        if (event.provider() != gateway.provider()) {
            throw new InvalidPaymentNotificationException(
                    "payment event provider does not match configured gateway");
        }
        if (purchaseStore.isEventProcessed(event.provider(), event.eventId())) {
            return UniversityPaymentProcessingResult.ALREADY_PROCESSED;
        }
        UniversityReportEntitlement entitlement = purchaseStore.findEntitlementById(event.entitlementId())
                .orElseThrow(() -> new InvalidPaymentNotificationException(
                        "payment event has no matching entitlement"));
        validateEvent(entitlement, event);
        PurchasedUniversityReportSnapshot snapshot = purchaseStore
                .findSnapshotByAssessmentId(event.assessmentId())
                .orElseGet(() -> freezeCurrentPaidReport(entitlement));
        var result = purchaseStore.confirmAndFreeze(event, snapshot);
        return result == UniversityPurchaseStore.PurchaseConfirmationResult.ACTIVATED
                ? UniversityPaymentProcessingResult.ACTIVATED
                : UniversityPaymentProcessingResult.ALREADY_PROCESSED;
    }

    public UniversityPurchaseAccess accessFor(UUID assessmentId) {
        Optional<UniversityReportEntitlement> entitlement =
                purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
        Optional<UniversityEntitlementStatus> status = entitlement.map(UniversityReportEntitlement::status);
        boolean checkoutAvailable = paymentGateway.isPresent()
                && (status.isEmpty()
                || status.filter(value -> value == UniversityEntitlementStatus.FAILED).isPresent());
        return new UniversityPurchaseAccess(status, checkoutAvailable, displayPrice);
    }

    /** Only an ACTIVE entitlement returns the frozen purchased report. */
    public Optional<UniversityReport> findPurchasedReport(UUID assessmentId) {
        Optional<UniversityReportEntitlement> entitlement =
                purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
        if (entitlement.map(UniversityReportEntitlement::status)
                .filter(status -> status == UniversityEntitlementStatus.ACTIVE).isEmpty()) {
            return Optional.empty();
        }
        return purchaseStore.findSnapshotByAssessmentId(assessmentId)
                .map(PurchasedUniversityReportSnapshot::report);
    }

    public Optional<UniversityReportEntitlement> findEntitlement(UUID assessmentId) {
        return purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    private PurchasedUniversityReportSnapshot freezeCurrentPaidReport(UniversityReportEntitlement entitlement) {
        UniversityReport report = reportService.assemble(entitlement.assessmentId(), UniversityReportTier.PAID)
                .orElseThrow(() -> new InvalidPaymentNotificationException("assessment no longer exists"));
        return new PurchasedUniversityReportSnapshot(entitlement.entitlementId(), entitlement.assessmentId(),
                report.reportVersion(), Instant.now(clock), report);
    }

    private void validateEvent(UniversityReportEntitlement entitlement, UniversityPaymentEvent event) {
        if (entitlement.paymentProvider() != event.provider()
                || !entitlement.providerCheckoutSessionId().equals(event.checkoutSessionId())
                || !entitlement.assessmentId().equals(event.assessmentId())
                || !entitlement.entitlementId().equals(event.entitlementId())) {
            throw new InvalidPaymentNotificationException(
                    "payment event does not match the pending entitlement");
        }
        if (entitlement.status() != UniversityEntitlementStatus.PENDING
                && entitlement.status() != UniversityEntitlementStatus.ACTIVE) {
            throw new InvalidPaymentNotificationException(
                    "entitlement cannot be activated from its current status");
        }
    }

    private static URI requireHttpBase(URI value) {
        Objects.requireNonNull(value);
        if (!value.isAbsolute() || value.getAuthority() == null
                || !("http".equalsIgnoreCase(value.getScheme()) || "https".equalsIgnoreCase(value.getScheme()))) {
            throw new IllegalArgumentException("applicationBaseUrl must be an absolute HTTP(S) URL");
        }
        String text = value.toString();
        return URI.create(text.endsWith("/") ? text : text + "/");
    }
}
