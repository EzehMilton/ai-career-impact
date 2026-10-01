package com.chikere.futureai.narrative.university.application;

import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;

import java.util.Comparator;

public final class UniversityNarrativeContextFactory {

    public UniversityNarrativeContext create(StoredUniversityAssessment assessment) {
        var state = assessment.state();
        var result = assessment.result();
        var decision = result.decision();
        var headline = decision.assessmentDecision();
        var measures = decision.measures().values().stream()
                .sorted(Comparator.comparing(measure -> measure.measure().ordinal()))
                .map(measure -> new UniversityNarrativeContext.MeasureContext(
                        measure.measure(), measure.indicator(), measure.band(), measure.confidence()))
                .toList();
        var strengths = decision.topStrengths().stream()
                .map(factor -> new UniversityNarrativeContext.FactorContext(
                        factor.dimensionId(), factor.effect(), factor.direction()))
                .toList();
        var opportunities = decision.topDevelopmentOpportunities().stream()
                .map(factor -> new UniversityNarrativeContext.FactorContext(
                        factor.dimensionId(), factor.effect(), factor.direction()))
                .toList();
        return new UniversityNarrativeContext(state.courseTitle(), state.courseDescription(),
                state.keyModules(), state.skillsDeveloped(), state.preferredCareerAreas(),
                state.institution(), state.country(), headline.status(), headline.confidence(),
                measures, strengths, opportunities, result.source(), result.judgmentSchemaVersion(),
                result.decisionPolicyVersion());
    }
}
