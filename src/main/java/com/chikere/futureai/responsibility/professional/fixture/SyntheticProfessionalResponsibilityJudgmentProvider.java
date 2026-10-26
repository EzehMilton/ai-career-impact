package com.chikere.futureai.responsibility.professional.fixture;

import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentException;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSource;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/**
 * SYNTHETIC responsibility judgments for automated tests. Every value here is hand-authored to exercise one
 * shape of input; none is, or imitates, a real Jev result.
 *
 * <p>Only the responsibilities in {@link #RESPONSIBILITIES} are known. Any other text is refused rather than
 * answered with invented values. Each judgment's value is the probability-weighted level of its distribution,
 * so the fixture keeps the same relationship real answers have.</p>
 */
public final class SyntheticProfessionalResponsibilityJudgmentProvider
        implements ProfessionalResponsibilityJudgmentProvider {

    public static final String SCHEMA_VERSION = "professional-responsibility-v1";

    /** The synthetic shapes, each a set of distributions for the five scored judgments plus clarity. */
    public enum Profile {
        CLEARLY_ANALYSABLE(0.95, d(0, .1, .5, .3, .1), d(.1, .4, .4, .1, 0), d(.2, .5, .3, 0, 0), d(.8, .2, 0, 0, 0), d(.1, .5, .4, 0, 0)),
        VAGUE(0.15, d(.2, .2, .2, .2, .2), d(.2, .2, .2, .2, .2), d(.2, .2, .2, .2, .2), d(.2, .2, .2, .2, .2), d(.2, .2, .2, .2, .2)),
        HIGH_CONTEXTUAL_JUDGEMENT(0.9, d(0, .3, .5, .2, 0), d(0, 0, .1, .4, .5), d(.1, .3, .4, .2, 0), d(.7, .3, 0, 0, 0), d(0, .1, .4, .4, .1)),
        HIGH_HUMAN_INTERACTION(0.9, d(.3, .5, .2, 0, 0), d(0, .2, .4, .3, .1), d(0, 0, .1, .3, .6), d(.2, .4, .3, .1, 0), d(0, .2, .5, .3, 0)),
        HIGH_PHYSICAL_PRESENCE(0.9, d(.5, .4, .1, 0, 0), d(0, .3, .4, .3, 0), d(.1, .3, .4, .2, 0), d(0, 0, .05, .25, .7), d(0, .1, .3, .4, .2)),
        HIGH_OUTCOME_ACCOUNTABILITY(0.9, d(0, .2, .5, .3, 0), d(0, .1, .4, .4, .1), d(.1, .3, .4, .2, 0), d(.4, .4, .2, 0, 0), d(0, 0, .05, .35, .6)),
        HIGH_ASSISTANCE_SCOPE(0.95, d(0, 0, .05, .35, .6), d(.4, .5, .1, 0, 0), d(.6, .4, 0, 0, 0), d(.9, .1, 0, 0, 0), d(.3, .6, .1, 0, 0));

        private final double describedClearly;
        private final double[][] distributions;

        Profile(double describedClearly, double[]... distributions) {
            this.describedClearly = describedClearly;
            this.distributions = distributions;
        }
    }

    /** Synthetic responsibility texts and the shape each one exercises. */
    public static final Map<String, Profile> RESPONSIBILITIES = Map.of(
            "Synthetic: reconcile the weekly supplier spreadsheet against the order system", Profile.CLEARLY_ANALYSABLE,
            "Synthetic: do various things as needed", Profile.VAGUE,
            "Synthetic: decide how to prioritise conflicting requests during an outage", Profile.HIGH_CONTEXTUAL_JUDGEMENT,
            "Synthetic: support distressed customers through a complaint", Profile.HIGH_HUMAN_INTERACTION,
            "Synthetic: install and test equipment on site", Profile.HIGH_PHYSICAL_PRESENCE,
            "Synthetic: sign off safety-critical release approvals", Profile.HIGH_OUTCOME_ACCOUNTABILITY,
            "Synthetic: draft first versions of routine status summaries from tracker data", Profile.HIGH_ASSISTANCE_SCOPE);

    private final double[] regulatedContext;

    /** @param regulatedContext the synthetic distribution returned for the role-level regulated-context judgment */
    public SyntheticProfessionalResponsibilityJudgmentProvider(double... regulatedContext) {
        this.regulatedContext = regulatedContext.length == 0 ? d(.4, .4, .2, 0, 0) : regulatedContext.clone();
    }

    @Override
    public ProfessionalResponsibilityJudgmentResult judge(ProfessionalResponsibilityAnalysisRequest request) {
        List<ResponsibilityJudgmentSet> sets = new ArrayList<>();
        int index = 1;
        for (Map.Entry<String, String> responsibility : request.keyedResponsibilities().entrySet()) {
            Profile profile = RESPONSIBILITIES.get(responsibility.getValue());
            if (profile == null) {
                throw new ResponsibilityJudgmentException("no synthetic judgments exist for responsibility "
                        + responsibility.getKey() + "; only the synthetic catalogue is supported");
            }
            double[][] p = profile.distributions;
            sets.add(new ResponsibilityJudgmentSet(index++, responsibility.getKey(), responsibility.getValue(),
                    profile.describedClearly, judgment(p[0]), judgment(p[1]), judgment(p[2]), judgment(p[3]),
                    judgment(p[4])));
        }
        return new ProfessionalResponsibilityJudgmentResult(SCHEMA_VERSION, ResponsibilityJudgmentSource.SYNTHETIC_FIXTURE,
                Optional.empty(), Optional.empty(), OptionalLong.empty(), OptionalLong.empty(), judgment(regulatedContext),
                sets);
    }

    @Override
    public ResponsibilityJudgmentSource source() {
        return ResponsibilityJudgmentSource.SYNTHETIC_FIXTURE;
    }

    private static ResponsibilityScoredJudgment judgment(double[] probabilities) {
        Map<Integer, Double> distribution = new LinkedHashMap<>();
        double value = 0;
        int nearest = 0;
        for (int level = 0; level < probabilities.length; level++) {
            distribution.put(level, probabilities[level]);
            value += level * probabilities[level];
            if (probabilities[level] > probabilities[nearest]) {
                nearest = level;
            }
        }
        // A synthetic concentration figure: the share of the most likely level. Not Jev's statistic.
        return new ResponsibilityScoredJudgment(value, distribution, OptionalDouble.of(probabilities[nearest]),
                OptionalInt.of(nearest), Optional.empty());
    }

    private static double[] d(double... probabilities) {
        return probabilities;
    }
}
