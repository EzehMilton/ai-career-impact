package com.chikere.futureai.decision.professional;

import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.trace.AssessmentTrace;

import java.util.OptionalDouble;

/**
 * The original Professional decision policy: the shared score formulas, weights, bands, thresholds and
 * confidence rules, with any non-zero AI Exposure factor eligible for selection.
 *
 * <p>Superseded by {@link ProfessionalDecisionPolicyV2} for new assessments. It is kept so the version
 * stored with historical assessments stays defined; those assessments keep the factors saved with them and
 * are never recomputed.</p>
 */
public final class ProfessionalDecisionPolicyV1 implements ProfessionalDecisionPolicy {

    public static final String POLICY_VERSION = "professional-decision-v1";

    private final ProfessionalDecisionEngine engine;

    public ProfessionalDecisionPolicyV1() {
        this(AssessmentTrace.disabled());
    }

    /** The trace only observes the values this policy computes; it never changes a decision. */
    public ProfessionalDecisionPolicyV1(AssessmentTrace trace) {
        this.engine = new ProfessionalDecisionEngine(POLICY_VERSION, OptionalDouble.empty(), trace);
    }

    @Override
    public String policyVersion() {
        return POLICY_VERSION;
    }

    @Override
    public ProfessionalDecisionResult decide(AssessmentJudgmentSet judgmentSet) {
        return engine.decide(judgmentSet);
    }

    public static IndicatorBand indicatorBand(double indicator) {
        return ProfessionalDecisionEngine.indicatorBand(indicator);
    }
}
