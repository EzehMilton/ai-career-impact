package com.chikere.futureai.responsibility.professional.analysis;

import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSource;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * HAND-BUILT test judgments for any request: fixed, valid values with distributions, labelled as a synthetic
 * source. They exercise the lifecycle, not the classification, so their exact values carry no meaning.
 */
public final class HandBuiltResponsibilityJudgments {

    private HandBuiltResponsibilityJudgments() {
    }

    /** assistance 3.4, context 1.6, interaction 1.2, physical 0.2, accountability 2.0; regulated context 1.8. */
    public static ProfessionalResponsibilityJudgmentResult forRequest(ProfessionalResponsibilityAnalysisRequest request) {
        return forRequest(request, 3.4, 1.6, 1.2, 0.2, 2.0);
    }

    public static ProfessionalResponsibilityJudgmentResult forRequest(ProfessionalResponsibilityAnalysisRequest request,
            double assistance, double context, double interaction, double physical, double accountability) {
        List<ResponsibilityJudgmentSet> sets = new ArrayList<>();
        int index = 1;
        for (Map.Entry<String, String> responsibility : request.keyedResponsibilities().entrySet()) {
            sets.add(new ResponsibilityJudgmentSet(index++, responsibility.getKey(), responsibility.getValue(), 0.4,
                    judgment(assistance), judgment(context), judgment(interaction), judgment(physical),
                    judgment(accountability)));
        }
        return new ProfessionalResponsibilityJudgmentResult("professional-responsibility-v1",
                ResponsibilityJudgmentSource.SYNTHETIC_FIXTURE, Optional.of("hand-built"), Optional.of("req_hand_built"),
                OptionalLong.of(100), OptionalLong.of(20), judgment(1.8), sets);
    }

    /** A two-level distribution whose probability-weighted level equals the value. */
    public static ResponsibilityScoredJudgment judgment(double value) {
        int low = (int) Math.floor(value);
        double upper = value - low;
        Map<Integer, Double> distribution = upper == 0 ? Map.of(low, 1.0) : Map.of(low, 1 - upper, low + 1, upper);
        return new ResponsibilityScoredJudgment(value, distribution, OptionalDouble.of(0.7),
                OptionalInt.of(upper >= 0.5 ? low + 1 : low), Optional.of("Hand-built level label"));
    }
}
