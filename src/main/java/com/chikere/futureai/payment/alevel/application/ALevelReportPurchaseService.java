package com.chikere.futureai.payment.alevel.application;

import com.chikere.futureai.report.alevel.application.ALevelReport;
import com.chikere.futureai.report.alevel.application.ALevelReportQuery;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;

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
 * evidence retrieval or report composition. An A-level entitlement is entirely separate from a
 * professional or university one: none of them grants access to another.</p>
 */
public final class ALevelReportPurchaseService implements ALevelReportPurchaseUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelReportPurchaseService.class);
    private final ALevelReportQuery reportService;
    private final ALevelPurchaseStore purchaseStore;
    private final Optional<ALevelPaymentGateway> paymentGateway;
    private final URI applicationBaseUrl;
    private final Optional<String> displayPrice;
    private final Clock clock;
    private final JourneyMetrics metrics;
    private final ComplimentaryReportAccess complimentaryAccess;

    public ALevelReportPurchaseService(ALevelReportQuery reportService,
            ALevelPurchaseStore purchaseStore, Optional<ALevelPaymentGateway> paymentGateway,
            URI applicationBaseUrl, Optional<String> displayPrice, Clock clock) {
        this(reportService, purchaseStore, paymentGateway, applicationBaseUrl, displayPrice, clock, JourneyMetrics.disabled());
    }

    public ALevelReportPurchaseService(ALevelReportQuery reportService,
            ALevelPurchaseStore purchaseStore, Optional<ALevelPaymentGateway> paymentGateway,
            URI applicationBaseUrl, Optional<String> displayPrice, Clock clock,
            JourneyMetrics metrics) {
        this(reportService, purchaseStore, paymentGateway, applicationBaseUrl, displayPrice, clock, metrics,
                ComplimentaryReportAccess.disabled());
    }

    public ALevelReportPurchaseService(ALevelReportQuery reportService,
            ALevelPurchaseStore purchaseStore, Optional<ALevelPaymentGateway> paymentGateway,
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
    public ALevelCheckoutSession startCheckout(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        ALevelPaymentGateway gateway = paymentGateway.orElseThrow(() ->
                new PaymentUnavailableException("paid report checkout is not configured"));
        var existing = purchaseStore.findEntitlementByAssessmentId(assessmentId);
        if (existing.isPresent() && existing.orElseThrow().status() == ALevelEntitlementStatus.PENDING) {
            return resumePendingCheckout(gateway, existing.orElseThrow());
        }
        if (existing.isPresent() && existing.orElseThrow().status() != ALevelEntitlementStatus.FAILED) {
            throw new PaymentUnavailableException("a purchase already exists for this assessment");
        }
        ALevelReport paidReport = reportService.assemble(assessmentId, ALevelReportTier.PAID)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        if (paidReport.measures().size() != 6) {
            throw new PaymentUnavailableException("the assessment is not complete enough for a paid report");
        }
        UUID entitlementId = existing.map(ALevelReportEntitlement::entitlementId).orElseGet(UUID::randomUUID);
        ALevelPaymentProvider provider = gateway.provider();
        String priceConfigurationKey = gateway.priceConfigurationKey();
        String resultPath = "/a-level-assessment/" + assessmentId;
        var request = new ALevelCheckoutRequest(entitlementId, assessmentId, ALevelReportTier.PAID,
                priceConfigurationKey, applicationBaseUrl.resolve(resultPath + "?checkout=returned"),
                applicationBaseUrl.resolve(resultPath + "?checkout=cancelled"));
        ALevelCheckoutSession session;
        try {
            session = metrics.timed("checkout", "a-level", () -> gateway.createCheckoutSession(request));
        }
        catch (RuntimeException failure) {
            throw new PaymentUnavailableException("checkout could not be created", failure);
        }
        if (session.provider() != provider) {
            throw new PaymentUnavailableException("payment gateway returned unexpected provenance");
        }
        var pending = new ALevelReportEntitlement(entitlementId, assessmentId, ALevelReportTier.PAID,
                ALevelEntitlementStatus.PENDING, session.provider(), session.checkoutSessionId(),
                session.redirectUrl(), priceConfigurationKey, Optional.empty(), Optional.empty(),
                Instant.now(clock), Optional.empty());
        purchaseStore.savePending(pending);
        return session;
    }

    /**
     * A PENDING purchase is either a checkout the visitor left (Stripe's cancel link, a closed tab)
     * or one that has been paid and is waiting for its webhook. The browser cannot tell these apart,
     * so the provider's own record of the session decides. The entitlement status is never changed
     * here: only the verified webhook moves it to ACTIVE.
     */
    private ALevelCheckoutSession resumePendingCheckout(ALevelPaymentGateway gateway,
            ALevelReportEntitlement pending) {
        if (pending.paymentProvider() != gateway.provider()) {
            throw new PaymentUnavailableException("the pending checkout belongs to another payment provider");
        }
        ALevelCheckoutSessionState state;
        try {
            state = gateway.checkoutSessionState(pending.providerCheckoutSessionId());
        }
        catch (RuntimeException failure) {
            throw new PaymentUnavailableException("the pending checkout could not be checked", failure);
        }
        return switch (state) {
            // Still payable: back to the same session, which can be paid at most once.
            case OPEN -> new ALevelCheckoutSession(pending.providerCheckoutSessionId(), pending.checkoutUrl(),
                    pending.paymentProvider(), Optional.empty());
            // Already paid: never open a second payment while the webhook is on its way.
            case COMPLETE -> throw new CheckoutAwaitingConfirmationException();
            // Can no longer be paid, so a fresh session for the same entitlement cannot double-charge.
            case EXPIRED -> replaceExpiredCheckout(gateway, pending);
        };
    }

    private ALevelCheckoutSession replaceExpiredCheckout(ALevelPaymentGateway gateway,
            ALevelReportEntitlement pending) {
        String priceConfigurationKey = gateway.priceConfigurationKey();
        String resultPath = "/a-level-assessment/" + pending.assessmentId();
        var request = new ALevelCheckoutRequest(pending.entitlementId(), pending.assessmentId(),
                ALevelReportTier.PAID, priceConfigurationKey,
                applicationBaseUrl.resolve(resultPath + "?checkout=returned"),
                applicationBaseUrl.resolve(resultPath + "?checkout=cancelled"),
                Optional.of(pending.providerCheckoutSessionId()));
        ALevelCheckoutSession session;
        try {
            session = metrics.timed("checkout", "a-level", () -> gateway.createCheckoutSession(request));
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
    public ALevelPaymentProcessingResult processPaymentNotification(ALevelPaymentNotification notification) {
        ALevelPaymentGateway gateway = paymentGateway.orElseThrow(() ->
                new PaymentUnavailableException("payment notifications are not configured"));
        Optional<ALevelPaymentEvent> verified = gateway.verifyPaymentNotification(notification);
        if (verified.isEmpty()) {
            return ALevelPaymentProcessingResult.IGNORED;
        }
        ALevelPaymentEvent event = verified.orElseThrow();
        if (event.provider() != gateway.provider()) {
            throw new InvalidPaymentNotificationException(
                    "payment event provider does not match configured gateway");
        }
        if (purchaseStore.isEventProcessed(event.provider(), event.eventId())) {
            LOGGER.info("A-level payment eventId={} sessionId={} assessmentId={} outcome=ALREADY_PROCESSED",
                    event.eventId(), event.checkoutSessionId(), event.assessmentId());
            return ALevelPaymentProcessingResult.ALREADY_PROCESSED;
        }
        ALevelReportEntitlement entitlement = purchaseStore.findEntitlementById(event.entitlementId())
                .orElseThrow(() -> new InvalidPaymentNotificationException(
                        "payment event has no matching entitlement"));
        validateEvent(entitlement, event);
        PurchasedALevelReportSnapshot snapshot = purchaseStore
                .findSnapshotByAssessmentId(event.assessmentId())
                .orElseGet(() -> freezeCurrentPaidReport(entitlement));
        var result = purchaseStore.confirmAndFreeze(event, snapshot);
        if (result == ALevelPurchaseStore.PurchaseConfirmationResult.ACTIVATED) {
            metrics.paymentConfirmed("a-level");
        }
        LOGGER.info("A-level payment eventId={} sessionId={} assessmentId={} entitlementId={} outcome={}",
                event.eventId(), event.checkoutSessionId(), event.assessmentId(), event.entitlementId(), result);
        return result == ALevelPurchaseStore.PurchaseConfirmationResult.ACTIVATED
                ? ALevelPaymentProcessingResult.ACTIVATED
                : ALevelPaymentProcessingResult.ALREADY_PROCESSED;
    }

    @Override
    public ALevelPurchaseAccess accessFor(UUID assessmentId) {
        Optional<ALevelReportEntitlement> entitlement =
                purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
        Optional<ALevelEntitlementStatus> status = entitlement.map(ALevelReportEntitlement::status);
        boolean checkoutAvailable = paymentGateway.isPresent()
                && (status.isEmpty()
                || status.filter(value -> value == ALevelEntitlementStatus.FAILED).isPresent());
        boolean complimentaryAvailable = complimentaryAccess.enabled() && status.isEmpty();
        return new ALevelPurchaseAccess(status, checkoutAvailable, displayPrice, complimentaryAvailable);
    }

    /** Only an ACTIVE entitlement returns the frozen purchased report. */
    @Override
    public Optional<ALevelReport> findPurchasedReport(UUID assessmentId) {
        Optional<ALevelReportEntitlement> entitlement =
                purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
        if (entitlement.map(ALevelReportEntitlement::status)
                .filter(status -> status == ALevelEntitlementStatus.ACTIVE).isEmpty()) {
            return Optional.empty();
        }
        return purchaseStore.findSnapshotByAssessmentId(assessmentId)
                .map(PurchasedALevelReportSnapshot::report);
    }

    @Override
    public Optional<ALevelReportEntitlement> findEntitlement(UUID assessmentId) {
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
        LOGGER.info("A-level complimentary access assessmentId={} outcome={}", assessmentId, result);
        return result;
    }

    private ComplimentaryRedemptionResult redeem(UUID assessmentId, String submittedCode) {
        if (!complimentaryAccess.accepts(submittedCode)) return ComplimentaryRedemptionResult.INVALID_CODE;
        var existing = purchaseStore.findEntitlementByAssessmentId(assessmentId);
        if (existing.isPresent()) return redemptionFor(existing.orElseThrow());
        ALevelReport paidReport = reportService.assemble(assessmentId, ALevelReportTier.PAID)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        if (paidReport.measures().size() != 6) return ComplimentaryRedemptionResult.NOT_ELIGIBLE;
        UUID entitlementId = UUID.randomUUID();
        Instant now = Instant.now(clock);
        // The checkout columns are required by the payment schema. These values name the grant
        // rather than a provider session, and no gateway is ever asked about them.
        var entitlement = new ALevelReportEntitlement(entitlementId, assessmentId, ALevelReportTier.PAID,
                ALevelEntitlementStatus.ACTIVE, ALevelPaymentProvider.COMPLIMENTARY,
                "complimentary_" + entitlementId,
                applicationBaseUrl.resolve("/a-level-assessment/" + assessmentId + "/report"), "complimentary",
                Optional.empty(), Optional.of(paidReport.reportVersion()), now, Optional.of(now));
        var snapshot = new PurchasedALevelReportSnapshot(entitlementId, assessmentId,
                paidReport.reportVersion(), now, paidReport);
        try {
            purchaseStore.saveComplimentary(entitlement, snapshot);
        }
        catch (RuntimeException failure) {
            // A simultaneous redemption or checkout for the same assessment took the one row first.
            return purchaseStore.findEntitlementByAssessmentId(assessmentId)
                    .map(ALevelReportPurchaseService::redemptionFor)
                    .orElseThrow(() -> failure);
        }
        return ComplimentaryRedemptionResult.UNLOCKED;
    }

    private static ComplimentaryRedemptionResult redemptionFor(ALevelReportEntitlement existing) {
        return existing.status() == ALevelEntitlementStatus.ACTIVE
                ? ComplimentaryRedemptionResult.ALREADY_ACTIVE
                : ComplimentaryRedemptionResult.NOT_ELIGIBLE;
    }

    private PurchasedALevelReportSnapshot freezeCurrentPaidReport(ALevelReportEntitlement entitlement) {
        ALevelReport report = reportService.assemble(entitlement.assessmentId(), ALevelReportTier.PAID)
                .orElseThrow(() -> new InvalidPaymentNotificationException("assessment no longer exists"));
        return new PurchasedALevelReportSnapshot(entitlement.entitlementId(), entitlement.assessmentId(),
                report.reportVersion(), Instant.now(clock), report);
    }

    private void validateEvent(ALevelReportEntitlement entitlement, ALevelPaymentEvent event) {
        if (entitlement.paymentProvider() != event.provider()
                || !entitlement.providerCheckoutSessionId().equals(event.checkoutSessionId())
                || !entitlement.assessmentId().equals(event.assessmentId())
                || !entitlement.entitlementId().equals(event.entitlementId())) {
            throw new InvalidPaymentNotificationException(
                    "payment event does not match the pending entitlement");
        }
        if (entitlement.status() != ALevelEntitlementStatus.PENDING
                && entitlement.status() != ALevelEntitlementStatus.ACTIVE) {
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
