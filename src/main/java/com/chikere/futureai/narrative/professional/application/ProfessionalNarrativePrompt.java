package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.decision.professional.DecisionFactorEffect;
import com.chikere.futureai.decision.professional.ProfessionalFactorDirection;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;

import java.util.List;
import java.util.OptionalInt;

/**
 * What a narrative provider actually receives: the decided measures, the selected factors in plain language and
 * the work the person described.
 *
 * <p>Deliberately narrower than {@link ProfessionalNarrativeContext}. The internal assessment status, judgement
 * scores, signed effects and internal dimension names stay in the context for validation and tracing, but are not
 * sent: the model is told not to repeat numbers, should not reinterpret a status partly derived from confidence,
 * and should describe factors in plain language. The factor that must lead the AI Exposure explanation is marked
 * instead of exposing the effects used to choose it.</p>
 */
public record ProfessionalNarrativePrompt(
        String profession,
        String workDescription,
        List<String> responsibilities,
        List<String> skills,
        OptionalInt yearsExperience,
        List<Measure> measures,
        List<Factor> exposureDrivers,
        List<Factor> protectiveFactors,
        AssessmentSource assessmentSource,
        String judgmentSchemaVersion,
        String decisionPolicyVersion) {

    public record Measure(ProfessionalMeasure measure, String name, double score, String band) { }

    /** @param dominant true for the factor the AI Exposure explanation must lead with */
    public record Factor(String dimensionId, String description, DecisionFactorEffect effect,
            ProfessionalFactorDirection direction, int rank, boolean dominant) { }

    public static ProfessionalNarrativePrompt from(ProfessionalNarrativeContext context) {
        List<String> dominant = DominantExposureFactor.of(context);
        return new ProfessionalNarrativePrompt(context.profession(), context.workDescription(),
                context.responsibilities(), context.skills(), context.yearsExperience(),
                context.measures().stream()
                        .map(value -> new Measure(value.measure(), value.name(), value.score(), value.band())).toList(),
                factors(context.exposureDrivers(), dominant),
                factors(context.protectiveFactors(), dominant),
                context.assessmentSource(), context.judgmentSchemaVersion(), context.decisionPolicyVersion());
    }

    private static List<Factor> factors(List<ProfessionalNarrativeContext.FactorContext> factors, List<String> dominant) {
        return factors.stream().map(factor -> new Factor(factor.dimensionId(), factor.description(), factor.effect(),
                factor.direction(), factor.rank(), factor.rank() == 1 && dominant.contains(factor.dimensionId())))
                .toList();
    }
}
