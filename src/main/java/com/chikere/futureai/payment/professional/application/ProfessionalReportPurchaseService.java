package com.chikere.futureai.payment.professional.application;

import com.chikere.futureai.report.professional.application.ProfessionalReport;
import com.chikere.futureai.report.professional.application.ProfessionalReportQuery;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import com.chikere.futureai.configuration.JourneyMetrics;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ProfessionalReportPurchaseService implements ProfessionalReportPurchaseUseCase {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalReportPurchaseService.class);
    private final ProfessionalReportQuery reportService;
    private final ProfessionalPurchaseStore purchaseStore;
    private final Optional<ProfessionalPaymentGateway> paymentGateway;
    private final URI applicationBaseUrl;
    private final Optional<String> displayPrice;
    private final Clock clock;
    private final JourneyMetrics metrics;

    public ProfessionalReportPurchaseService(ProfessionalReportQuery reportService,
            ProfessionalPurchaseStore purchaseStore, Optional<ProfessionalPaymentGateway> paymentGateway,
            URI applicationBaseUrl, Optional<String> displayPrice, Clock clock) {
        this(reportService, purchaseStore, paymentGateway, applicationBaseUrl, displayPrice, clock, JourneyMetrics.disabled());
    }

    public ProfessionalReportPurchaseService(ProfessionalReportQuery reportService,
            ProfessionalPurchaseStore purchaseStore, Optional<ProfessionalPaymentGateway> paymentGateway,
            URI applicationBaseUrl, Optional<String> displayPrice, Clock clock,
            JourneyMetrics metrics) {
        this.reportService = Objects.requireNonNull(reportService);
        this.purchaseStore = Objects.requireNonNull(purchaseStore);
        this.paymentGateway = Objects.requireNonNull(paymentGateway);
        this.applicationBaseUrl = requireHttpBase(applicationBaseUrl);
        this.displayPrice = Objects.requireNonNull(displayPrice).map(String::strip).filter(value -> !value.isEmpty());
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
        this.clock = Objects.requireNonNull(clock);
    }

    /** Calls the external gateway before opening the short pending-entitlement database transaction. */
    @Override
    public ProfessionalCheckoutSession startCheckout(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        ProfessionalPaymentGateway gateway = paymentGateway.orElseThrow(() ->
                new PaymentUnavailableException("paid report checkout is not configured"));
        var existing = purchaseStore.findEntitlementByAssessmentId(assessmentId);
        if (existing.isPresent() && existing.orElseThrow().status() != ProfessionalEntitlementStatus.FAILED) {
            throw new PaymentUnavailableException("a purchase already exists for this assessment");
        }
        ProfessionalReport paidReport = reportService.assemble(assessmentId, ProfessionalReportTier.PAID)
                .orElseThrow(() -> new IllegalArgumentException("assessment does not exist"));
        if (paidReport.measures().size() != 4) {
            throw new PaymentUnavailableException("the assessment is not complete enough for a paid report");
        }
        UUID entitlementId = existing.map(ProfessionalReportEntitlement::entitlementId).orElseGet(UUID::randomUUID);
        ProfessionalPaymentProvider provider = gateway.provider();
        String priceConfigurationKey = gateway.priceConfigurationKey();
        String resultPath = "/professional-assessment/" + assessmentId;
        var request = new ProfessionalCheckoutRequest(entitlementId, assessmentId, ProfessionalReportTier.PAID,
                priceConfigurationKey, applicationBaseUrl.resolve(resultPath + "?checkout=returned"),
                applicationBaseUrl.resolve(resultPath + "?checkout=cancelled"));
        ProfessionalCheckoutSession session;
        try {
            session = metrics.timed("checkout", "professional", () -> gateway.createCheckoutSession(request));
        }
        catch (RuntimeException failure) {
            throw new PaymentUnavailableException("checkout could not be created", failure);
        }
        if (session.provider() != provider) {
            throw new PaymentUnavailableException("payment gateway returned unexpected provenance");
        }
        var pending = new ProfessionalReportEntitlement(entitlementId, assessmentId,
                ProfessionalReportTier.PAID, ProfessionalEntitlementStatus.PENDING, session.provider(),
                session.checkoutSessionId(), session.redirectUrl(), priceConfigurationKey,
                Optional.empty(), Optional.empty(), Instant.now(clock), Optional.empty());
        purchaseStore.savePending(pending);
        return session;
    }

    @Override
    public ProfessionalPaymentProcessingResult processPaymentNotification(ProfessionalPaymentNotification notification) {
        ProfessionalPaymentGateway gateway = paymentGateway.orElseThrow(() ->
                new PaymentUnavailableException("payment notifications are not configured"));
        Optional<ProfessionalPaymentEvent> verified = gateway.verifyPaymentNotification(notification);
        if (verified.isEmpty()) return ProfessionalPaymentProcessingResult.IGNORED;
        ProfessionalPaymentEvent event = verified.orElseThrow();
        try {
            return processVerifiedPaymentEvent(gateway, event);
        }
        catch (InvalidPaymentNotificationException invalid) {
            LOGGER.warn("Professional payment eventId={} sessionId={} assessmentId={} entitlementId={} "
                            + "outcome=REJECTED reason={}", event.eventId(), event.checkoutSessionId(),
                    event.assessmentId(), event.entitlementId(), safeReason(invalid.getMessage()));
            throw invalid;
        }
    }

    private ProfessionalPaymentProcessingResult processVerifiedPaymentEvent(
            ProfessionalPaymentGateway gateway, ProfessionalPaymentEvent event) {
        if (event.provider() != gateway.provider()) {
            throw new InvalidPaymentNotificationException("PROVIDER_MISMATCH");
        }
        if (purchaseStore.isEventProcessed(event.provider(), event.eventId())) {
            LOGGER.info("Professional payment eventId={} sessionId={} assessmentId={} outcome=ALREADY_PROCESSED",
                    event.eventId(), event.checkoutSessionId(), event.assessmentId());
            return ProfessionalPaymentProcessingResult.ALREADY_PROCESSED;
        }
        ProfessionalReportEntitlement entitlement = purchaseStore.findEntitlementById(event.entitlementId())
                .orElseThrow(() -> new InvalidPaymentNotificationException("ENTITLEMENT_NOT_FOUND"));
        validateEvent(entitlement, event);
        PurchasedProfessionalReportSnapshot snapshot = purchaseStore
                .findSnapshotByAssessmentId(event.assessmentId())
                .orElseGet(() -> freezeCurrentPaidReport(entitlement));
        var result = purchaseStore.confirmAndFreeze(event, snapshot);
        if (result == ProfessionalPurchaseStore.PurchaseConfirmationResult.ACTIVATED) {
            metrics.paymentConfirmed("professional");
        }
        LOGGER.info("Professional payment eventId={} sessionId={} assessmentId={} entitlementId={} outcome={}",
                event.eventId(), event.checkoutSessionId(), event.assessmentId(), event.entitlementId(), result);
        return result == ProfessionalPurchaseStore.PurchaseConfirmationResult.ACTIVATED
                ? ProfessionalPaymentProcessingResult.ACTIVATED
                : ProfessionalPaymentProcessingResult.ALREADY_PROCESSED;
    }

    private static String safeReason(String message) {
        return message != null && message.matches("[A-Z_]+") ? message : "INVALID_NOTIFICATION";
    }

    @Override
    public ProfessionalPurchaseAccess accessFor(UUID assessmentId) {
        Optional<ProfessionalReportEntitlement> entitlement =
                purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
        Optional<ProfessionalEntitlementStatus> status = entitlement.map(ProfessionalReportEntitlement::status);
        boolean checkoutAvailable = paymentGateway.isPresent()
                && (status.isEmpty() || status.filter(value -> value == ProfessionalEntitlementStatus.FAILED).isPresent());
        return new ProfessionalPurchaseAccess(status, checkoutAvailable, displayPrice);
    }

    @Override
    public Optional<ProfessionalReport> findPurchasedReport(UUID assessmentId) {
        Optional<ProfessionalReportEntitlement> entitlement =
                purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
        if (entitlement.map(ProfessionalReportEntitlement::status)
                .filter(status -> status == ProfessionalEntitlementStatus.ACTIVE).isEmpty()) {
            return Optional.empty();
        }
        return purchaseStore.findSnapshotByAssessmentId(assessmentId)
                .map(PurchasedProfessionalReportSnapshot::report);
    }

    @Override
    public Optional<ProfessionalReportEntitlement> findEntitlement(UUID assessmentId) {
        return purchaseStore.findEntitlementByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    private PurchasedProfessionalReportSnapshot freezeCurrentPaidReport(ProfessionalReportEntitlement entitlement) {
        ProfessionalReport report = reportService.assemble(entitlement.assessmentId(), ProfessionalReportTier.PAID)
                .orElseThrow(() -> new InvalidPaymentNotificationException("ASSESSMENT_NOT_FOUND"));
        return new PurchasedProfessionalReportSnapshot(entitlement.entitlementId(), entitlement.assessmentId(),
                report.reportVersion(), Instant.now(clock), report);
    }

    private void validateEvent(ProfessionalReportEntitlement entitlement, ProfessionalPaymentEvent event) {
        if (entitlement.paymentProvider() != event.provider()
                || !entitlement.providerCheckoutSessionId().equals(event.checkoutSessionId())
                || !entitlement.assessmentId().equals(event.assessmentId())
                || !entitlement.entitlementId().equals(event.entitlementId())) {
            throw new InvalidPaymentNotificationException("ENTITLEMENT_REFERENCE_MISMATCH");
        }
        if (entitlement.status() != ProfessionalEntitlementStatus.PENDING
                && entitlement.status() != ProfessionalEntitlementStatus.ACTIVE) {
            throw new InvalidPaymentNotificationException("ENTITLEMENT_STATUS_INVALID");
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
