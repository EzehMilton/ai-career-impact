package com.chikere.futureai.narrative.alevel.application;

import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;

import java.util.Comparator;

public final class ALevelNarrativeContextFactory {

    public ALevelNarrativeContext create(StoredALevelAssessment assessment) {
        var state = assessment.state();
        var result = assessment.result();
        var decision = result.decision();
        var headline = decision.assessmentDecision();
        var measures = decision.measures().values().stream()
                .sorted(Comparator.comparing(measure -> measure.measure().ordinal()))
                .map(measure -> new ALevelNarrativeContext.MeasureContext(
                        measure.measure(), measure.indicator(), measure.band(), measure.confidence()))
                .toList();
        var strengths = decision.topStrengths().stream()
                .map(factor -> new ALevelNarrativeContext.FactorContext(
                        factor.dimensionId(), factor.effect(), factor.direction()))
                .toList();
        var opportunities = decision.topDevelopmentOpportunities().stream()
                .map(factor -> new ALevelNarrativeContext.FactorContext(
                        factor.dimensionId(), factor.effect(), factor.direction()))
                .toList();
        return new ALevelNarrativeContext(state.selectedSubjects(), state.predictedGrades(),
                state.strengthsAndInterests(), state.preferredDegreeAreas(), state.preferredCareerAreas(),
                state.country(), state.additionalContext(), headline.status(), headline.confidence(),
                measures, strengths, opportunities, result.source(), result.judgmentSchemaVersion(),
                result.decisionPolicyVersion());
    }
}
