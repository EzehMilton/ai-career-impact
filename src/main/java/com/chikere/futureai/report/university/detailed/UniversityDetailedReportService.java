package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisService;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisStore;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisUnavailableException;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidance;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidancePolicyV1;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeOutcome;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeService;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeStatus;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeVersions;
import com.chikere.futureai.payment.university.application.PurchasedUniversityReportSnapshot;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityPurchaseStore;
import com.chikere.futureai.payment.university.application.UniversityReportEntitlement;
import com.chikere.futureai.report.university.application.UniversityReportVersions;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The paid {@value UniversityReportVersions#V2} lifecycle for a {@code university-decision-v2} assessment: an ACTIVE
 * entitlement and a v2 purchase, then the stored report if it exists; otherwise the module analysis (Stage 5E, the
 * only possible Jev call), the deterministic guidance (Stage 5F), one paid-narrative attempt and one immutable
 * snapshot. When the narrative is only retryable, the deterministic report is returned provisionally and nothing is
 * frozen; the canonical report is stored only with an AVAILABLE or final FALLBACK narrative.
 *
 * <p>Generation happens only here, on the customer's explicit request, never while a page or PDF is rendered and
 * never from payment confirmation. A historical v1 assessment or v1 purchase is never routed here: it keeps its frozen
 * university-report-v1. Retrying never reruns Jev or reclassifies: the stored module analysis is reused, and the
 * guidance is the same deterministic function of it.</p>
 *
 * <p>Concurrency: the unique assessment constraints keep one module analysis and one report canonical. Two truly
 * simultaneous first requests may duplicate provider calls; both receive the canonical snapshot.</p>
 */
public final class UniversityDetailedReportService implements UniversityDetailedReportUseCase {

    private static final String TRACE_KIND = "UNIVERSITY_REPORT_V2";

    private final UniversityPurchaseStore purchases;
    private final UniversityAssessmentStore assessments;
    private final UniversityModuleAnalysisService moduleAnalyses;
    private final UniversityModuleAnalysisStore moduleSnapshots;
    private final UniversityGuidancePolicyV1 guidancePolicy;
    private final UniversityPaidNarrativeService narratives;
    private final UniversityDetailedReportAssembler assembler;
    private final UniversityDetailedReportStore store;
    private final Clock clock;
    private final AssessmentTrace trace;

    public UniversityDetailedReportService(UniversityPurchaseStore purchases, UniversityAssessmentStore assessments,
            UniversityModuleAnalysisService moduleAnalyses, UniversityModuleAnalysisStore moduleSnapshots,
            UniversityGuidancePolicyV1 guidancePolicy, UniversityPaidNarrativeService narratives,
            UniversityDetailedReportAssembler assembler, UniversityDetailedReportStore store, Clock clock,
            AssessmentTrace trace) {
        this.purchases = Objects.requireNonNull(purchases);
        this.assessments = Objects.requireNonNull(assessments);
        this.moduleAnalyses = Objects.requireNonNull(moduleAnalyses);
        this.moduleSnapshots = Objects.requireNonNull(moduleSnapshots);
        this.guidancePolicy = Objects.requireNonNull(guidancePolicy);
        this.narratives = Objects.requireNonNull(narratives);
        this.assembler = Objects.requireNonNull(assembler);
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.trace = Objects.requireNonNull(trace);
    }

    @Override
    public Optional<UniversityDetailedReportSnapshot> find(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        if (activeV2Purchase(assessmentId).isEmpty()) {
            return Optional.empty();
        }
        return store.findByAssessmentId(assessmentId);
    }

    @Override
    public Optional<UniversityDetailedReport> provisional(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        if (activeV2Purchase(assessmentId).isEmpty() || store.findByAssessmentId(assessmentId).isPresent()) {
            return Optional.empty();
        }
        Optional<StoredUniversityAssessment> assessment = assessments.findById(assessmentId);
        Optional<UniversityModuleAnalysisSnapshot> modules = moduleSnapshots.findCompletedByAssessmentId(assessmentId);
        if (assessment.isEmpty() || modules.isEmpty() || !assessment.get().result().usesCourseProfile()) {
            return Optional.empty();
        }
        UniversityGuidance guidance = guidancePolicy.decide(modules.get());
        return Optional.of(assembler.assemble(assessment.get(), modules.get(), guidance,
                deterministicCopy(assessmentId)));
    }

    @Override
    public Optional<Preparation> preparation(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        if (activeV2Purchase(assessmentId).isEmpty()) {
            return Optional.empty();
        }
        boolean ready = store.findByAssessmentId(assessmentId).isPresent();
        boolean analysed = moduleSnapshots.findCompletedByAssessmentId(assessmentId).isPresent();
        List<String> modules = assessments.findById(assessmentId).map(value -> value.state().keyModules())
                .orElse(List.of());
        int maximum = UniversityModuleAnalysisRequest.MAXIMUM_MODULES;
        return Optional.of(new Preparation(ready, analysed, !ready && !analysed && modules.size() > maximum, modules,
                maximum));
    }

    @Override
    public Outcome prepare(UUID assessmentId, Optional<List<Integer>> selection) {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Objects.requireNonNull(selection, "selection must not be null");
        return trace.scoped(JourneyType.UNIVERSITY, assessmentId, TRACE_KIND, () -> run(assessmentId, selection));
    }

    private Outcome run(UUID assessmentId, Optional<List<Integer>> selection) {
        UniversityReportEntitlement entitlement = activeV2Purchase(assessmentId).orElseThrow(() ->
                new UniversityModuleAnalysisUnavailableException(
                        UniversityModuleAnalysisUnavailableException.Reason.NOT_ENTITLED,
                        "the detailed report requires an ACTIVE university-report-v2 purchase for the assessment"));
        Optional<UniversityDetailedReportSnapshot> existing = store.findByAssessmentId(assessmentId);
        if (existing.isPresent()) {
            trace.event(JourneyType.UNIVERSITY, TraceStage.UNIVERSITY_REPORT_V2_REUSED,
                    () -> payload("REUSED", existing.get()));
            return new Outcome(existing, existing.get().report(), true);
        }
        StoredUniversityAssessment assessment = assessments.findById(assessmentId)
                .filter(value -> value.result().usesCourseProfile())
                .orElseThrow(() -> new UniversityModuleAnalysisUnavailableException(
                        UniversityModuleAnalysisUnavailableException.Reason.ASSESSMENT_NOT_FOUND,
                        "no university-decision-v2 assessment exists with that id"));
        // Reuses the stored analysis, or makes the one Jev call after the selection is valid.
        UniversityModuleAnalysisSnapshot modules = moduleAnalyses.generateOrGet(assessmentId, selection).snapshot();
        UniversityGuidance guidance = guidancePolicy.decide(modules);
        UniversityPaidNarrativeOutcome narrative = narratives.generate(assembler.narrativeContext(assessment, modules,
                guidance));
        UniversityDetailedReport report = assembler.assemble(assessment, modules, guidance, narrative);
        if (narrative.retryable()) {
            // A transient narrative failure never freezes the report: it is shown provisionally, nothing is stored,
            // and a later explicit preparation can still add the narrative.
            trace.event(JourneyType.UNIVERSITY, TraceStage.UNIVERSITY_REPORT_V2_PROVISIONAL, () -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("status", "PROVISIONAL");
                payload.put("stored", false);
                payload.put("paidNarrativeStatus", narrative.status());
                payload.put("retryReason", narrative.reason().orElse(null));
                payload.put("moduleAnalysisId", modules.snapshotId());
                return payload;
            });
            return new Outcome(Optional.empty(), report, false);
        }
        var candidate = new UniversityDetailedReportSnapshot(UUID.randomUUID(), assessmentId,
                entitlement.entitlementId(), assessment.result().decisionPolicyVersion(), modules.snapshotId(),
                modules.schemaVersion(), modules.policyVersion(), guidance.policyVersion(), narrative.narrativeVersion(),
                narrative.status(), UniversityReportVersions.V2, UniversityReportVersions.PDF_V2, Instant.now(clock),
                report);
        UniversityDetailedReportSnapshot stored = store.saveIfAbsent(candidate);
        boolean ours = stored.reportId().equals(candidate.reportId());
        trace.event(JourneyType.UNIVERSITY, ours ? TraceStage.UNIVERSITY_REPORT_V2_GENERATED
                : TraceStage.UNIVERSITY_REPORT_V2_REUSED, () -> payload(ours ? "GENERATED"
                : "CONCURRENT_SNAPSHOT_RETURNED", stored));
        return new Outcome(Optional.of(stored), stored.report(), !ours);
    }

    /** An ACTIVE entitlement whose purchase is a university-report-v2 course-profile purchase. */
    private Optional<UniversityReportEntitlement> activeV2Purchase(UUID assessmentId) {
        return purchases.findEntitlementByAssessmentId(assessmentId)
                .filter(entitlement -> entitlement.status() == UniversityEntitlementStatus.ACTIVE)
                .filter(entitlement -> purchases.findSnapshotByAssessmentId(assessmentId)
                        .filter(snapshot -> snapshot.entitlementId().equals(entitlement.entitlementId()))
                        .filter(PurchasedUniversityReportSnapshot::isCourseProfilePurchase).isPresent());
    }

    private static UniversityPaidNarrativeOutcome deterministicCopy(UUID assessmentId) {
        return new UniversityPaidNarrativeOutcome(UniversityPaidNarrativeStatus.RETRYABLE_FALLBACK, Optional.empty(),
                UniversityPaidNarrativeVersions.CURRENT, "none", Optional.empty(), Optional.empty(), 0,
                Optional.of("PROVISIONAL_REPORT"));
    }

    /** Identifiers, versions and counts only: never the report body. */
    private static Map<String, Object> payload(String status, UniversityDetailedReportSnapshot snapshot) {
        var report = snapshot.report();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reportId", snapshot.reportId());
        payload.put("reportVersion", snapshot.reportVersion());
        payload.put("pdfVersion", snapshot.pdfVersion());
        payload.put("decisionPolicyVersion", snapshot.decisionPolicyVersion());
        payload.put("moduleAnalysisId", snapshot.moduleAnalysisId());
        payload.put("guidancePolicyVersion", snapshot.guidancePolicyVersion());
        payload.put("paidNarrativeStatus", snapshot.paidNarrativeStatus());
        payload.put("modules", report.modules().cards().size());
        payload.put("skills", report.skills().size());
        payload.put("safeguards", report.safeguards().size());
        payload.put("planActions", report.plan().stream().mapToInt(phase -> phase.actions().size()).sum());
        return payload;
    }
}
