package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.time.Clock;
import java.time.Instant;

public final class ProfessionalAssessmentService {

    private final ProfessionalJudgmentProvider judgmentProvider;
    private final ProfessionalDecisionPolicyV1 decisionPolicy;
    private final ProfessionalAssessmentStore store;
    private final Clock clock;

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
        this.judgmentProvider = Objects.requireNonNull(judgmentProvider, "judgmentProvider must not be null");
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy must not be null");
        this.store = store;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public ProfessionalAssessmentResult assess(ProfessionalAssessmentState state) {
        return execute(state).result();
    }

    public UUID submitProfessionalAssessment(ProfessionalAssessmentState state) {
        return submitProfessionalAssessment(state, ProfessionalAssessmentInputMetadata.manual());
    }

    public UUID submitProfessionalAssessment(ProfessionalAssessmentState state,
            ProfessionalAssessmentInputMetadata inputMetadata) {
        Objects.requireNonNull(inputMetadata, "inputMetadata must not be null");
        if (store == null) {
            throw new IllegalStateException("ProfessionalAssessmentStore is required for submission");
        }
        Execution execution = execute(state);
        UUID id = UUID.randomUUID();
        store.save(new StoredProfessionalAssessment(id, Instant.now(clock), state,
                execution.judgments(), execution.result(), judgmentProvider.modelIdentifier(), inputMetadata));
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
        AssessmentJudgmentSet judgments = judgmentProvider.judge(state);
        ProfessionalDecisionResult decision = decisionPolicy.decide(judgments);
        ProfessionalAssessmentResult result = new ProfessionalAssessmentResult(
                decision,
                judgmentProvider.source(),
                judgments.schemaVersion(),
                decision.policyVersion());
        return new Execution(judgments, result);
    }

    private record Execution(AssessmentJudgmentSet judgments, ProfessionalAssessmentResult result) {}
}
