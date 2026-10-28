package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.coursemodule.university.analysis.ModuleAnalysisTestData;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisFailure;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisService;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisStore;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleClassifier;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentProvider;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidancePolicyV1;
import com.chikere.futureai.coursemodule.university.policy.UniversityModulePolicyV1;
import com.chikere.futureai.narrative.university.paid.ScriptedPaidNarrativeGenerator;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeService;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeValidator;
import com.chikere.futureai.payment.university.application.PurchasedUniversityReportSnapshot;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityPurchaseStore;
import com.chikere.futureai.payment.university.application.UniversityReportEntitlement;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.report.university.application.UniversityReportAssembler;
import com.chikere.futureai.report.university.application.UniversityReportSource;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import com.chikere.futureai.trace.AssessmentTrace;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The university-report-v2 lifecycle on in-memory ports: the real module-analysis service, guidance policy,
 * paid-narrative service and assembler, with a counting fixture "Jev" and a scripted narrative. No real provider.
 */
final class DetailedReportTestPorts {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC);

    final Map<UUID, StoredUniversityAssessment> assessments = new HashMap<>();
    final Map<UUID, UniversityReportEntitlement> entitlements = new HashMap<>();
    final Map<UUID, PurchasedUniversityReportSnapshot> purchaseSnapshots = new HashMap<>();
    final Map<UUID, UniversityModuleAnalysisSnapshot> moduleSnapshots = new HashMap<>();
    final Map<UUID, UniversityDetailedReportSnapshot> reports = new HashMap<>();
    final List<UniversityModuleAnalysisRequest> jevRequests = new ArrayList<>();
    ScriptedPaidNarrativeGenerator narrative = new ScriptedPaidNarrativeGenerator();
    boolean narrativeConfigured = true;
    int reportSaves;

    final UniversityAssessmentStore assessmentStore = new UniversityAssessmentStore() {
        @Override
        public void save(StoredUniversityAssessment assessment) {
            assessments.put(assessment.id(), assessment);
        }

        @Override
        public Optional<StoredUniversityAssessment> findById(UUID id) {
            return Optional.ofNullable(assessments.get(id));
        }
    };

    final UniversityModuleAnalysisStore moduleStore = new UniversityModuleAnalysisStore() {
        @Override
        public Optional<UniversityModuleAnalysisSnapshot> findCompletedByAssessmentId(UUID assessmentId) {
            return Optional.ofNullable(moduleSnapshots.get(assessmentId));
        }

        @Override
        public UniversityModuleAnalysisSnapshot saveCompletedIfAbsent(UniversityModuleAnalysisSnapshot snapshot) {
            return moduleSnapshots.computeIfAbsent(snapshot.assessmentId(), id -> snapshot);
        }

        @Override
        public void recordFailure(UniversityModuleAnalysisFailure failure) {
        }

        @Override
        public List<UniversityModuleAnalysisFailure> findFailuresByAssessmentId(UUID assessmentId) {
            return List.of();
        }
    };

    final UniversityDetailedReportStore reportStore = new UniversityDetailedReportStore() {
        @Override
        public Optional<UniversityDetailedReportSnapshot> findByAssessmentId(UUID assessmentId) {
            return Optional.ofNullable(reports.get(assessmentId));
        }

        @Override
        public UniversityDetailedReportSnapshot saveIfAbsent(UniversityDetailedReportSnapshot snapshot) {
            reportSaves++;
            return reports.computeIfAbsent(snapshot.assessmentId(), id -> snapshot);
        }
    };

    private final UniversityModuleJudgmentProvider jev = new UniversityModuleJudgmentProvider() {
        @Override
        public UniversityModuleJudgmentResult judge(UniversityModuleAnalysisRequest request) {
            jevRequests.add(request);
            return ModuleAnalysisTestData.fixtureJudgments("nursing", request);
        }

        @Override
        public UniversityModuleJudgmentSource source() {
            return UniversityModuleJudgmentSource.JEV;
        }
    };

    UniversityDetailedReportService service() {
        UniversityPurchaseStore purchases = mock(UniversityPurchaseStore.class);
        when(purchases.findEntitlementByAssessmentId(any())).thenAnswer(call ->
                Optional.ofNullable(entitlements.get(call.<UUID>getArgument(0))));
        when(purchases.findSnapshotByAssessmentId(any())).thenAnswer(call ->
                Optional.ofNullable(purchaseSnapshots.get(call.<UUID>getArgument(0))));
        UniversityReportPurchaseUseCase purchaseUseCase = mock(UniversityReportPurchaseUseCase.class);
        when(purchaseUseCase.findEntitlement(any())).thenAnswer(call ->
                Optional.ofNullable(entitlements.get(call.<UUID>getArgument(0))));
        var moduleAnalyses = new UniversityModuleAnalysisService(assessmentStore, purchaseUseCase,
                () -> Optional.of(jev), UniversityModuleClassifier.v1(new UniversityModulePolicyV1()), moduleStore,
                CLOCK, AssessmentTrace.disabled());
        var narratives = new UniversityPaidNarrativeService(
                () -> narrativeConfigured ? Optional.of(narrative) : Optional.empty(),
                new UniversityPaidNarrativeValidator(), AssessmentTrace.disabled());
        return new UniversityDetailedReportService(purchases, assessmentStore, moduleAnalyses, moduleStore,
                new UniversityGuidancePolicyV1(), narratives, new UniversityDetailedReportAssembler(), reportStore,
                CLOCK, AssessmentTrace.disabled());
    }

    /** A university-decision-v2 assessment with an ACTIVE v2 (course-profile) purchase. */
    UUID paidCourseProfile(int modules) {
        UUID id = UUID.randomUUID();
        assessments.put(id, ModuleAnalysisTestData.courseProfileAssessment(id, modules));
        purchase(id, UniversityEntitlementStatus.ACTIVE, true);
        return id;
    }

    void purchase(UUID id, UniversityEntitlementStatus status, boolean courseProfile) {
        var entitlement = ModuleAnalysisTestData.entitlement(id, status);
        entitlements.put(id, entitlement);
        purchaseSnapshots.put(id, courseProfile
                ? PurchasedUniversityReportSnapshot.courseProfile(entitlement.entitlementId(), id,
                        ModuleAnalysisTestData.ASSESSED_AT)
                : historicalV1Purchase(entitlement.entitlementId(), id));
    }

    /** A frozen university-report-v1 purchase of the stored (v1) assessment, as before Stage 5G. */
    private PurchasedUniversityReportSnapshot historicalV1Purchase(UUID entitlementId, UUID assessmentId) {
        var report = new UniversityReportAssembler().assemble(new UniversityReportSource(assessments.get(assessmentId),
                Optional.empty(), Optional.empty()), UniversityReportTier.PAID);
        return new PurchasedUniversityReportSnapshot(entitlementId, assessmentId, report.reportVersion(),
                ModuleAnalysisTestData.ASSESSED_AT, report);
    }
}
