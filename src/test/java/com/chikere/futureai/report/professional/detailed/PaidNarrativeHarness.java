package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceStore;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeStore;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeGenerator;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeService;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeValidator;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationStore;
import com.chikere.futureai.payment.complimentary.ComplimentaryRedemptionResult;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseService;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssembler;
import com.chikere.futureai.report.professional.application.ProfessionalReportService;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisService;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider;
import com.chikere.futureai.responsibility.professional.guidance.AnalysisSnapshots;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceService;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
import com.chikere.futureai.trace.AssessmentTrace;

import java.net.URI;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;

/**
 * The real detailed-report orchestration for one captured calibration role, over in-memory stores. Only the external
 * responsibility judgement is replaced: the captured Stage 4C analysis is stored as the completed snapshot, and the
 * Jev provider is a mock that must stay untouched.
 *
 * <p>{@link #seed} persists the minimum state production has before a detailed report is prepared: the stored
 * Professional assessment, then the ACTIVE complimentary entitlement and frozen professional-report-v2 through the
 * real complimentary grant (which assembles the v2 report from that stored assessment), then the captured analysis.
 * Guidance is not seeded: the real guidance service decides it once from the stored analysis.</p>
 */
final class PaidNarrativeHarness {

    final Assessments assessments = new Assessments();
    final ProfessionalDetailedReportServiceTest.Purchases purchases = new ProfessionalDetailedReportServiceTest.Purchases();
    final ProfessionalDetailedReportServiceTest.AnalysisStore analysisStore =
            new ProfessionalDetailedReportServiceTest.AnalysisStore();
    final ProfessionalDetailedReportServiceTest.GuidanceStore guidanceStore =
            new ProfessionalDetailedReportServiceTest.GuidanceStore();
    final ProfessionalDetailedReportServiceTest.NarrativeStore narrativeStore =
            new ProfessionalDetailedReportServiceTest.NarrativeStore();
    final ProfessionalDetailedReportServiceTest.ReportStore reportStore = new ProfessionalDetailedReportServiceTest.ReportStore();
    final ProfessionalDetailedReportServiceTest.CountingGuidancePolicy guidancePolicy =
            new ProfessionalDetailedReportServiceTest.CountingGuidancePolicy();
    final ProfessionalResponsibilityJudgmentProvider jev = mock(ProfessionalResponsibilityJudgmentProvider.class);
    final ProfessionalReportPurchaseService purchaseService;
    final ProfessionalDetailedReportService service;

    PaidNarrativeHarness(ProfessionalPaidNarrativeGenerator generator) {
        // No role-level narrative, evidence or classification is stored, as for the captured role reports.
        var roleReports = new ProfessionalReportService(assessments, mock(ProfessionalNarrativeStore.class),
                mock(ProfessionalEvidenceStore.class), mock(ProfessionalOccupationClassificationStore.class),
                new ProfessionalReportAssembler());
        purchaseService = new ProfessionalReportPurchaseService(roleReports, purchases, Optional.empty(),
                URI.create("http://localhost"), Optional.empty(), Clock.systemUTC());
        var analyses = new ProfessionalResponsibilityAnalysisService(assessments, purchaseService, () -> Optional.of(jev),
                new ProfessionalResponsibilityPolicyV1(), analysisStore, Clock.systemUTC(), AssessmentTrace.disabled());
        var guidance = new ProfessionalResponsibilityGuidanceService(purchaseService, analyses, guidancePolicy,
                guidanceStore, Clock.systemUTC(), AssessmentTrace.disabled());
        var narratives = new ProfessionalPaidNarrativeService(narrativeStore, () -> Optional.of(generator),
                new ProfessionalPaidNarrativeValidator(), Clock.systemUTC(), AssessmentTrace.disabled());
        service = new ProfessionalDetailedReportService(purchases, assessments, analyses, guidance, narratives,
                new ProfessionalDetailedReportAssembler(), reportStore, Clock.systemUTC(), AssessmentTrace.disabled());
    }

    /** Persists the role's assessment, entitlement, frozen v2 report and captured analysis; returns the assessment id. */
    UUID seed(String run) {
        StoredProfessionalAssessment assessment = DetailedReportFixtures.assessment(run);
        assessments.save(assessment);
        var granted = purchaseService.grantComplimentaryAccess(assessment.id());
        if (granted != ComplimentaryRedemptionResult.UNLOCKED) {
            throw new IllegalStateException(run + ": complimentary access was not granted: " + granted);
        }
        var analysis = AnalysisSnapshots.live(run);
        if (!analysis.assessmentId().equals(assessment.id())) {
            throw new IllegalStateException(run + ": the captured analysis belongs to a different assessment");
        }
        analysisStore.snapshots.put(assessment.id(), analysis);
        return assessment.id();
    }

    static final class Assessments implements ProfessionalAssessmentStore {
        final Map<UUID, StoredProfessionalAssessment> stored = new HashMap<>();

        @Override
        public void save(StoredProfessionalAssessment assessment) {
            if (stored.putIfAbsent(assessment.id(), assessment) != null) {
                throw new IllegalStateException("the assessment is already stored");
            }
        }

        @Override
        public Optional<StoredProfessionalAssessment> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }
    }
}
