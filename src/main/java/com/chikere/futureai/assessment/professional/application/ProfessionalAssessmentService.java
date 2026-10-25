package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;

public final class ProfessionalAssessmentService implements ProfessionalAssessmentSubmission {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalAssessmentService.class);

    private final ProfessionalJudgmentProvider judgmentProvider;
    private final ProfessionalDecisionPolicyV1 decisionPolicy;
    private final ProfessionalAssessmentStore store;
    private final Clock clock;
    private final JourneyMetrics metrics;
    private final AssessmentTrace trace;

    public ProfessionalAssessmentService(
            ProfessionalJudgmentProvider judgmentProvider,
            ProfessionalDecisionPolicyV1 decisionPolicy) {
        this(judgmentProvider, decisionPolicy, null, Clock.systemUTC());
    }

    public ProfessionalAssessmentService(
            ProfessionalJudgmentProvider judgmentProvider,
            ProfessionalDecisionPolicyV1 decisionPolicy,
            ProfessionalAssessmentStore store,
            Clock clock) {
        this(judgmentProvider, decisionPolicy, store, clock, JourneyMetrics.disabled());
    }

    public ProfessionalAssessmentService(
            ProfessionalJudgmentProvider judgmentProvider,
            ProfessionalDecisionPolicyV1 decisionPolicy,
            ProfessionalAssessmentStore store,
            Clock clock,
            JourneyMetrics metrics) {
        this(judgmentProvider, decisionPolicy, store, clock, metrics, AssessmentTrace.disabled());
    }

    public ProfessionalAssessmentService(
            ProfessionalJudgmentProvider judgmentProvider,
            ProfessionalDecisionPolicyV1 decisionPolicy,
            ProfessionalAssessmentStore store,
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

    public ProfessionalAssessmentResult assess(ProfessionalAssessmentState state) {
        return execute(state).result();
    }

    @Override
    public UUID submitProfessionalAssessment(ProfessionalAssessmentState state) {
        return submitProfessionalAssessment(state, ProfessionalAssessmentInputMetadata.manual());
    }

    @Override
    public UUID submitProfessionalAssessment(ProfessionalAssessmentState state,
            ProfessionalAssessmentInputMetadata inputMetadata) {
        Objects.requireNonNull(inputMetadata, "inputMetadata must not be null");
        if (store == null) {
            throw new IllegalStateException("ProfessionalAssessmentStore is required for submission");
        }
        Execution execution = execute(state);
        UUID id = UUID.randomUUID();
        trace.assessmentId(id);
        trace.timed(JourneyType.PROFESSIONAL, TraceStage.PERSISTENCE, "persistenceMs", () -> {
            store.save(new StoredProfessionalAssessment(id, Instant.now(clock), state,
                execution.judgments(), execution.result(), judgmentProvider.modelIdentifier(), inputMetadata));
            return id;
        }, saved -> Map.of("assessmentId", saved, "saved", "assessment, judgments and deterministic result"));
        return id;
    }

    public Optional<StoredProfessionalAssessment> getProfessionalAssessment(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        if (store == null) {
            throw new IllegalStateException("ProfessionalAssessmentStore is required for retrieval");
        }
        return store.findById(id);
    }

    private Execution execute(ProfessionalAssessmentState state) {
        Objects.requireNonNull(state, "state must not be null");
        final AssessmentJudgmentSet judgments;
        try {
            trace.event(JourneyType.PROFESSIONAL, TraceStage.DECISION_MODEL_REQUEST, () -> decisionModelRequest(state));
            judgments = metrics.timed("judgment", "professional", () -> trace.timed(JourneyType.PROFESSIONAL,
                    TraceStage.DECISION_MODEL_RESPONSE, "decisionModelMs", () -> judgmentProvider.judge(state),
                    judged -> judged));
            LOGGER.info("Judgment journey=professional outcome=SUCCEEDED schemaVersion={}",
                    judgments.schemaVersion());
        }
        catch (RuntimeException failure) {
            LOGGER.warn("Judgment journey=professional outcome=FAILED failureType={}",
                    failure.getClass().getSimpleName());
            metrics.judgmentFailed("professional");
            throw failure;
        }
        ProfessionalDecisionResult decision = trace.measured("decisionPolicyMs",
                () -> decisionPolicy.decide(judgments));
        ProfessionalAssessmentResult result = new ProfessionalAssessmentResult(
                decision,
                judgmentProvider.source(),
                judgments.schemaVersion(),
                decision.policyVersion());
        trace.event(JourneyType.PROFESSIONAL, TraceStage.DETERMINISTIC_ASSESSMENT,
                () -> deterministicAssessment(result));
        return new Execution(judgments, result);
    }

    private Map<String, Object> decisionModelRequest(ProfessionalAssessmentState state) {
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

    private Map<String, Object> deterministicAssessment(ProfessionalAssessmentResult result) {
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

    private record Execution(AssessmentJudgmentSet judgments, ProfessionalAssessmentResult result) {}
}
