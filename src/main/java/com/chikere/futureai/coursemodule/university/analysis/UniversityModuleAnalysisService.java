package com.chikere.futureai.coursemodule.university.analysis;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentException;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentProvider;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleClassification;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityReportEntitlement;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Generates, or returns, the one immutable paid module analysis of a University assessment.
 *
 * <p>{@link #generateOrGet} first requires an ACTIVE entitlement, for reuse as well as generation. It then returns a
 * stored snapshot without calling anything, or resolves the module selection, calls the module provider exactly once
 * for up to eight modules, classifies them with the frozen policy and stores the complete snapshot. The course-level
 * assessment is only read: no score, band, factor, narrative or course-level judgment is recomputed or changed.</p>
 *
 * <p>Nothing is stored before generation succeeds, so a crash never leaves a stale pending record. A failure stores
 * no snapshot; it is recorded and may be retried by calling again. Nothing is retried automatically.</p>
 *
 * <p><b>Known characteristics, accepted deliberately:</b> two truly simultaneous first requests can each call the
 * provider, but only one snapshot can become canonical (the unique assessment constraint) and both callers return
 * it. Generation never runs from payment confirmation; the paid-report flow calls this service later.</p>
 */
public final class UniversityModuleAnalysisService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityModuleAnalysisService.class);
    private static final String TRACE_KIND = "UNIVERSITY_MODULE_ANALYSIS";

    private final UniversityAssessmentStore assessments;
    private final UniversityReportPurchaseUseCase purchases;
    private final Supplier<Optional<UniversityModuleJudgmentProvider>> provider;
    private final UniversityModuleClassifier classifier;
    private final UniversityModuleAnalysisStore store;
    private final Clock clock;
    private final AssessmentTrace trace;

    /** @param provider the module provider if one is configured for the active profile */
    public UniversityModuleAnalysisService(UniversityAssessmentStore assessments,
            UniversityReportPurchaseUseCase purchases, Supplier<Optional<UniversityModuleJudgmentProvider>> provider,
            UniversityModuleClassifier classifier, UniversityModuleAnalysisStore store, Clock clock,
            AssessmentTrace trace) {
        this.assessments = Objects.requireNonNull(assessments);
        this.purchases = Objects.requireNonNull(purchases);
        this.provider = Objects.requireNonNull(provider);
        this.classifier = Objects.requireNonNull(classifier);
        this.store = Objects.requireNonNull(store);
        this.clock = Objects.requireNonNull(clock);
        this.trace = Objects.requireNonNull(trace);
    }

    public UniversityModuleAnalysisOutcome generateOrGet(UUID assessmentId) {
        return generateOrGet(assessmentId, Optional.empty());
    }

    /**
     * As {@link #generateOrGet(UUID)}, with the customer's explicit choice of module ids (1-based course positions),
     * required when the course has more than eight modules and ignored when it has eight or fewer. A stored snapshot
     * is returned whatever selection is passed: it was made once and is never regenerated for a different choice.
     */
    public UniversityModuleAnalysisOutcome generateOrGet(UUID assessmentId, Optional<List<Integer>> selection) {
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Objects.requireNonNull(selection, "selection must not be null");
        return trace.scoped(JourneyType.UNIVERSITY, assessmentId, TRACE_KIND, () -> run(assessmentId, selection));
    }

    private UniversityModuleAnalysisOutcome run(UUID assessmentId, Optional<List<Integer>> selection) {
        StoredUniversityAssessment assessment = assessments.findById(assessmentId).orElseThrow(() -> denied(
                UniversityModuleAnalysisUnavailableException.Reason.ASSESSMENT_NOT_FOUND,
                "no University assessment exists with that id"));
        // Access is checked before reuse: a stored snapshot is immutable history, but the right to see it ends when
        // the entitlement is refunded or revoked. The snapshot is kept, never deleted or regenerated.
        boolean entitled = purchases.findEntitlement(assessmentId).map(UniversityReportEntitlement::status)
                .filter(status -> status == UniversityEntitlementStatus.ACTIVE).isPresent();
        if (!entitled) {
            throw denied(UniversityModuleAnalysisUnavailableException.Reason.NOT_ENTITLED,
                    "module analysis requires an ACTIVE entitlement for the assessment");
        }
        Optional<UniversityModuleAnalysisSnapshot> existing = store.findCompletedByAssessmentId(assessmentId);
        if (existing.isPresent()) {
            trace.event(JourneyType.UNIVERSITY, TraceStage.UNIVERSITY_MODULE_ANALYSIS_REUSED,
                    () -> lifecycle(existing.get(), false, selection.isPresent()));
            return new UniversityModuleAnalysisOutcome(existing.get(), true);
        }
        List<UniversityModuleSelection.SelectedModule> selected;
        try {
            selected = UniversityModuleSelection.resolve(assessment.state().keyModules(), selection);
        }
        catch (UniversityModuleAnalysisUnavailableException invalid) {
            throw denied(invalid.reason(), invalid.getMessage());
        }
        UniversityModuleJudgmentProvider judgmentProvider = provider.get().orElseThrow(() -> denied(
                UniversityModuleAnalysisUnavailableException.Reason.PROVIDER_NOT_CONFIGURED,
                "no module judgment provider is configured for this environment"));
        var state = assessment.state();
        var request = new UniversityModuleAnalysisRequest(assessmentId, state.courseTitle(), state.courseDescription(),
                selected.stream().map(UniversityModuleSelection.SelectedModule::title).toList(),
                state.skillsDeveloped());
        trace.event(JourneyType.UNIVERSITY, TraceStage.UNIVERSITY_MODULE_ANALYSIS_REQUEST, () -> Map.of(
                "entitlement", "ACTIVE", "selectedModuleIds", selected.stream()
                        .map(UniversityModuleSelection.SelectedModule::moduleId).toList(),
                "explicitSelection", selection.isPresent(), "policyVersion", classifier.policyVersion()));
        Instant startedAt = Instant.now(clock);

        UniversityModuleJudgmentResult judgments = attempt(assessmentId, () -> {
            var result = judgmentProvider.judge(request);
            if (!classifier.supportedSchemaVersion().equals(result.schemaVersion())) {
                throw new UniversityModuleJudgmentException("unsupported module schema " + result.schemaVersion());
            }
            if (!result.answers(request)) {
                throw new UniversityModuleJudgmentException("the provider result does not correspond to the request");
            }
            return result;
        });
        List<UniversityModuleClassification> classifications = attempt(assessmentId,
                UniversityModuleAnalysisFailure.Category.CLASSIFICATION, () -> classifier.classify(judgments));
        UniversityModuleAnalysisSnapshot candidate = attempt(assessmentId,
                UniversityModuleAnalysisFailure.Category.CLASSIFICATION,
                () -> snapshot(assessmentId, request, selected, judgments, classifications, startedAt));
        UniversityModuleAnalysisSnapshot stored = attempt(assessmentId,
                UniversityModuleAnalysisFailure.Category.PERSISTENCE, () -> store.saveCompletedIfAbsent(candidate));
        boolean ours = stored.snapshotId().equals(candidate.snapshotId());
        trace.event(JourneyType.UNIVERSITY, TraceStage.UNIVERSITY_MODULE_ANALYSIS_PERSISTED, () -> {
            Map<String, Object> payload = lifecycle(stored, true, selection.isPresent());
            if (!ours) {
                payload.put("note", "another request completed first; its snapshot is canonical and was returned");
            }
            return payload;
        });
        LOGGER.info("Module analysis journey=university assessmentId={} outcome={} modules={}", assessmentId,
                ours ? "PERSISTED" : "CONCURRENT_SNAPSHOT_RETURNED", stored.items().size());
        return new UniversityModuleAnalysisOutcome(stored, !ours);
    }

    private UniversityModuleAnalysisSnapshot snapshot(UUID assessmentId, UniversityModuleAnalysisRequest request,
            List<UniversityModuleSelection.SelectedModule> selected, UniversityModuleJudgmentResult judgments,
            List<UniversityModuleClassification> classifications, Instant startedAt) {
        if (classifications.size() != judgments.modules().size()) {
            throw new IllegalStateException("the policy must classify every module exactly once");
        }
        List<UniversityModuleAnalysisItem> items = new ArrayList<>();
        for (int index = 0; index < judgments.modules().size(); index++) {
            UniversityModuleJudgmentSet set = judgments.modules().get(index);
            UniversityModuleClassification classification = classifications.get(index);
            items.add(new UniversityModuleAnalysisItem(selected.get(index).moduleId(), set, classification,
                    classification.customerLabel()));
        }
        return new UniversityModuleAnalysisSnapshot(UUID.randomUUID(), assessmentId, judgments.schemaVersion(),
                classifier.policyVersion(), judgments.source(), judgments.model(), judgments.providerRequestId(),
                judgments.inputTokens(), judgments.outputTokens(), request.courseTitle(), request.courseDescription(),
                request.skillsDeveloped(), items, startedAt, Instant.now(clock));
    }

    /** A provider failure: transient when the call itself failed, invalid when its answers could not be accepted. */
    private <T> T attempt(UUID assessmentId, Supplier<T> work) {
        try {
            return work.get();
        }
        catch (UniversityModuleJudgmentException rejected) {
            throw failed(assessmentId, rejected.kind() == UniversityModuleJudgmentException.Kind.PROVIDER_CALL
                    ? UniversityModuleAnalysisFailure.Category.PROVIDER_TRANSIENT
                    : UniversityModuleAnalysisFailure.Category.PROVIDER_INVALID_RESPONSE, rejected);
        }
        catch (RuntimeException failure) {
            throw failed(assessmentId, UniversityModuleAnalysisFailure.Category.INTERNAL, failure);
        }
    }

    private <T> T attempt(UUID assessmentId, UniversityModuleAnalysisFailure.Category category, Supplier<T> work) {
        try {
            return work.get();
        }
        catch (RuntimeException failure) {
            throw failed(assessmentId, category, failure);
        }
    }

    private UniversityModuleAnalysisFailedException failed(UUID assessmentId,
            UniversityModuleAnalysisFailure.Category category, RuntimeException failure) {
        var record = new UniversityModuleAnalysisFailure(UUID.randomUUID(), assessmentId, Instant.now(clock), category,
                category.retryable(), failure.getClass().getName(), AssessmentTrace.safeMessage(failure),
                classifier.supportedSchemaVersion(), classifier.policyVersion());
        trace.event(JourneyType.UNIVERSITY, TraceStage.UNIVERSITY_MODULE_ANALYSIS_FAILED, () -> Map.of(
                "category", category, "retryable", record.retryable(), "errorType", record.errorType(),
                "errorMessage", String.valueOf(record.errorMessage()), "completedSnapshotStored", false));
        try {
            store.recordFailure(record);
        }
        catch (RuntimeException recordingFailure) {
            LOGGER.warn("Module analysis failure could not be recorded assessmentId={} error={}", assessmentId,
                    recordingFailure.getClass().getSimpleName());
        }
        LOGGER.warn("Module analysis journey=university assessmentId={} outcome=FAILED category={} error={}",
                assessmentId, category, failure.getClass().getSimpleName());
        return new UniversityModuleAnalysisFailedException(category, "module analysis failed: " + category, failure);
    }

    private UniversityModuleAnalysisUnavailableException denied(UniversityModuleAnalysisUnavailableException.Reason reason,
            String message) {
        trace.event(JourneyType.UNIVERSITY, TraceStage.UNIVERSITY_MODULE_ANALYSIS_DENIED,
                () -> Map.of("reason", reason, "providerCalled", false));
        return new UniversityModuleAnalysisUnavailableException(reason, message);
    }

    private static Map<String, Object> lifecycle(UniversityModuleAnalysisSnapshot snapshot, boolean providerCalled,
            boolean selectionSupplied) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("snapshotId", snapshot.snapshotId());
        payload.put("status", UniversityModuleAnalysisSnapshot.STATUS);
        payload.put("schemaVersion", snapshot.schemaVersion());
        payload.put("policyVersion", snapshot.policyVersion());
        payload.put("judgmentSource", snapshot.judgmentSource());
        payload.put("selectedModuleIds", snapshot.selectedModuleIds());
        payload.put("completedAt", snapshot.completedAt());
        payload.put("providerCalled", providerCalled);
        if (!providerCalled && selectionSupplied) {
            payload.put("selectionIgnored", "a completed snapshot exists; its stored selection is canonical");
        }
        return payload;
    }
}
