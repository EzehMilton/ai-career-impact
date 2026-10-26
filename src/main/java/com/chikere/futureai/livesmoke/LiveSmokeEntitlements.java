package com.chikere.futureai.livesmoke;

import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseService;
import com.chikere.futureai.payment.complimentary.ComplimentaryRedemptionResult;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseService;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The live-smoke payment bypass. It grants detailed-report access through each journey's ordinary
 * complimentary entitlement path: an ACTIVE COMPLIMENTARY entitlement with the frozen PAID report, exactly
 * what a redeemed access code produces. No payment gateway is involved, so Stripe is never called, no webhook
 * is simulated and no payment-provider record is written. Paid-report authorisation is unchanged: the normal
 * controllers still require an ACTIVE entitlement.
 */
public final class LiveSmokeEntitlements {

    private static final Logger LOGGER = LoggerFactory.getLogger(LiveSmokeEntitlements.class);

    private final ProfessionalReportPurchaseService professional;
    private final UniversityReportPurchaseService university;
    private final ALevelReportPurchaseService aLevel;

    public LiveSmokeEntitlements(ProfessionalReportPurchaseService professional,
            UniversityReportPurchaseService university, ALevelReportPurchaseService aLevel) {
        this.professional = Objects.requireNonNull(professional);
        this.university = Objects.requireNonNull(university);
        this.aLevel = Objects.requireNonNull(aLevel);
    }

    /**
     * Idempotent: an assessment that already has ACTIVE access is left unchanged. A failure is logged and
     * swallowed, so it can never fail the assessment that was just saved; the result page then simply shows
     * no detailed-report access.
     */
    public Optional<ComplimentaryRedemptionResult> grant(LiveSmokeJourney journey, UUID assessmentId) {
        Function<UUID, ComplimentaryRedemptionResult> grant = switch (journey) {
            case PROFESSIONAL -> professional::grantComplimentaryAccess;
            case UNIVERSITY -> university::grantComplimentaryAccess;
            case A_LEVEL -> aLevel::grantComplimentaryAccess;
        };
        try {
            ComplimentaryRedemptionResult result = grant.apply(assessmentId);
            LOGGER.info("LIVE SMOKE detailed-report access journey={} assessmentId={} outcome={} executionMode=LIVE_SMOKE",
                    journey.slug(), assessmentId, result);
            return Optional.of(result);
        }
        catch (RuntimeException failure) {
            LOGGER.warn("LIVE SMOKE detailed-report access could not be granted journey={} assessmentId={} error={}",
                    journey.slug(), assessmentId, failure.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
