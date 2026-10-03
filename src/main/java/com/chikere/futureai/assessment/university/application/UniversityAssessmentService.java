package com.chikere.futureai.assessment.university.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates exactly one judgment-provider call and one decision-policy call, then stores an
 * immutable snapshot. It never scores a course itself.
 */
public final class UniversityAssessmentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityAssessmentService.class);

    private final UniversityJudgmentProvider judgmentProvider;
    private final UniversityDecisionPolicyV1 decisionPolicy;
    private final UniversityAssessmentStore store;
    private final Clock clock;

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
        this.judgmentProvider = Objects.requireNonNull(judgmentProvider, "judgmentProvider must not be null");
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy must not be null");
        this.store = store;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public UniversityAssessmentResult assess(UniversityAssessmentState state) {
        return execute(state).result();
    }

    public UUID submitUniversityAssessment(UniversityAssessmentState state) {
        if (store == null) {
            throw new IllegalStateException("UniversityAssessmentStore is required for submission");
        }
        Execution execution = execute(state);
        UUID id = UUID.randomUUID();
        store.save(new StoredUniversityAssessment(id, Instant.now(clock), state,
                execution.judgments(), execution.result(), judgmentProvider.modelIdentifier()));
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
            judgments = judgmentProvider.judge(state);
            LOGGER.info("Judgment journey=university outcome=SUCCEEDED schemaVersion={}",
                    judgments.schemaVersion());
        }
        catch (RuntimeException failure) {
            LOGGER.warn("Judgment journey=university outcome=FAILED failureType={}",
                    failure.getClass().getSimpleName());
            throw failure;
        }
        UniversityDecisionResult decision = decisionPolicy.decide(judgments);
        UniversityAssessmentResult result = new UniversityAssessmentResult(
                decision, judgmentProvider.source(), judgments.schemaVersion(), decision.policyVersion());
        return new Execution(judgments, result);
    }

    private record Execution(AssessmentJudgmentSet judgments, UniversityAssessmentResult result) {
    }
}
