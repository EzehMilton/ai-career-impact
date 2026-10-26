package com.chikere.futureai.decision.professional;

import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;

/**
 * A versioned, deterministic Professional decision policy. The version it reports is stored with every
 * assessment it decides, and a stored decision is never recomputed under a different version.
 */
public sealed interface ProfessionalDecisionPolicy permits ProfessionalDecisionPolicyV1, ProfessionalDecisionPolicyV2 {

    String policyVersion();

    ProfessionalDecisionResult decide(AssessmentJudgmentSet judgmentSet);
}
