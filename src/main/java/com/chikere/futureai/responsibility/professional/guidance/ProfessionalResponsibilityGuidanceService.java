package com.chikere.futureai.responsibility.professional.guidance;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalReportEntitlement;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisOutcome;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisService;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisUnavailableException;
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
 * The paid guidance lifecycle: entitlement, then the stored guidance if it exists, otherwise the stored (or newly
 * generated, through {@link ProfessionalResponsibilityAnalysisService}) responsibility analysis, the guidance policy
 * once, and an immutable guidance snapshot.
 *
 * <p>Generation is explicit: it happens only when the paid-report lifecycle calls this service, never while a report
 * is rendered. A stored guidance snapshot is returned unchanged and the policy is not run again. The guidance step
 * itself calls no provider: only a missing analysis causes a responsibility Jev call, made by the analysis service.
 * More than eight responsibilities without an explicit selection is reported as RESPONSIBILITY_SELECTION_REQUIRED.</p>
 */
public final class ProfessionalResponsibilityGuidanceService {

    private static final String TRACE_KIND = "RESPONSIBILITY_GUIDANCE";

    private final ProfessionalReportPurchaseUseCase purchases;
    private final ProfessionalResponsibilityAnalysisService analyses;
    private final ProfessionalResponsibilityGuidancePolicy policy;
    private final ProfessionalResponsibilityGuidanceStore store;
    private final Clock clock;
    private final AssessmentTrace trace;

    public ProfessionalResponsibilityGuidanceService(ProfessionalReportPurchaseUseCase purchases,
            ProfessionalResponsibilityAnalysisService analyses, ProfessionalResponsibilityGuidancePolicy policy,
            ProfessionalResponsibilityGuidanceStore store, Clock clock, AssessmentTrace trace) {
        this.purchases = Objects.requireNonNull(purchases);
        this.analyses = Objects.requireNonNull(analyses);
        this.policy = Objects.requireNonNull(policy);
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.trace = Objects.requireNonNull(trace);
    }

    /** The stored guidance, if any. Never generates anything. */
    public Optional<ProfessionalResponsibilityGuidanceSnapshot> find(UUID assessmentId) {
        return store.findByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    public ProfessionalResponsibilityGuidanceOutcome generateOrGet(UUID assessmentId) {
        return generateOrGet(assessmentId, Optional.empty());
    }

    /** @param selection the customer's explicit choice of responsibilities, when the assessment has more than eight */
    public ProfessionalResponsibilityGuidanceOutcome generateOrGet(UUID assessmentId, Optional<List<Integer>> selection) {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Objects.requireNonNull(selection, "selection must not be null");
        return trace.scoped(JourneyType.PROFESSIONAL, assessmentId, TRACE_KIND, () -> run(assessmentId, selection));
    }

    private ProfessionalResponsibilityGuidanceOutcome run(UUID assessmentId, Optional<List<Integer>> selection) {
        boolean entitled = purchases.findEntitlement(assessmentId).map(ProfessionalReportEntitlement::status)
                .filter(status -> status == ProfessionalEntitlementStatus.ACTIVE).isPresent();
        if (!entitled) {
            throw new ResponsibilityAnalysisUnavailableException(ResponsibilityAnalysisUnavailableException.Reason.NOT_ENTITLED,
                    "paid guidance requires an ACTIVE entitlement for the assessment");
        }
        Optional<ProfessionalResponsibilityGuidanceSnapshot> existing = store.findByAssessmentId(assessmentId);
        if (existing.isPresent()) {
            trace.event(JourneyType.PROFESSIONAL, TraceStage.RESPONSIBILITY_GUIDANCE, () -> payload("REUSED", existing.get()));
            return new ProfessionalResponsibilityGuidanceOutcome(existing.get(), true, true);
        }
        ProfessionalResponsibilityAnalysisOutcome analysis = analyses.generateOrGet(assessmentId, selection);
        ProfessionalResponsibilityGuidance guidance = policy.decide(analysis.snapshot());
        if (!guidance.analysisSnapshotId().equals(analysis.snapshot().snapshotId())) {
            throw new IllegalStateException("the guidance was not derived from the stored analysis");
        }
        var candidate = new ProfessionalResponsibilityGuidanceSnapshot(UUID.randomUUID(), assessmentId,
                analysis.snapshot().snapshotId(), guidance.policyVersion(), Instant.now(clock), guidance);
        ProfessionalResponsibilityGuidanceSnapshot stored = store.saveIfAbsent(candidate);
        boolean ours = stored.guidanceId().equals(candidate.guidanceId());
        trace.event(JourneyType.PROFESSIONAL, TraceStage.RESPONSIBILITY_GUIDANCE,
                () -> payload(ours ? "GENERATED" : "CONCURRENT_SNAPSHOT_RETURNED", stored));
        return new ProfessionalResponsibilityGuidanceOutcome(stored, !ours, analysis.reused());
    }

    private static Map<String, Object> payload(String status, ProfessionalResponsibilityGuidanceSnapshot snapshot) {
        var guidance = snapshot.guidance();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("guidanceId", snapshot.guidanceId());
        payload.put("analysisSnapshotId", snapshot.analysisSnapshotId());
        payload.put("policyVersion", snapshot.policyVersion());
        payload.put("opportunities", guidance.opportunities().stream().map(o -> o.responsibilityKey() + ":" + o.ruleId()).toList());
        payload.put("humanValueAreas", guidance.humanValueAreas().stream().map(h -> h.responsibilityKey() + ":" + h.ruleId()).toList());
        payload.put("skillPriorities", guidance.skillPriorities().stream().map(s -> s.ruleId()).toList());
        payload.put("planActions", List.of(guidance.plan().days1To30(), guidance.plan().days31To60(), guidance.plan().days61To90())
                .stream().flatMap(List::stream).map(action -> action.ruleId()).toList());
        payload.put("safeguards", guidance.safeguards().stream().map(s -> s.ruleId()).toList());
        return payload;
    }
}
