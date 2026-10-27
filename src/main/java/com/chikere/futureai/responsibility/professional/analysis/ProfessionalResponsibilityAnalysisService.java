package com.chikere.futureai.responsibility.professional.analysis;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalReportEntitlement;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityClassificationResult;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityClassifier;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Generates, or returns, the one immutable paid responsibility analysis of a Professional assessment.
 *
 * <p>{@link #generateOrGet} first requires an ACTIVE entitlement (paid or complimentary, as the existing entitlement
 * model records it), for reuse as well as generation. It then returns a stored snapshot without calling anything,
 * or selects the stored responsibilities, calls the responsibility provider exactly once, classifies with
 * {@link ProfessionalResponsibilityPolicyV1}, and stores the complete snapshot. The role-level assessment is only
 * read: no score, band, factor, narrative or role-level judgment is recomputed or changed.</p>
 *
 * <p>A failure at any stage stores no completed snapshot; it is recorded and may be retried by calling again.
 * Nothing is retried automatically, so a failure never silently costs extra provider calls.</p>
 *
 * <p><b>Known v1 characteristics, accepted deliberately:</b></p>
 * <ul>
 *   <li>Two truly simultaneous first-generation requests can each make a provider call, but only one immutable
 *       snapshot can become canonical (the database's unique assessment constraint) and both callers return it.
 *       There is no stored PENDING claim, lease, queue or lock; revisit only if production shows a real cost.</li>
 *   <li>A Professional assessment may hold more than eight responsibilities, but detailed analysis supports one to
 *       eight. The later paid-report flow will collect the customer's explicit choice of up to eight before
 *       generation; until it exists, more than eight is refused. No automatic "first eight" selection is allowed.</li>
 *   <li>Generation never runs from payment confirmation. The Stripe webhook only makes the entitlement ACTIVE; the
 *       paid-report lifecycle calls this service later.</li>
 * </ul>
 */
public final class ProfessionalResponsibilityAnalysisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalResponsibilityAnalysisService.class);
    private static final String TRACE_KIND = "RESPONSIBILITY_ANALYSIS";

    private final ProfessionalAssessmentStore assessments;
    private final ProfessionalReportPurchaseUseCase purchases;
    private final Supplier<Optional<ProfessionalResponsibilityJudgmentProvider>> provider;
    private final ProfessionalResponsibilityClassifier policy;
    private final ProfessionalResponsibilityAnalysisStore store;
    private final Clock clock;
    private final AssessmentTrace trace;

    /** @param provider the responsibility provider if one is configured for the active profile */
    public ProfessionalResponsibilityAnalysisService(ProfessionalAssessmentStore assessments,
            ProfessionalReportPurchaseUseCase purchases,
            Supplier<Optional<ProfessionalResponsibilityJudgmentProvider>> provider,
            ProfessionalResponsibilityClassifier policy, ProfessionalResponsibilityAnalysisStore store, Clock clock,
            AssessmentTrace trace) {
        this.assessments = Objects.requireNonNull(assessments);
        this.purchases = Objects.requireNonNull(purchases);
        this.provider = Objects.requireNonNull(provider);
        this.policy = Objects.requireNonNull(policy);
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.trace = Objects.requireNonNull(trace);
    }

    /** The stored snapshot, if one exists. Never generates anything. */
    public Optional<ProfessionalResponsibilityAnalysisSnapshot> find(UUID assessmentId) {
        return store.findCompletedByAssessmentId(Objects.requireNonNull(assessmentId));
    }

    public ProfessionalResponsibilityAnalysisOutcome generateOrGet(UUID assessmentId) {
        return generateOrGet(assessmentId, Optional.empty());
    }

    /**
     * As {@link #generateOrGet(UUID)}, with the customer's explicit choice of responsibilities (1-based positions)
     * when the assessment has more than eight. A stored snapshot is returned whatever selection is passed: it was
     * made once and is never regenerated for a different choice.
     */
    public ProfessionalResponsibilityAnalysisOutcome generateOrGet(UUID assessmentId, Optional<List<Integer>> selection) {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Objects.requireNonNull(selection, "selection must not be null");
        return trace.scoped(JourneyType.PROFESSIONAL, assessmentId, TRACE_KIND, () -> run(assessmentId, selection));
    }

    private ProfessionalResponsibilityAnalysisOutcome run(UUID assessmentId, Optional<List<Integer>> selection) {
        StoredProfessionalAssessment assessment = assessments.findById(assessmentId).orElseThrow(() ->
                new ResponsibilityAnalysisUnavailableException(ResponsibilityAnalysisUnavailableException.Reason.ASSESSMENT_NOT_FOUND,
                        "no Professional assessment exists with that id"));
        // Access is checked before reuse: a stored snapshot is immutable history, but the right to see it ends
        // when the entitlement is refunded or revoked. The snapshot is kept, never deleted or regenerated.
        boolean entitled = purchases.findEntitlement(assessmentId).map(ProfessionalReportEntitlement::status)
                .filter(status -> status == ProfessionalEntitlementStatus.ACTIVE).isPresent();
        if (!entitled) {
            throw new ResponsibilityAnalysisUnavailableException(ResponsibilityAnalysisUnavailableException.Reason.NOT_ENTITLED,
                    "responsibility analysis requires an ACTIVE entitlement for the assessment");
        }
        Optional<ProfessionalResponsibilityAnalysisSnapshot> existing = store.findCompletedByAssessmentId(assessmentId);
        if (existing.isPresent()) {
            trace.event(JourneyType.PROFESSIONAL, TraceStage.RESPONSIBILITY_ANALYSIS_REUSED, () -> lifecycle(existing.get(), false));
            return new ProfessionalResponsibilityAnalysisOutcome(existing.get(), true);
        }
        ProfessionalResponsibilityJudgmentProvider judgmentProvider = provider.get().orElseThrow(() ->
                new ResponsibilityAnalysisUnavailableException(ResponsibilityAnalysisUnavailableException.Reason.PROVIDER_NOT_CONFIGURED,
                        "no responsibility judgment provider is configured for this environment"));
        var state = assessment.state();
        var request = new ProfessionalResponsibilityAnalysisRequest(assessmentId, state.profession(),
                state.workDescription(), selection.map(chosen -> ResponsibilitySelection.selectExplicitly(
                        state.responsibilities(), chosen)).orElseGet(() -> ResponsibilitySelection.select(
                        state.responsibilities())), state.skills());
        Instant startedAt = Instant.now(clock);

        ProfessionalResponsibilityJudgmentResult judgments = attempt(assessmentId, ResponsibilityAnalysisFailure.Stage.PROVIDER,
                () -> {
                    var result = judgmentProvider.judge(request);
                    if (!ProfessionalResponsibilityPolicyV1.SUPPORTED_SCHEMA_VERSION.equals(result.schemaVersion())) {
                        throw new IllegalStateException("unsupported responsibility schema " + result.schemaVersion());
                    }
                    if (!result.answers(request)) {
                        throw new IllegalStateException("the provider result does not correspond to the request");
                    }
                    return result;
                });
        ProfessionalResponsibilityClassificationResult classification = attempt(assessmentId,
                ResponsibilityAnalysisFailure.Stage.CLASSIFICATION, () -> policy.classify(judgments));
        ProfessionalResponsibilityAnalysisSnapshot candidate = attempt(assessmentId,
                ResponsibilityAnalysisFailure.Stage.CLASSIFICATION,
                () -> ResponsibilityAnalysisSnapshots.create(UUID.randomUUID(), assessmentId, request, judgments,
                        classification, startedAt, Instant.now(clock)));
        ProfessionalResponsibilityAnalysisSnapshot stored = attempt(assessmentId, ResponsibilityAnalysisFailure.Stage.PERSISTENCE,
                () -> store.saveCompletedIfAbsent(candidate));
        boolean ours = stored.snapshotId().equals(candidate.snapshotId());
        trace.event(JourneyType.PROFESSIONAL, ours ? TraceStage.RESPONSIBILITY_ANALYSIS_PERSISTED
                : TraceStage.RESPONSIBILITY_ANALYSIS_REUSED, () -> {
                    // The provider was called on this request either way; only whose snapshot is canonical differs.
                    Map<String, Object> payload = lifecycle(stored, true);
                    if (!ours) {
                        payload.put("note", "another request completed first; its snapshot is canonical and was returned");
                    }
                    return payload;
                });
        LOGGER.info("Responsibility analysis journey=professional assessmentId={} outcome={} items={}", assessmentId,
                ours ? "PERSISTED" : "CONCURRENT_SNAPSHOT_RETURNED", stored.items().size());
        return new ProfessionalResponsibilityAnalysisOutcome(stored, !ours);
    }

    private <T> T attempt(UUID assessmentId, ResponsibilityAnalysisFailure.Stage stage, Supplier<T> work) {
        try {
            return work.get();
        }
        catch (RuntimeException failure) {
            var record = new ResponsibilityAnalysisFailure(UUID.randomUUID(), assessmentId, Instant.now(clock), stage,
                    failure.getClass().getName(), AssessmentTrace.safeMessage(failure),
                    ProfessionalResponsibilityPolicyV1.SUPPORTED_SCHEMA_VERSION, ProfessionalResponsibilityPolicyV1.POLICY_VERSION);
            trace.event(JourneyType.PROFESSIONAL, TraceStage.RESPONSIBILITY_ANALYSIS_FAILED, () -> Map.of(
                    "stage", stage, "errorType", record.errorType(),
                    "errorMessage", String.valueOf(record.errorMessage()), "completedSnapshotStored", false));
            try {
                store.recordFailure(record);
            }
            catch (RuntimeException recordingFailure) {
                LOGGER.warn("Responsibility analysis failure could not be recorded assessmentId={} error={}",
                        assessmentId, recordingFailure.getClass().getSimpleName());
            }
            LOGGER.warn("Responsibility analysis journey=professional assessmentId={} outcome=FAILED stage={} error={}",
                    assessmentId, stage, failure.getClass().getSimpleName());
            throw new ResponsibilityAnalysisFailedException(stage, "responsibility analysis failed at " + stage, failure);
        }
    }

    private static Map<String, Object> lifecycle(ProfessionalResponsibilityAnalysisSnapshot snapshot,
            boolean providerCalled) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("snapshotId", snapshot.snapshotId());
        payload.put("status", snapshot.status());
        payload.put("schemaVersion", snapshot.schemaVersion());
        payload.put("policyVersion", snapshot.policyVersion());
        payload.put("judgmentSource", snapshot.judgmentSource());
        payload.put("responsibilityCount", snapshot.items().size());
        payload.put("completedAt", snapshot.completedAt());
        payload.put("providerCalled", providerCalled);
        return payload;
    }
}
