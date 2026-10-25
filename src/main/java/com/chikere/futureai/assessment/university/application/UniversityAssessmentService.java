package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates exactly one judgment-provider call and one decision-policy call, then stores an
 * immutable snapshot. It never scores a course itself.
 */
public final class UniversityAssessmentService implements UniversityAssessmentSubmission {
    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityAssessmentService.class);

    private final UniversityJudgmentProvider judgmentProvider;
    private final UniversityDecisionPolicyV1 decisionPolicy;
    private final UniversityAssessmentStore store;
    private final Clock clock;
    private final JourneyMetrics metrics;
    private final AssessmentTrace trace;

    public UniversityAssessmentService(
            UniversityJudgmentProvider judgmentProvider,
            UniversityDecisionPolicyV1 decisionPolicy) {
        this(judgmentProvider, decisionPolicy, null, Clock.systemUTC());
    }

    public UniversityAssessmentService(
            UniversityJudgmentProvider judgmentProvider,
            UniversityDecisionPolicyV1 decisionPolicy,
            UniversityAssessmentStore store,
            Clock clock) {
        this(judgmentProvider, decisionPolicy, store, clock, JourneyMetrics.disabled());
    }

    public UniversityAssessmentService(
            UniversityJudgmentProvider judgmentProvider,
            UniversityDecisionPolicyV1 decisionPolicy,
            UniversityAssessmentStore store,
            Clock clock,
            JourneyMetrics metrics) {
        this(judgmentProvider, decisionPolicy, store, clock, metrics, AssessmentTrace.disabled());
    }

    public UniversityAssessmentService(
            UniversityJudgmentProvider judgmentProvider,
            UniversityDecisionPolicyV1 decisionPolicy,
            UniversityAssessmentStore store,
            Clock clock,
            JourneyMetrics metrics,
            AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
        this.judgmentProvider = Objects.requireNonNull(judgmentProvider, "judgmentProvider must not be null");
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy must not be null");
        this.store = store;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    public UniversityAssessmentResult assess(UniversityAssessmentState state) {
        return execute(state).result();
    }

    @Override
    public UUID submitUniversityAssessment(UniversityAssessmentState state) {
        if (store == null) {
            throw new IllegalStateException("UniversityAssessmentStore is required for submission");
        }
        Execution execution = execute(state);
        UUID id = UUID.randomUUID();
        trace.assessmentId(id);
        trace.timed(JourneyType.UNIVERSITY, TraceStage.PERSISTENCE, "persistenceMs", () -> {
            store.save(new StoredUniversityAssessment(id, Instant.now(clock), state,
                execution.judgments(), execution.result(), judgmentProvider.modelIdentifier()));
            return id;
        }, saved -> Map.of("assessmentId", saved, "saved", "assessment, judgments and deterministic result"));
        return id;
    }

    public Optional<StoredUniversityAssessment> getUniversityAssessment(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        if (store == null) {
            throw new IllegalStateException("UniversityAssessmentStore is required for retrieval");
        }
        return store.findById(id);
    }

    private Execution execute(UniversityAssessmentState state) {
        Objects.requireNonNull(state, "state must not be null");
        final AssessmentJudgmentSet judgments;
        try {
            trace.event(JourneyType.UNIVERSITY, TraceStage.DECISION_MODEL_REQUEST, () -> decisionModelRequest(state));
            judgments = metrics.timed("judgment", "university", () -> trace.timed(JourneyType.UNIVERSITY,
                    TraceStage.DECISION_MODEL_RESPONSE, "decisionModelMs", () -> judgmentProvider.judge(state),
                    judged -> judged));
            LOGGER.info("Judgment journey=university outcome=SUCCEEDED schemaVersion={}",
                    judgments.schemaVersion());
        }
        catch (RuntimeException failure) {
            LOGGER.warn("Judgment journey=university outcome=FAILED failureType={}",
                    failure.getClass().getSimpleName());
            metrics.judgmentFailed("university");
            throw failure;
        }
        UniversityDecisionResult decision = trace.measured("decisionPolicyMs", () -> decisionPolicy.decide(judgments));
        UniversityAssessmentResult result = new UniversityAssessmentResult(
                decision, judgmentProvider.source(), judgments.schemaVersion(), decision.policyVersion());
        trace.event(JourneyType.UNIVERSITY, TraceStage.DETERMINISTIC_ASSESSMENT, () -> deterministicAssessment(result));
        return new Execution(judgments, result);
    }

    private Map<String, Object> decisionModelRequest(UniversityAssessmentState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", judgmentProvider.source());
        payload.put("providerImplementation", judgmentProvider.getClass().getName());
        payload.put("model", judgmentProvider.modelIdentifier().orElse(null));
        payload.put("fixtureId", judgmentProvider.fixtureId().orElse(null));
        payload.put("assessmentState", state);
        payload.put("note", "The exact provider request (schema questions and wire state) follows as "
                + "DECISION_MODEL_PROVIDER_REQUEST when the provider is Jev.");
        return payload;
    }

    private Map<String, Object> deterministicAssessment(UniversityAssessmentResult result) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("boundary",
                "AI/Jev judgments -> Java decision policy -> FINAL DETERMINISTIC ASSESSMENT -> narrative AI");
        payload.put("provenance", Map.of(
                "decisionModelProvider", String.valueOf(result.source()),
                "decisionModel", judgmentProvider.modelIdentifier().orElse("none"),
                "judgmentSchemaVersion", result.judgmentSchemaVersion(),
                "decisionPolicyVersion", result.decisionPolicyVersion()));
        payload.put("result", result);
        return payload;
    }

    private record Execution(AssessmentJudgmentSet judgments, UniversityAssessmentResult result) {
    }
}
