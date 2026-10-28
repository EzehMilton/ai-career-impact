package com.chikere.futureai.payment.university.application;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.report.university.application.UniversityReport;
import com.chikere.futureai.report.university.application.UniversityReportQuery;
import com.chikere.futureai.report.university.application.UniversityReportTier;

import com.chikere.futureai.payment.complimentary.ComplimentaryRedemptionResult;
import com.chikere.futureai.payment.complimentary.ComplimentaryReportAccess;

import java.net.URI;
import com.chikere.futureai.configuration.JourneyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
public final class UniversityReportPurchaseService implements UniversityReportPurchaseUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityReportPurchaseService.class);
    private final UniversityReportQuery reportService;
    private final UniversityPurchaseStore purchaseStore;
    private final Optional<UniversityPaymentGateway> paymentGateway;
    private final URI applicationBaseUrl;
    private final Optional<String> displayPrice;
    private final Clock clock;
    private final JourneyMetrics metrics;
    private final ComplimentaryReportAccess complimentaryAccess;

    public UniversityReportPurchaseService(UniversityReportQuery reportService,
            UniversityPurchaseStore purchaseStore, Optional<UniversityPaymentGateway> paymentGateway,
            URI applicationBaseUrl, Optional<String> displayPrice, Clock clock) {
        this(reportService, purchaseStore, paymentGateway, applicationBaseUrl, displayPrice, clock, JourneyMetrics.disabled());
    }

    public UniversityReportPurchaseService(UniversityReportQuery reportService,
            UniversityPurchaseStore purchaseStore, Optional<UniversityPaymentGateway> paymentGateway,
            URI applicationBaseUrl, Optional<String> displayPrice, Clock clock,
            JourneyMetrics metrics) {
        this(reportService, purchaseStore, paymentGateway, applicationBaseUrl, displayPrice, clock, metrics,
                ComplimentaryReportAccess.disabled());
    }

    public UniversityReportPurchaseService(UniversityReportQuery reportService,
            UniversityPurchaseStore purchaseStore, Optional<UniversityPaymentGateway> paymentGateway,
            URI applicationBaseUrl, Optional<String> displayPrice, Clock clock,
            JourneyMetrics metrics, ComplimentaryReportAccess complimentaryAccess) {
        this.reportService = Objects.requireNonNull(reportService);
        this.purchaseStore = Objects.requireNonNull(purchaseStore);
        this.paymentGateway = Objects.requireNonNull(paymentGateway);
        this.applicationBaseUrl = requireHttpBase(applicationBaseUrl);
        this.displayPrice = Objects.requireNonNull(displayPrice)
                .map(String::strip).filter(value -> !value.isEmpty());
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock);
        this.complimentaryAccess = Objects.requireNonNull(complimentaryAccess);
    }

    /** Calls the external gateway before opening the short pending-entitlement database transaction. */
    @Override
    public UniversityCheckoutSession startCheckout(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        UniversityPaymentGateway gateway = paymentGateway.orElseThrow(() ->
                new PaymentUnavailableException("paid report checkout is not configured"));
        var existing = purchaseStore.findEntitlementByAssessmentId(assessmentId);
        if (existing.isPresent() && existing.orElseThrow().status() == UniversityEntitlementStatus.PENDING) {
            return resumePendingCheckout(gateway, existing.orElseThrow());
        }
        if (existing.isPresent() && existing.orElseThrow().status() != UniversityEntitlementStatus.FAILED) {
            throw new PaymentUnavailableException("a purchase already exists for this assessment");
        }
        if (!eligible(assessmentId)) {
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
            session = metrics.timed("checkout", "university", () -> gateway.createCheckoutSession(request));
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

    /**
     * A PENDING purchase is either a checkout the visitor left (Stripe's cancel link, a closed tab)
     * or one that has been paid and is waiting for its webhook. The browser cannot tell these apart,
     * so the provider's own record of the session decides. The entitlement status is never changed
     * here: only the verified webhook moves it to ACTIVE.
     */
    private UniversityCheckoutSession resumePendingCheckout(UniversityPaymentGateway gateway,
            UniversityReportEntitlement pending) {
        if (pending.paymentProvider() != gateway.provider()) {
            throw new PaymentUnavailableException("the pending checkout belongs to another payment provider");
        }
        UniversityCheckoutSessionState state;
        try {
            state = gateway.checkoutSessionState(pending.providerCheckoutSessionId());
        }
        catch (RuntimeException failure) {
            throw new PaymentUnavailableException("the pending checkout could not be checked", failure);
        }
        return switch (state) {
            // Still payable: back to the same session, which can be paid at most once.
            case OPEN -> new UniversityCheckoutSession(pending.providerCheckoutSessionId(), pending.checkoutUrl(),
                    pending.paymentProvider(), Optional.empty());
            // Already paid: never open a second payment while the webhook is on its way.
            case COMPLETE -> throw new CheckoutAwaitingConfirmationException();
            // Can no longer be paid, so a fresh session for the same entitlement cannot double-charge.
            case EXPIRED -> replaceExpiredCheckout(gateway, pending);
        };
    }

    private UniversityCheckoutSession replaceExpiredCheckout(UniversityPaymentGateway gateway,
            UniversityReportEntitlement pending) {
        String priceConfigurationKey = gateway.priceConfigurationKey();
        String resultPath = "/university-assessment/" + pending.assessmentId();
        var request = new UniversityCheckoutRequest(pending.entitlementId(), pending.assessmentId(),
                UniversityReportTier.PAID, priceConfigurationKey,
                applicationBaseUrl.resolve(resultPath + "?checkout=returned"),
                applicationBaseUrl.resolve(resultPath + "?checkout=cancelled"),
                Optional.of(pending.providerCheckoutSessionId()));
        UniversityCheckoutSession session;
        try {
            session = metrics.timed("checkout", "university", () -> gateway.createCheckoutSession(request));
        }
        catch (RuntimeException failure) {
            throw new PaymentUnavailableException("checkout could not be created", failure);
        }
        if (session.provider() != pending.paymentProvider()) {
            throw new PaymentUnavailableException("payment gateway returned unexpected provenance");
        }
        purchaseStore.replaceExpiredCheckout(pending.entitlementId(), pending.providerCheckoutSessionId(),
                session, priceConfigurationKey, Instant.now(clock));
        return session;
    }

    @Override
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
            LOGGER.info("University payment eventId={} sessionId={} assessmentId={} outcome=ALREADY_PROCESSED",
                    event.eventId(), event.checkoutSessionId(), event.assessmentId());
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
        if (result == UniversityPurchaseStore.PurchaseConfirmationResult.ACTIVATED) {
            metrics.paymentConfirmed("university");
        }
        LOGGER.info("University payment eventId={} sessionId={} assessmentId={} entitlementId={} outcome={}",
                event.eventId(), event.checkoutSessionId(), event.assessmentId(), event.entitlementId(), result);
        return result == UniversityPurchaseStore.PurchaseConfirmationResult.ACTIVATED
                ? UniversityPaymentProcessingResult.ACTIVATED
                : UniversityPaymentProcessingResult.ALREADY_PROCESSED;
    }

    @Override
    public UniversityPurchaseAccess accessFor(UUID assessmentId) {
        Optional<UniversityReportEntitlement> entitlement =
                purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
        Optional<UniversityEntitlementStatus> status = entitlement.map(UniversityReportEntitlement::status);
        boolean checkoutAvailable = paymentGateway.isPresent()
                && (status.isEmpty()
                || status.filter(value -> value == UniversityEntitlementStatus.FAILED).isPresent());
        boolean complimentaryAvailable = complimentaryAccess.enabled() && status.isEmpty();
        return new UniversityPurchaseAccess(status, checkoutAvailable, displayPrice, complimentaryAvailable);
    }

    /** Only an ACTIVE entitlement returns the frozen purchased report. */
    @Override
    public Optional<UniversityReport> findPurchasedReport(UUID assessmentId) {
        Optional<UniversityReportEntitlement> entitlement =
                purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
        if (entitlement.map(UniversityReportEntitlement::status)
                .filter(status -> status == UniversityEntitlementStatus.ACTIVE).isEmpty()) {
            return Optional.empty();
        }
        return purchaseStore.findSnapshotByAssessmentId(assessmentId)
                .flatMap(PurchasedUniversityReportSnapshot::purchasedReport);
    }

    @Override
    public Optional<String> activePurchaseVersion(UUID assessmentId) {
        Optional<UniversityReportEntitlement> entitlement =
                purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
        if (entitlement.map(UniversityReportEntitlement::status)
                .filter(status -> status == UniversityEntitlementStatus.ACTIVE).isEmpty()) {
            return Optional.empty();
        }
        return purchaseStore.findSnapshotByAssessmentId(assessmentId)
                .map(PurchasedUniversityReportSnapshot::reportVersion);
    }

    @Override
    public Optional<UniversityReportEntitlement> findEntitlement(UUID assessmentId) {
        return purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    /**
     * Grants the same frozen PAID report a confirmed payment produces, with no payment gateway
     * involved. Only an assessment without any entitlement is unlocked: an ACTIVE one already has
     * the report, and a PENDING, REFUNDED or REVOKED one belongs to the payment flow, which a code
     * must never override. The submitted code is compared and then discarded; it is never logged.
     */
    @Override
    public ComplimentaryRedemptionResult redeemComplimentaryAccess(UUID assessmentId, String submittedCode) {
        Objects.requireNonNull(assessmentId);
        if (!complimentaryAccess.enabled()) return ComplimentaryRedemptionResult.DISABLED;
        ComplimentaryRedemptionResult result = redeem(assessmentId, submittedCode);
        LOGGER.info("University complimentary access assessmentId={} outcome={}", assessmentId, result);
        return result;
    }

    /**
     * The same complimentary grant as a redeemed code, without a code: the frozen PAID report and an ACTIVE
     * COMPLIMENTARY entitlement, never a payment-provider record. Idempotent: an assessment that already has
     * ACTIVE access is left as it is. For trusted in-process callers only, such as the live-smoke entitlement
     * adapter, which exists only under that profile; it is deliberately not part of the web-facing use case.
     */
    public ComplimentaryRedemptionResult grantComplimentaryAccess(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        ComplimentaryRedemptionResult result = grant(assessmentId);
        LOGGER.info("University complimentary access granted without a code assessmentId={} outcome={}",
                assessmentId, result);
        return result;
    }

    private ComplimentaryRedemptionResult redeem(UUID assessmentId, String submittedCode) {
        if (!complimentaryAccess.accepts(submittedCode)) return ComplimentaryRedemptionResult.INVALID_CODE;
        return grant(assessmentId);
    }

    private ComplimentaryRedemptionResult grant(UUID assessmentId) {
        var existing = purchaseStore.findEntitlementByAssessmentId(assessmentId);
        if (existing.isPresent()) return redemptionFor(existing.orElseThrow());
        if (!eligible(assessmentId)) return ComplimentaryRedemptionResult.NOT_ELIGIBLE;
        UUID entitlementId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        PurchasedUniversityReportSnapshot snapshot = freeze(entitlementId, assessmentId, now);
        // The checkout columns are required by the payment schema. These values name the grant
        // rather than a provider session, and no gateway is ever asked about them.
        var entitlement = new UniversityReportEntitlement(entitlementId, assessmentId, UniversityReportTier.PAID,
                UniversityEntitlementStatus.ACTIVE, UniversityPaymentProvider.COMPLIMENTARY,
                "complimentary_" + entitlementId,
                applicationBaseUrl.resolve("/university-assessment/" + assessmentId + "/report"), "complimentary",
                Optional.empty(), Optional.of(snapshot.reportVersion()), now, Optional.of(now));
        try {
            purchaseStore.saveComplimentary(entitlement, snapshot);
        }
        catch (RuntimeException failure) {
            // A simultaneous redemption or checkout for the same assessment took the one row first.
            return purchaseStore.findEntitlementByAssessmentId(assessmentId)
                    .map(UniversityReportPurchaseService::redemptionFor)
                    .orElseThrow(() -> failure);
        }
        return ComplimentaryRedemptionResult.UNLOCKED;
    }

    private static ComplimentaryRedemptionResult redemptionFor(UniversityReportEntitlement existing) {
        return existing.status() == UniversityEntitlementStatus.ACTIVE
                ? ComplimentaryRedemptionResult.ALREADY_ACTIVE
                : ComplimentaryRedemptionResult.NOT_ELIGIBLE;
    }

    private PurchasedUniversityReportSnapshot freezeCurrentPaidReport(UniversityReportEntitlement entitlement) {
        return freeze(entitlement.entitlementId(), entitlement.assessmentId(), Instant.now(clock));
    }

    /**
     * Routed by the assessment's stored decision version, never the current application version: a v1 assessment
     * freezes its university-report-v1 document; a v2 course profile records a university-report-v2 purchase.
     */
    private PurchasedUniversityReportSnapshot freeze(UUID entitlementId, UUID assessmentId, Instant at) {
        if (reportService.stored(assessmentId).map(stored -> stored.result())
                .filter(UniversityAssessmentResult::usesCourseProfile).isPresent()) {
            return PurchasedUniversityReportSnapshot.courseProfile(entitlementId, assessmentId, at);
        }
        UniversityReport report = reportService.assemble(assessmentId, UniversityReportTier.PAID)
                .orElseThrow(() -> new InvalidPaymentNotificationException("assessment no longer exists"));
        return new PurchasedUniversityReportSnapshot(entitlementId, assessmentId, report.reportVersion(), at, report);
    }

    /** A v2 course profile is eligible unless more information was required; a v1 report needs all five measures. */
    private boolean eligible(UUID assessmentId) {
        var result = reportService.stored(assessmentId).map(stored -> stored.result());
        if (result.filter(UniversityAssessmentResult::usesCourseProfile).isPresent()) {
            return result.get().headline().status() != AssessmentStatus.MORE_INFORMATION_REQUIRED;
        }
        UniversityReport paidReport = reportService.assemble(assessmentId, UniversityReportTier.PAID)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        return paidReport.measures().size() == 5;
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
