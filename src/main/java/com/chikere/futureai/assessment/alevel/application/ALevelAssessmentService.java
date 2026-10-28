package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.decision.alevel.ALevelProfileDecisionResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates exactly one {@code a-level-v2} judgment-provider call and one {@code a-level-decision-v2} call, then
 * stores an immutable snapshot. It never scores a subject combination itself. The provider sees only the subject
 * combination ({@link ALevelJudgmentInput}); the student's own context is stored with the assessment but never
 * judged. Historical v1 assessments are only ever read back, never decided again.
 */
public final class ALevelAssessmentService implements ALevelAssessmentSubmission {
    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelAssessmentService.class);

    private final ALevelJudgmentProvider judgmentProvider;
    private final ALevelDecisionPolicyV2 decisionPolicy;
    private final ALevelAssessmentStore store;
    private final Clock clock;
    private final JourneyMetrics metrics;
    private final AssessmentTrace trace;

    public ALevelAssessmentService(ALevelJudgmentProvider judgmentProvider,
            ALevelDecisionPolicyV2 decisionPolicy) {
        this(judgmentProvider, decisionPolicy, null, Clock.systemUTC());
    }

    public ALevelAssessmentService(ALevelJudgmentProvider judgmentProvider,
            ALevelDecisionPolicyV2 decisionPolicy, ALevelAssessmentStore store, Clock clock) {
        this(judgmentProvider, decisionPolicy, store, clock, JourneyMetrics.disabled());
    }

    public ALevelAssessmentService(ALevelJudgmentProvider judgmentProvider,
            ALevelDecisionPolicyV2 decisionPolicy, ALevelAssessmentStore store, Clock clock,
            JourneyMetrics metrics) {
        this(judgmentProvider, decisionPolicy, store, clock, metrics, AssessmentTrace.disabled());
    }

    public ALevelAssessmentService(ALevelJudgmentProvider judgmentProvider,
            ALevelDecisionPolicyV2 decisionPolicy, ALevelAssessmentStore store, Clock clock,
            JourneyMetrics metrics,
            AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
        this.judgmentProvider = Objects.requireNonNull(judgmentProvider, "judgmentProvider must not be null");
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy must not be null");
        this.store = store;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.metrics = Objects.requireNonNull(metrics, "metrics must not be null");
    }

    public ALevelAssessmentResult assess(ALevelAssessmentState state) {
        return execute(state).result();
    }

    @Override
    public UUID submitALevelAssessment(ALevelAssessmentState state) {
        if (store == null) {
            throw new IllegalStateException("ALevelAssessmentStore is required for submission");
        }
        Execution execution = execute(state);
        UUID id = UUID.randomUUID();
        trace.assessmentId(id);
        trace.timed(JourneyType.A_LEVEL, TraceStage.PERSISTENCE, "persistenceMs", () -> {
            store.save(new StoredALevelAssessment(id, Instant.now(clock), state,
                execution.judgments(), execution.result(), judgmentProvider.modelIdentifier()));
            return id;
        }, saved -> Map.of("assessmentId", saved, "saved", "assessment, judgments and deterministic result"));
        return id;
    }

    public Optional<StoredALevelAssessment> getALevelAssessment(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        if (store == null) {
            throw new IllegalStateException("ALevelAssessmentStore is required for retrieval");
        }
        return store.findById(id);
    }

    private Execution execute(ALevelAssessmentState state) {
        Objects.requireNonNull(state, "state must not be null");
        final AssessmentJudgmentSet judgments;
        try {
            trace.event(JourneyType.A_LEVEL, TraceStage.DECISION_MODEL_REQUEST, () -> decisionModelRequest(state));
            judgments = metrics.timed("judgment", "a-level", () -> trace.timed(JourneyType.A_LEVEL,
                    TraceStage.DECISION_MODEL_RESPONSE, "decisionModelMs",
                    () -> judgmentProvider.judge(ALevelJudgmentInput.of(state)),
                    judged -> judged));
            LOGGER.info("Judgment journey=alevel outcome=SUCCEEDED schemaVersion={}",
                    judgments.schemaVersion());
        }
        catch (RuntimeException failure) {
            LOGGER.warn("Judgment journey=alevel outcome=FAILED failureType={}",
                    failure.getClass().getSimpleName());
            metrics.judgmentFailed("a-level");
            throw failure;
        }
        ALevelProfileDecisionResult decision = trace.measured("decisionPolicyMs",
                () -> decisionPolicy.decide(judgments));
        ALevelAssessmentResult result = new ALevelAssessmentResult(
                decision, judgmentProvider.source(), judgments.schemaVersion(), decision.policyVersion());
        trace.event(JourneyType.A_LEVEL, TraceStage.DETERMINISTIC_ASSESSMENT, () -> deterministicAssessment(result));
        return new Execution(judgments, result);
    }

    private Map<String, Object> decisionModelRequest(ALevelAssessmentState state) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", judgmentProvider.source());
        payload.put("providerImplementation", judgmentProvider.getClass().getName());
        payload.put("model", judgmentProvider.modelIdentifier().orElse(null));
        payload.put("fixtureId", judgmentProvider.fixtureId().orElse(null));
        payload.put("assessmentState", state);
        payload.put("judgmentInput", ALevelJudgmentInput.of(state));
        payload.put("note", "Only judgmentInput (the subject combination) is judged; the rest of the state is never "
                + "sent. The exact provider request follows as DECISION_MODEL_PROVIDER_REQUEST when the provider is "
                + "Jev.");
        return payload;
    }

    private Map<String, Object> deterministicAssessment(ALevelAssessmentResult result) {
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

    private record Execution(AssessmentJudgmentSet judgments, ALevelAssessmentResult result) {
    }
}
