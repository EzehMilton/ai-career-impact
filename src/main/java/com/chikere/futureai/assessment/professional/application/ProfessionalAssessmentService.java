package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.util.Objects;

public final class ProfessionalAssessmentService {

    private final ProfessionalJudgmentProvider judgmentProvider;
    private final ProfessionalDecisionPolicyV1 decisionPolicy;

    public ProfessionalAssessmentService(
            ProfessionalJudgmentProvider judgmentProvider,
            ProfessionalDecisionPolicyV1 decisionPolicy) {
        this.judgmentProvider = Objects.requireNonNull(judgmentProvider, "judgmentProvider must not be null");
        this.decisionPolicy = Objects.requireNonNull(decisionPolicy, "decisionPolicy must not be null");
    }

    public ProfessionalAssessmentResult assess(ProfessionalAssessmentState state) {
        Objects.requireNonNull(state, "state must not be null");
        AssessmentJudgmentSet judgments = judgmentProvider.judge(state);
        ProfessionalDecisionResult decision = decisionPolicy.decide(judgments);
        return new ProfessionalAssessmentResult(
                decision,
                judgmentProvider.source(),
                judgments.schemaVersion(),
                decision.policyVersion());
    }
}
