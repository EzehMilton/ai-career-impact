package com.chikere.futureai.decision.professional;

import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.trace.AssessmentTrace;

import java.util.OptionalDouble;

/**
 * The current Professional decision policy. It changes factor materiality only.
 *
 * <p>Score formulas, weights, transformations, bands, band thresholds, confidence, assessment status and
 * information sufficiency are identical to {@link ProfessionalDecisionPolicyV1}, so the same judgments
 * produce the same four scores and bands under both versions.</p>
 *
 * <p>The one difference: an AI Exposure factor is selected only when the absolute value of its signed
 * effect is at least {@link #MATERIALITY_THRESHOLD}, about 0.15 points on the 0-10 AI Exposure score
 * (score = 5 + 10 x the sum of signed effects). The filter runs before the factors are split by sign,
 * ranked and limited to three per side, so either side may be legitimately empty. A factor below the
 * threshold still contributes to the score; it is just too small to present.</p>
 */
public final class ProfessionalDecisionPolicyV2 implements ProfessionalDecisionPolicy {

    public static final String POLICY_VERSION = "professional-decision-v2";

    /** The minimum absolute signed effect for an AI Exposure factor to be selected. */
    public static final double MATERIALITY_THRESHOLD = 0.015;

    /** Effects within this distance below the threshold are treated as equal to it. */
    public static final double MATERIALITY_TOLERANCE = ProfessionalDecisionEngine.MATERIALITY_TOLERANCE;

    private final ProfessionalDecisionEngine engine;

    public ProfessionalDecisionPolicyV2() {
        this(AssessmentTrace.disabled());
    }

    /** The trace only observes the values this policy computes; it never changes a decision. */
    public ProfessionalDecisionPolicyV2(AssessmentTrace trace) {
        this.engine = new ProfessionalDecisionEngine(POLICY_VERSION, OptionalDouble.of(MATERIALITY_THRESHOLD), trace);
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
