package com.chikere.futureai.responsibility.professional.policy;

import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;

import java.util.function.Function;

/** The five scored responsibility judgments, in schema order. */
public enum ResponsibilityJudgmentKind {
    ASSISTANCE_SCOPE(ResponsibilityJudgmentSet::assistanceScope),
    CONTEXTUAL_JUDGEMENT(ResponsibilityJudgmentSet::contextualJudgement),
    HUMAN_INTERACTION(ResponsibilityJudgmentSet::humanInteraction),
    PHYSICAL_PRESENCE(ResponsibilityJudgmentSet::physicalPresence),
    OUTCOME_ACCOUNTABILITY(ResponsibilityJudgmentSet::outcomeAccountability);

    private final Function<ResponsibilityJudgmentSet, ResponsibilityScoredJudgment> judgment;

    ResponsibilityJudgmentKind(Function<ResponsibilityJudgmentSet, ResponsibilityScoredJudgment> judgment) {
        this.judgment = judgment;
    }

    public double value(ResponsibilityJudgmentSet set) {
        return judgment.apply(set).value();
    }
}
