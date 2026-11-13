package com.chikere.futureai.report.alevel.detailed;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceStore;
import com.chikere.futureai.guidance.alevel.ALevelGuidance;
import com.chikere.futureai.guidance.alevel.ALevelGuidancePolicyV1;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeOutcome;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeService;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeStatus;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeVersions;
import com.chikere.futureai.payment.alevel.application.ALevelEntitlementStatus;
import com.chikere.futureai.payment.alevel.application.ALevelPurchaseStore;
import com.chikere.futureai.payment.alevel.application.ALevelReportEntitlement;
import com.chikere.futureai.payment.alevel.application.PurchasedALevelReportSnapshot;
import com.chikere.futureai.report.alevel.application.ALevelReportVersions;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * The paid {@value ALevelReportVersions#V2} lifecycle for an {@code a-level-decision-v2} assessment: an ACTIVE
 * entitlement and a v2 purchase, then the stored report if it exists; otherwise the deterministic guidance, one
 * paid-narrative attempt and one immutable snapshot.
 *
 * <p><strong>No Jev call ever happens here.</strong> The subject profile was decided once, at submission, and is read
 * back as stored; there is no per-subject analysis. One A-level assessment is therefore one Jev call in total. When
 * the narrative is only retryable, the deterministic report is returned provisionally and nothing is frozen; the
 * canonical report is stored only with an AVAILABLE or final FALLBACK narrative. A retry reuses the same frozen
 * assessment and re-derives the same deterministic guidance, so only the narrative can change.</p>
 *
 * <p>Generation happens only here, on the customer's explicit request, never while a page or PDF is rendered and
 * never from payment confirmation. A historical v1 assessment or v1 purchase is never routed here.</p>
 *
 * <p>Concurrency: the unique assessment constraint keeps one report canonical. Two truly simultaneous first requests
 * may each make a narrative call; both receive the canonical snapshot, which is never mutated.</p>
 */
public final class ALevelDetailedReportService implements ALevelDetailedReportUseCase {

    private static final String TRACE_KIND = "A_LEVEL_DETAILED_REPORT";

    private final ALevelPurchaseStore purchases;
    private final ALevelAssessmentStore assessments;
    private final ALevelEvidenceStore evidence;
    private final ALevelGuidancePolicyV1 guidancePolicy;
    private final ALevelPaidNarrativeService narratives;
    private final ALevelDetailedReportAssembler assembler;
    private final ALevelDetailedReportStore store;
    private final Clock clock;
    private final AssessmentTrace trace;

    public ALevelDetailedReportService(ALevelPurchaseStore purchases, ALevelAssessmentStore assessments,
            ALevelEvidenceStore evidence, ALevelGuidancePolicyV1 guidancePolicy, ALevelPaidNarrativeService narratives,
            ALevelDetailedReportAssembler assembler, ALevelDetailedReportStore store, Clock clock,
            AssessmentTrace trace) {
        this.purchases = Objects.requireNonNull(purchases);
        this.assessments = Objects.requireNonNull(assessments);
        this.evidence = Objects.requireNonNull(evidence);
        this.guidancePolicy = Objects.requireNonNull(guidancePolicy);
        this.narratives = Objects.requireNonNull(narratives);
        this.assembler = Objects.requireNonNull(assembler);
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.trace = Objects.requireNonNull(trace);
    }

    @Override
    public Optional<ALevelDetailedReportSnapshot> find(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        if (activeV2Purchase(assessmentId).isEmpty()) {
            return Optional.empty();
        }
        return store.findByAssessmentId(assessmentId);
    }

    @Override
    public Optional<ALevelDetailedReport> provisional(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        if (activeV2Purchase(assessmentId).isEmpty() || store.findByAssessmentId(assessmentId).isPresent()) {
            return Optional.empty();
        }
        return subjectProfile(assessmentId).map(assessment -> assembler.assemble(assessment,
                guidance(assessment), deterministicCopy(), evidence.findByAssessmentId(assessmentId)));
    }

    @Override
    public Optional<Preparation> preparation(UUID assessmentId) {
        Objects.requireNonNull(assessmentId);
        if (activeV2Purchase(assessmentId).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Preparation(store.findByAssessmentId(assessmentId).isPresent()));
    }

    @Override
    public Outcome prepare(UUID assessmentId) {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        return trace.scoped(JourneyType.A_LEVEL, assessmentId, TRACE_KIND, () -> run(assessmentId));
    }

    private Outcome run(UUID assessmentId) {
        ALevelReportEntitlement entitlement = activeV2Purchase(assessmentId).orElseThrow(() ->
                new ALevelDetailedReportUnavailableException(ALevelDetailedReportUnavailableException.Reason.NOT_ENTITLED,
                        "the detailed report requires an ACTIVE a-level-report-v2 purchase for the assessment"));
        Optional<ALevelDetailedReportSnapshot> existing = store.findByAssessmentId(assessmentId);
        trace.event(JourneyType.A_LEVEL, TraceStage.A_LEVEL_REPORT_V2_REQUEST, () -> {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("entitlement", "ACTIVE");
            payload.put("entitlementId", entitlement.entitlementId());
            payload.put("reportStored", existing.isPresent());
            payload.put("jevCalled", false);
            return payload;
        });
        if (existing.isPresent()) {
            trace.event(JourneyType.A_LEVEL, TraceStage.A_LEVEL_REPORT_V2_REUSED,
                    () -> payload("REUSED", existing.get()));
            return new Outcome(existing, existing.get().report(), true);
        }
        StoredALevelAssessment assessment = subjectProfile(assessmentId).orElseThrow(() ->
                new ALevelDetailedReportUnavailableException(
                        ALevelDetailedReportUnavailableException.Reason.ASSESSMENT_NOT_FOUND,
                        "no completed a-level-decision-v2 assessment exists with that id"));
        ALevelGuidance guidance = guidance(assessment);
        ALevelPaidNarrativeOutcome narrative = narratives.generate(assembler.narrativeContext(assessment, guidance));
        var storedEvidence = evidence.findByAssessmentId(assessmentId);
        ALevelDetailedReport report = assembler.assemble(assessment, guidance, narrative, storedEvidence);
        if (narrative.retryable()) {
            // A transient narrative failure never freezes the report: it is shown provisionally, nothing is stored,
            // and a later explicit preparation can still add the narrative.
            trace.event(JourneyType.A_LEVEL, TraceStage.A_LEVEL_REPORT_V2_PROVISIONAL, () -> {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("status", "PROVISIONAL");
                payload.put("stored", false);
                payload.put("paidNarrativeStatus", narrative.status());
                payload.put("retryReason", narrative.reason().orElse(null));
                return payload;
            });
            return new Outcome(Optional.empty(), report, false);
        }
        String evidenceVersion = report.provenance().evidenceVersion();
        var candidate = new ALevelDetailedReportSnapshot(UUID.randomUUID(), assessmentId, entitlement.entitlementId(),
                assessment.result().judgmentSchemaVersion(), assessment.result().decisionPolicyVersion(),
                guidance.policyVersion(), narrative.narrativeVersion(), narrative.status(),
                evidenceVersion.isEmpty() ? Optional.empty() : Optional.of(evidenceVersion), report.reportVersion(),
                pdfVersion(report.reportVersion()), Instant.now(clock), report);
        ALevelDetailedReportSnapshot stored = store.saveIfAbsent(candidate);
        boolean ours = stored.reportId().equals(candidate.reportId());
        trace.event(JourneyType.A_LEVEL, ours ? TraceStage.A_LEVEL_REPORT_V2_GENERATED
                : TraceStage.A_LEVEL_REPORT_V2_REUSED, () -> payload(ours ? "GENERATED"
                : "CONCURRENT_SNAPSHOT_RETURNED", stored));
        return new Outcome(Optional.of(stored), stored.report(), !ours);
    }

    private ALevelGuidance guidance(StoredALevelAssessment assessment) {
        return guidancePolicy.decide(assessment.result().profile(), assessment.state());
    }

    /** A completed v2 subject profile: a v1 assessment or one that needed more information has no v2 report. */
    private Optional<StoredALevelAssessment> subjectProfile(UUID assessmentId) {
        return assessments.findById(assessmentId)
                .filter(value -> value.result().usesSubjectProfile())
                .filter(value -> value.result().headline().status() != AssessmentStatus.MORE_INFORMATION_REQUIRED);
    }

    /** An ACTIVE entitlement whose purchase is an a-level-report-v2 subject-profile purchase. */
    private Optional<ALevelReportEntitlement> activeV2Purchase(UUID assessmentId) {
        return purchases.findEntitlementByAssessmentId(assessmentId)
                .filter(entitlement -> entitlement.status() == ALevelEntitlementStatus.ACTIVE)
                .filter(entitlement -> purchases.findSnapshotByAssessmentId(assessmentId)
                        .filter(snapshot -> snapshot.entitlementId().equals(entitlement.entitlementId()))
                        .filter(PurchasedALevelReportSnapshot::isSubjectProfilePurchase).isPresent());
    }

    private static ALevelPaidNarrativeOutcome deterministicCopy() {
        return new ALevelPaidNarrativeOutcome(ALevelPaidNarrativeStatus.RETRYABLE_FALLBACK, Optional.empty(),
                ALevelPaidNarrativeVersions.CURRENT, "none", Optional.empty(), Optional.empty(), 0,
                Optional.of("PROVISIONAL_REPORT"));
    }

    /** Identifiers, versions and counts only: never the report body. */
    private static Map<String, Object> payload(String status, ALevelDetailedReportSnapshot snapshot) {
        var report = snapshot.report();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", status);
        payload.put("reportId", snapshot.reportId());
        payload.put("reportVersion", snapshot.reportVersion());
        payload.put("pdfVersion", snapshot.pdfVersion());
        payload.put("decisionPolicyVersion", snapshot.decisionPolicyVersion());
        payload.put("guidancePolicyVersion", snapshot.guidancePolicyVersion());
        payload.put("paidNarrativeStatus", snapshot.paidNarrativeStatus());
        payload.put("strengths", report.strengths().size());
        payload.put("skills", report.skillPriorities().size());
        payload.put("safeguards", report.safeguards().size());
        payload.put("planActions", report.actionPlan().now().size() + report.actionPlan().sixthForm().size()
                + report.actionPlan().applications().size());
        payload.put("evidenceItems", report.evidence().size());
        payload.put("jevCalled", false);
        return payload;
    }

    private static String pdfVersion(String reportVersion) {
        return switch (reportVersion) {
            case ALevelReportVersions.V2 -> ALevelReportVersions.PDF_V2;
            case ALevelReportVersions.V3 -> ALevelReportVersions.PDF_V3;
            default -> throw new IllegalArgumentException("unsupported A-level report version: " + reportVersion);
        };
    }
}
