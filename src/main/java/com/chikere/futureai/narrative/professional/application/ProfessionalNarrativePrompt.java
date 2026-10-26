package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.decision.professional.DecisionFactorEffect;

import java.util.List;
import java.util.OptionalInt;

/**
 * What a narrative provider actually receives: customer-facing meaning only.
 *
 * <p>The decided measures by display name and band, the selected factors as their plain visitor-facing description
 * with a natural-language effect, rank and dominant flag, and the work the person described. No implementation
 * vocabulary is sent: no dimension ids, direction or effect codes, internal dimension labels, judgement scores,
 * signed effects, assessment status or schema and policy versions. All of that stays in
 * {@link ProfessionalNarrativeContext} for validation and tracing.</p>
 */
public record ProfessionalNarrativePrompt(
        String profession,
        String workDescription,
        List<String> responsibilities,
        List<String> skills,
        OptionalInt yearsExperience,
        List<Measure> measures,
        List<Factor> factorsRaisingAiExposure,
        List<Factor> factorsLoweringAiExposure) {

    public record Measure(String name, double score, String band) { }

    /**
     * @param effect   "raises AI Exposure" or "lowers AI Exposure"
     * @param dominant true for the factor the AI Exposure explanation must include
     */
    public record Factor(String description, String effect, int rank, boolean dominant) { }

    public static ProfessionalNarrativePrompt from(ProfessionalNarrativeContext context) {
        List<String> dominant = DominantExposureFactor.of(context);
        return new ProfessionalNarrativePrompt(context.profession(), context.workDescription(),
                context.responsibilities(), context.skills(), context.yearsExperience(),
                context.measures().stream().map(value -> new Measure(value.name(), value.score(), value.band())).toList(),
                factors(context.exposureDrivers(), dominant),
                factors(context.protectiveFactors(), dominant));
    }

    private static List<Factor> factors(List<ProfessionalNarrativeContext.FactorContext> factors, List<String> dominant) {
        return factors.stream().map(factor -> new Factor(factor.description(),
                factor.effect() == DecisionFactorEffect.INCREASES_AI_EXPOSURE ? "raises AI Exposure" : "lowers AI Exposure",
                factor.rank(), factor.rank() == 1 && dominant.contains(factor.dimensionId())))
                .toList();
    }
}
