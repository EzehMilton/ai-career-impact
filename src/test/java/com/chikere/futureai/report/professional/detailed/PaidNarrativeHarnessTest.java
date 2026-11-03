package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentProvider;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisUnavailableException;
import com.chikere.futureai.responsibility.professional.guidance.AnalysisSnapshots;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalGuidancePolicyV1;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * The live paid-narrative harness's setup, with an offline generator instead of OpenAI: every role's assessment
 * really exists, the real orchestration runs end to end, Jev is never called, guidance is decided exactly once, the
 * richer v2 narrative needs exactly one provider call (the calibrated Nurse and Accountant fixtures included), and
 * reuse makes none.
 */
class PaidNarrativeHarnessTest {

    @Test
    void everyCalibrationRoleRunsTheRealOrchestrationFromAStoredAssessment() {
        assertThat(DetailedReportFixtures.ROLES).hasSize(7);
        for (String run : DetailedReportFixtures.ROLES.keySet()) {
            var generator = new ProfessionalPaidNarrativeLiveIT.Recording(new ProfessionalPaidNarrativeLiveIT.Offline());
            var harness = new PaidNarrativeHarness(generator);
            UUID id = harness.seed(run);
            var analysis = AnalysisSnapshots.live(run);

            // The assessment, entitlement and frozen v2 report exist before preparation, all for the captured id.
            assertThat(id).as(run).isEqualTo(analysis.assessmentId());
            assertThat(harness.assessments.findById(id)).as(run).hasValueSatisfying(assessment -> {
                assertThat(assessment.state().profession()).isEqualTo(DetailedReportFixtures.ROLES.get(run));
                assertThat(assessment.state().responsibilities()).isEqualTo(analysis.input().responsibilities());
            });
            var entitlement = harness.purchases.findEntitlementByAssessmentId(id).orElseThrow();
            assertThat(entitlement.status()).isEqualTo(ProfessionalEntitlementStatus.ACTIVE);
            assertThat(entitlement.paymentProvider()).isEqualTo(ProfessionalPaymentProvider.COMPLIMENTARY);
            var role = harness.purchases.findSnapshotByAssessmentId(id).orElseThrow();
            assertThat(role.report().profession()).isEqualTo(DetailedReportFixtures.ROLES.get(run));

            var outcome = harness.service.prepare(id, Optional.empty());

            assertThat(generator.calls).as(run + " reached the paid narrative generator").hasSize(1);
            verifyNoInteractions(harness.jev);
            assertThat(harness.guidancePolicy.calls).as(run).isEqualTo(1);
            var guidance = harness.guidanceStore.snapshots.get(id);
            assertThat(guidance.analysisSnapshotId()).isEqualTo(analysis.snapshotId());
            assertThat(guidance.guidance()).isEqualTo(new ProfessionalGuidancePolicyV1().decide(analysis));
            var snapshot = outcome.snapshot().orElseThrow();
            assertThat(snapshot.paidNarrativeStatus()).isEqualTo(PaidNarrativeStatus.AVAILABLE);
            assertThat(snapshot.reportVersion()).isEqualTo(ProfessionalDetailedReportVersions.REPORT_V4);
            assertThat(snapshot.report().opportunities().items()).as(run)
                    .allSatisfy(item -> assertThat(item.practicalAiUses()).isNotEmpty());
            assertThat(snapshot.entitlementId()).isEqualTo(entitlement.entitlementId());
            assertThat(snapshot.analysisId()).isEqualTo(analysis.snapshotId());
            assertThat(harness.reportStore.reports.get(id)).isEqualTo(snapshot);

            // Reuse: the stored report comes back with no further generator, policy or Jev call.
            var again = harness.service.prepare(id, Optional.empty());
            assertThat(again.reused()).isTrue();
            assertThat(again.snapshot()).contains(snapshot);
            assertThat(generator.calls).hasSize(1);
            assertThat(harness.guidancePolicy.calls).isEqualTo(1);
            verifyNoInteractions(harness.jev);
        }
    }

    @Test
    void theAssessmentLookupIsNotBypassed() {
        var generator = new ProfessionalPaidNarrativeLiveIT.Recording(new ProfessionalPaidNarrativeLiveIT.Offline());
        var harness = new PaidNarrativeHarness(generator);
        UUID id = harness.seed("registered-nurse-8");
        harness.assessments.stored.remove(id);

        assertThatThrownBy(() -> harness.service.prepare(id, Optional.empty())).isInstanceOfSatisfying(
                ResponsibilityAnalysisUnavailableException.class, unavailable -> assertThat(unavailable.reason())
                        .isEqualTo(ResponsibilityAnalysisUnavailableException.Reason.ASSESSMENT_NOT_FOUND));
        assertThat(generator.calls).isEmpty();
        verifyNoInteractions(harness.jev);
    }
}
