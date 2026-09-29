package com.chikere.futureai.evaluation.professional;

import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

import java.util.Objects;

record ProfessionalEvaluationResult(
        ProfessionalEvaluationCase evaluationCase,
        AssessmentJudgmentSet judgments,
        ProfessionalDecisionResult decision) {

    ProfessionalEvaluationResult {
        Objects.requireNonNull(evaluationCase, "evaluationCase must not be null");
        Objects.requireNonNull(judgments, "judgments must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
    }
}
