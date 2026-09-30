package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;

import java.util.Comparator;

public final class ProfessionalNarrativeContextFactory {

    public ProfessionalNarrativeContext create(StoredProfessionalAssessment assessment) {
        var state = assessment.state();
        var result = assessment.result();
        var decision = result.decision();
        var headline = decision.assessmentDecision();
        var measures = decision.measures().values().stream()
                .sorted(Comparator.comparing(measure -> measure.measure().ordinal()))
                .map(measure -> new ProfessionalNarrativeContext.MeasureContext(
                        measure.measure(), measure.indicator(), measure.band(), measure.confidence()))
                .toList();
        var drivers = decision.topExposureDrivers().stream()
                .map(factor -> new ProfessionalNarrativeContext.FactorContext(
                        factor.dimensionId(), factor.effect(), factor.direction()))
                .toList();
        var protective = decision.topProtectiveFactors().stream()
                .map(factor -> new ProfessionalNarrativeContext.FactorContext(
                        factor.dimensionId(), factor.effect(), factor.direction()))
                .toList();
        return new ProfessionalNarrativeContext(
                state.profession(), state.workDescription(), state.responsibilities(), state.skills(),
                state.yearsOfExperience(), headline.status(), headline.confidence(), measures,
                drivers, protective, result.source(), result.judgmentSchemaVersion(),
                result.decisionPolicyVersion());
    }
}
