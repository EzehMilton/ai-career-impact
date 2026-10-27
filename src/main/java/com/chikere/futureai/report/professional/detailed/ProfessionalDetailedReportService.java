package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.narrative.professional.paid.PaidNarrativeResult;
import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeContext;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeService;
import com.chikere.futureai.narrative.professional.paid.StoredProfessionalPaidNarrative;
import com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalPurchaseStore;
import com.chikere.futureai.payment.professional.application.ProfessionalReportEntitlement;
import com.chikere.futureai.payment.professional.application.PurchasedProfessionalReportSnapshot;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisService;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisUnavailableException;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilitySelection;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceService;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceSnapshot;
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
 * The paid-report lifecycle for {@value ProfessionalDetailedReportVersions#REPORT}: an ACTIVE entitlement, then the
 * stored report if it exists; otherwise the responsibility analysis (Stage 4D, the only possible Jev call) and the
 * guidance (Stage 4E) through the guidance service, the paid narrative, and one immutable assembled snapshot. When the
 * paid narrative is only retryable (a provider failure), the deterministic report is returned provisionally and
 * nothing is frozen; the canonical report is stored only with an AVAILABLE or final FALLBACK narrative.
 *
 * <p>Generation happens only here, on the customer's explicit request, never while a page or PDF is rendered and
 * never from payment confirmation. The role-level content is copied from the purchased professional-report-v2
 * snapshot frozen at payment, so scores, bands and factors are exactly what was purchased.</p>
 *
 * <p>Concurrency: the unique assessment constraints make one analysis, guidance, narrative and report canonical.
 * Two truly simultaneous first requests may duplicate provider calls; both receive the canonical snapshot. This is
 * accepted for v1 instead of queues or locks.</p>
 */
public final class ProfessionalDetailedReportService implements ProfessionalDetailedReportUseCase {

    private static final String TRACE_KIND = "PROFESSIONAL_REPORT_V3";

    private final ProfessionalPurchaseStore purchases;
    private final ProfessionalAssessmentStore assessments;
    private final ProfessionalResponsibilityAnalysisService analyses;
    private final ProfessionalResponsibilityGuidanceService guidance;
    private final ProfessionalPaidNarrativeService narratives;
    private final ProfessionalDetailedReportAssembler assembler;
    private final ProfessionalDetailedReportStore store;
    private final Clock clock;
    private final AssessmentTrace trace;

    public ProfessionalDetailedReportService(ProfessionalPurchaseStore purchases, ProfessionalAssessmentStore assessments,
            ProfessionalResponsibilityAnalysisService analyses, ProfessionalResponsibilityGuidanceService guidance,
            ProfessionalPaidNarrativeService narratives, ProfessionalDetailedReportAssembler assembler,
            ProfessionalDetailedReportStore store, Clock clock, AssessmentTrace trace) {
        this.purchases = Objects.requireNonNull(purchases);
        this.assessments = Objects.requireNonNull(assessments);
        this.analyses = Objects.requireNonNull(analyses);
        this.guidance = Objects.requireNonNull(guidance);
        this.narratives = Objects.requireNonNull(narratives);
        this.assembler = Objects.requireNonNull(assembler);
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.trace = Objects.requireNonNull(trace);
    }

    @Override
    public Optional<ProfessionalDetailedReportSnapshot> find(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        if (activeEntitlement(assessmentId).isEmpty()) {
            return Optional.empty();
        }
        return store.findByAssessmentId(assessmentId);
    }

    @Override
    public Optional<ProfessionalDetailedReport> provisional(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        Optional<ProfessionalReportEntitlement> entitlement = activeEntitlement(assessmentId);
        if (entitlement.isEmpty() || store.findByAssessmentId(assessmentId).isPresent()
                || narratives.find(assessmentId).isPresent()) {
            return Optional.empty();
        }
        Optional<ProfessionalResponsibilityGuidanceSnapshot> guidanceSnapshot = guidance.find(assessmentId);
        Optional<ProfessionalResponsibilityAnalysisSnapshot> analysis = analyses.find(assessmentId);
        Optional<PurchasedProfessionalReportSnapshot> role = roleSnapshot(assessmentId, entitlement.get());
        if (guidanceSnapshot.isEmpty() || analysis.isEmpty() || role.isEmpty()
                || !analysis.get().snapshotId().equals(guidanceSnapshot.get().analysisSnapshotId())) {
            return Optional.empty();
        }
        return Optional.of(assembler.assembleProvisional(role.get(), analysis.get(), guidanceSnapshot.get()));
    }

    @Override
    public Optional<DetailedReportPreparation> preparation(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        if (activeEntitlement(assessmentId).isEmpty()) {
            return Optional.empty();
        }
        boolean ready = store.findByAssessmentId(assessmentId).isPresent();
        Optional<ProfessionalResponsibilityAnalysisSnapshot> analysis = analyses.find(assessmentId);
        List<String> responsibilities = analysis.map(value -> value.input().responsibilities())
                .orElseGet(() -> assessments.findById(assessmentId)
                        .map(stored -> ResponsibilitySelection.usable(stored.state().responsibilities()))
                        .orElse(List.of()));
        int maximum = ProfessionalResponsibilityAnalysisRequest.MAXIMUM_RESPONSIBILITIES;
        return Optional.of(new DetailedReportPreparation(ready, analysis.isPresent(),
                !ready && analysis.isEmpty() && responsibilities.size() > maximum, responsibilities, maximum));
    }

    @Override
    public DetailedReportOutcome prepare(UUID assessmentId, Optional<List<Integer>> selection) {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Objects.requireNonNull(selection, "selection must not be null");
        return trace.scoped(JourneyType.PROFESSIONAL, assessmentId, TRACE_KIND, () -> run(assessmentId, selection));
    }

    private DetailedReportOutcome run(UUID assessmentId, Optional<List<Integer>> selection) {
        ProfessionalReportEntitlement entitlement = activeEntitlement(assessmentId).orElseThrow(() ->
                new ResponsibilityAnalysisUnavailableException(ResponsibilityAnalysisUnavailableException.Reason.NOT_ENTITLED,
                        "the detailed report requires an ACTIVE entitlement for the assessment"));
        Optional<ProfessionalDetailedReportSnapshot> existing = store.findByAssessmentId(assessmentId);
        if (existing.isPresent()) {
            trace.event(JourneyType.PROFESSIONAL, TraceStage.PROFESSIONAL_REPORT_V3_REUSED,
                    () -> payload("REUSED", existing.get(), false));
            return new DetailedReportOutcome(existing, existing.get().report(), true);
        }
        PurchasedProfessionalReportSnapshot roleSnapshot = roleSnapshot(assessmentId, entitlement)
                .orElseThrow(() -> new IllegalStateException("the purchased role-level report snapshot is unavailable"));
        // Generates the responsibility analysis first if it does not exist; otherwise reuses both stored artefacts.
        ProfessionalResponsibilityGuidanceSnapshot guidanceSnapshot = guidance.generateOrGet(assessmentId, selection)
                .snapshot();
        ProfessionalResponsibilityAnalysisSnapshot analysis = analyses.find(assessmentId)
                .filter(value -> value.snapshotId().equals(guidanceSnapshot.analysisSnapshotId()))
                .orElseThrow(() -> new IllegalStateException("the stored analysis does not match the stored guidance"));
        PaidNarrativeResult narrativeResult = narratives.generateOrGet(
                ProfessionalPaidNarrativeContext.from(roleSnapshot.report(), analysis, guidanceSnapshot));
        if (narrativeResult.retryable()) {
            // A transient narrative failure never freezes the report: the deterministic report is returned
            // provisionally and nothing is stored, so a later explicit preparation can still add the narrative.
            ProfessionalDetailedReport provisional = assembler.assembleProvisional(roleSnapshot, analysis, guidanceSnapshot);
            trace.event(JourneyType.PROFESSIONAL, TraceStage.PROFESSIONAL_REPORT_V3_PROVISIONAL, () -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("status", "PROVISIONAL");
                payload.put("stored", false);
                payload.put("paidNarrativeStatus", PaidNarrativeStatus.RETRYABLE_FALLBACK);
                payload.put("retryReason", narrativeResult.retryReason().orElse(null));
                payload.put("analysisId", analysis.snapshotId());
                payload.put("guidanceId", guidanceSnapshot.guidanceId());
                return payload;
            });
            return new DetailedReportOutcome(Optional.empty(), provisional, false);
        }
        StoredProfessionalPaidNarrative narrative = narrativeResult.stored().orElseThrow();
        ProfessionalDetailedReport report = assembler.assemble(roleSnapshot, analysis, guidanceSnapshot, narrative);
        var candidate = new ProfessionalDetailedReportSnapshot(UUID.randomUUID(), assessmentId,
                entitlement.entitlementId(), analysis.snapshotId(), guidanceSnapshot.guidanceId(), narrative.narrativeId(),
                narrative.status(), ProfessionalDetailedReportVersions.REPORT, ProfessionalDetailedReportVersions.PDF,
                Instant.now(clock), report);
        ProfessionalDetailedReportSnapshot stored = store.saveIfAbsent(candidate);
        boolean ours = stored.reportId().equals(candidate.reportId());
        trace.event(JourneyType.PROFESSIONAL, ours ? TraceStage.PROFESSIONAL_REPORT_V3_GENERATED
                : TraceStage.PROFESSIONAL_REPORT_V3_REUSED, () -> payload(ours ? "GENERATED"
                : "CONCURRENT_SNAPSHOT_RETURNED", stored, true));
        return new DetailedReportOutcome(Optional.of(stored), stored.report(), !ours);
    }

    private Optional<PurchasedProfessionalReportSnapshot> roleSnapshot(UUID assessmentId,
            ProfessionalReportEntitlement entitlement) {
        return purchases.findSnapshotByAssessmentId(assessmentId)
                .filter(snapshot -> snapshot.entitlementId().equals(entitlement.entitlementId()));
    }

    private Optional<ProfessionalReportEntitlement> activeEntitlement(UUID assessmentId) {
        return purchases.findEntitlementByAssessmentId(assessmentId)
                .filter(entitlement -> entitlement.status() == ProfessionalEntitlementStatus.ACTIVE);
    }

    /** Identifiers, versions and counts only: never the report body. */
    private static Map<String, Object> payload(String status, ProfessionalDetailedReportSnapshot snapshot,
            boolean assembled) {
        var report = snapshot.report();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reportId", snapshot.reportId());
        payload.put("reportVersion", snapshot.reportVersion());
        payload.put("pdfVersion", snapshot.pdfVersion());
        payload.put("entitlementId", snapshot.entitlementId());
        payload.put("analysisId", snapshot.analysisId());
        payload.put("guidanceId", snapshot.guidanceId());
        payload.put("paidNarrativeId", snapshot.paidNarrativeId());
        payload.put("paidNarrativeStatus", snapshot.paidNarrativeStatus());
        payload.put("provenance", report.provenance());
        payload.put("assembledNow", assembled);
        payload.put("responsibilities", report.responsibilities().cards().size());
        payload.put("opportunities", report.opportunities().items().size());
        payload.put("humanValueAreas", report.humanValue().items().size());
        payload.put("skills", report.skills().items().size());
        payload.put("safeguards", report.safeguards().items().size());
        payload.put("planActions", report.plan().phases().stream().mapToInt(phase -> phase.actions().size()).sum());
        return payload;
    }
}
