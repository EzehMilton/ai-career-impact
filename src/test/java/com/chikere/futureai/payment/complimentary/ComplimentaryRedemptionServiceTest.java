package com.chikere.futureai.payment.complimentary;

import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.report.professional.application.*;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** The service rules behind every journey's redemption, shown on the professional one. */
class ComplimentaryRedemptionServiceTest {
    private static final String CODE = "shared-code";
    private static final UUID ASSESSMENT = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T09:00:00Z"), ZoneOffset.UTC);

    private final ProfessionalPurchaseStore store = mock(ProfessionalPurchaseStore.class);
    private final ProfessionalReportQuery reports = mock(ProfessionalReportQuery.class);
    private final ProfessionalPaymentGateway stripe = mock(ProfessionalPaymentGateway.class);

    private ProfessionalReportPurchaseService service(ComplimentaryReportAccess access) {
        return new ProfessionalReportPurchaseService(reports, store, Optional.of(stripe),
                URI.create("https://ai-career-impact.example"), Optional.of("£4.99"), CLOCK,
                JourneyMetrics.disabled(), access);
    }

    @Test
    void aValidCodeFreezesThePaidReportAsAnActiveComplimentaryEntitlement() {
        var paid = new ProfessionalReportAssembler().assemble(
                ProfessionalReportAssemblerTest.source(true, true, true), ProfessionalReportTier.PAID);
        when(reports.assemble(ASSESSMENT, ProfessionalReportTier.PAID)).thenReturn(Optional.of(paid));

        assertThat(service(ComplimentaryReportAccess.enabledWith(CODE)).redeemComplimentaryAccess(ASSESSMENT, CODE))
                .isEqualTo(ComplimentaryRedemptionResult.UNLOCKED);

        verify(store).saveComplimentary(argThat(entitlement -> entitlement.status() == ProfessionalEntitlementStatus.ACTIVE
                        && entitlement.paymentProvider() == ProfessionalPaymentProvider.COMPLIMENTARY
                        && entitlement.assessmentId().equals(ASSESSMENT)
                        && entitlement.confirmedAt().equals(Optional.of(CLOCK.instant()))),
                argThat(snapshot -> snapshot.report().equals(paid)));
        verifyNoInteractions(stripe);
    }

    @Test
    void losingARaceToAnotherRedemptionStillSendsTheVisitorToTheReport() {
        var paid = new ProfessionalReportAssembler().assemble(
                ProfessionalReportAssemblerTest.source(true, true, true), ProfessionalReportTier.PAID);
        UUID winnerId = UUID.randomUUID();
        var winner = new ProfessionalReportEntitlement(winnerId, ASSESSMENT, ProfessionalReportTier.PAID,
                ProfessionalEntitlementStatus.ACTIVE, ProfessionalPaymentProvider.COMPLIMENTARY,
                "complimentary_" + winnerId, URI.create("https://ai-career-impact.example/report"), "complimentary",
                Optional.empty(), Optional.of(paid.reportVersion()), CLOCK.instant(), Optional.of(CLOCK.instant()));
        when(store.findEntitlementByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty(), Optional.of(winner));
        when(reports.assemble(ASSESSMENT, ProfessionalReportTier.PAID)).thenReturn(Optional.of(paid));
        doThrow(new IllegalStateException("unique assessment_id")).when(store).saveComplimentary(any(), any());

        assertThat(service(ComplimentaryReportAccess.enabledWith(CODE)).redeemComplimentaryAccess(ASSESSMENT, CODE))
                .isEqualTo(ComplimentaryRedemptionResult.ALREADY_ACTIVE);
        verifyNoInteractions(stripe);
    }

    @Test
    void whileDisabledNothingIsReadOrWrittenAndTheFormIsNotOffered() {
        var service = service(ComplimentaryReportAccess.disabled());

        assertThat(service.redeemComplimentaryAccess(ASSESSMENT, CODE)).isEqualTo(ComplimentaryRedemptionResult.DISABLED);
        verifyNoInteractions(store, reports, stripe);
        assertThat(service.accessFor(ASSESSMENT).complimentaryAvailable()).isFalse();
    }

    @Test
    void anInvalidCodeTouchesNoEntitlement() {
        assertThat(service(ComplimentaryReportAccess.enabledWith(CODE)).redeemComplimentaryAccess(ASSESSMENT, "guess"))
                .isEqualTo(ComplimentaryRedemptionResult.INVALID_CODE);
        verifyNoInteractions(store, reports, stripe);
    }
}
