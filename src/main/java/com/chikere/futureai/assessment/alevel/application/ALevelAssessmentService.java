package com.chikere.futureai.assessment.alevel.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.alevel.ALevelDecisionResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates exactly one judgment-provider call and one decision-policy call, then stores an
 * immutable snapshot. It never scores a subject combination itself.
 */
public final class ALevelAssessmentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelAssessmentService.class);

    private final ALevelJudgmentProvider judgmentProvider;
    private final ALevelDecisionPolicyV1 decisionPolicy;
    private final ALevelAssessmentStore store;
    private final Clock clock;

    public ALevelAssessmentService(ALevelJudgmentProvider judgmentProvider,
            ALevelDecisionPolicyV1 decisionPolicy) {
        this(judgmentProvider, decisionPolicy, null, Clock.systemUTC());
    }

    public ALevelAssessmentService(ALevelJudgmentProvider judgmentProvider,
            ALevelDecisionPolicyV1 decisionPolicy, ALevelAssessmentStore store, Clock clock) {
        this.judgmentProvider = Objects.requireNonNull(judgmentProvider, "judgmentProvider must not be null");
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy must not be null");
        this.store = store;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ALevelAssessmentResult assess(ALevelAssessmentState state) {
        return execute(state).result();
    }

    public UUID submitALevelAssessment(ALevelAssessmentState state) {
        if (store == null) {
            throw new IllegalStateException("ALevelAssessmentStore is required for submission");
        }
        Execution execution = execute(state);
        UUID id = UUID.randomUUID();
        store.save(new StoredALevelAssessment(id, Instant.now(clock), state,
                execution.judgments(), execution.result(), judgmentProvider.modelIdentifier()));
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
            judgments = judgmentProvider.judge(state);
            LOGGER.info("Judgment journey=alevel outcome=SUCCEEDED schemaVersion={}",
                    judgments.schemaVersion());
        }
        catch (RuntimeException failure) {
            LOGGER.warn("Judgment journey=alevel outcome=FAILED failureType={}",
                    failure.getClass().getSimpleName());
            throw failure;
        }
        ALevelDecisionResult decision = decisionPolicy.decide(judgments);
        ALevelAssessmentResult result = new ALevelAssessmentResult(
                decision, judgmentProvider.source(), judgments.schemaVersion(), decision.policyVersion());
        return new Execution(judgments, result);
    }

    private record Execution(AssessmentJudgmentSet judgments, ALevelAssessmentResult result) {
    }
}
