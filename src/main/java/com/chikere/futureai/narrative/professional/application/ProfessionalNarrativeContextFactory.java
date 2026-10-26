package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionFactor;
import com.chikere.futureai.decision.professional.ProfessionalFactorDescriptions;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.judgment.domain.DimensionJudgment;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Builds the narrative input from what Java already decided; it selects, ranks and scores nothing itself. */
public final class ProfessionalNarrativeContextFactory {

    public ProfessionalNarrativeContext create(StoredProfessionalAssessment assessment) {
        var state = assessment.state();
        var result = assessment.result();
        var decision = result.decision();
        var headline = decision.assessmentDecision();
        Map<String, String> dimensionNames = assessment.judgments().judgments().stream()
                .collect(Collectors.toMap(DimensionJudgment::dimensionId, DimensionJudgment::name, (a, b) -> a));
        var measures = decision.measures().values().stream()
                .sorted(Comparator.comparing(measure -> measure.measure().ordinal()))
                .map(measure -> new ProfessionalNarrativeContext.MeasureContext(measure.measure(),
                        measureName(measure.measure()), measure.indicator(), band(measure.band().name())))
                .toList();
        return new ProfessionalNarrativeContext(
                state.profession(), state.workDescription(), state.responsibilities(), state.skills(),
                state.yearsOfExperience(), headline.status(), measures,
                factors(decision.topExposureDrivers(), dimensionNames::get),
                factors(decision.topProtectiveFactors(), dimensionNames::get),
                result.source(), result.judgmentSchemaVersion(), result.decisionPolicyVersion());
    }

    /** The policy already ordered each list strongest first, so a factor's rank is its position. */
    private static List<ProfessionalNarrativeContext.FactorContext> factors(List<ProfessionalDecisionFactor> selected,
            Function<String, String> dimensionName) {
        return IntStream.range(0, selected.size()).mapToObj(index -> {
            var factor = selected.get(index);
            String name = dimensionName.apply(factor.dimensionId());
            return new ProfessionalNarrativeContext.FactorContext(factor.dimensionId(),
                    name == null ? factor.dimensionId() : name,
                    ProfessionalFactorDescriptions.describe(factor.direction()), factor.effect(), factor.direction(),
                    index + 1, factor.score(), factor.signedEffect());
        }).toList();
    }

    private static String measureName(ProfessionalMeasure measure) {
        return switch (measure) {
            case AI_EXPOSURE -> "AI Exposure";
            case HUMAN_ADVANTAGE -> "Human Advantage";
            case AI_COMPLEMENTARITY -> "AI Complementarity";
            case ROLE_ADAPTABILITY -> "Role Adaptability";
        };
    }

    private static String band(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }
}
