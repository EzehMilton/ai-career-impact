package com.chikere.futureai.narrative.professional.fixture;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.professional.ProfessionalFactorDirection;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.narrative.professional.application.NarrativeSource;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrative;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeContext;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;

import java.util.List;

/** Deterministic presentation templates for explicit offline UI development. */
public final class LocalFixtureProfessionalNarrativeGenerator implements ProfessionalNarrativeGenerator {

    @Override
    public ProfessionalNarrative generate(ProfessionalNarrativeContext context) {
        if (context.assessmentStatus() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return insufficientInformation(context);
        }
        String exposureBand = band(context, ProfessionalMeasure.AI_EXPOSURE);
        String complementarityBand = band(context, ProfessionalMeasure.AI_COMPLEMENTARITY);
        String protection = context.protectiveFactors().isEmpty()
                ? "the parts of the role that still need human context and accountability"
                : describe(context.protectiveFactors().getFirst().direction());
        return new ProfessionalNarrative(
                "A practical interpretation of your " + context.profession() + " assessment",
                "This local fixture explanation reflects the saved assessment: AI exposure is "
                        + exposureBand + " while scope for AI complementarity is " + complementarityBand + ".",
                "These measures describe the work you described as it is today; exposure and human value can "
                        + "exist at the same time.",
                exposureExplanation(context),
                "Human value remains visible where the work depends on " + protection + ".",
                "There may be scope to use AI for repeatable or information-heavy tasks while keeping human "
                        + "judgement, context and accountability in the loop.",
                "Role adaptability describes room in the work itself to evolve. It does not assess your motivation, "
                        + "ability to learn or personal circumstances.",
                List.of(
                        "Identify repeatable parts of your current work where an AI tool could assist under review.",
                        "Strengthen the human-facing and context-heavy responsibilities already present in the role.",
                        "Document decisions where human accountability and checking remain important.",
                        "Build practical confidence with AI tools that are relevant to your existing work."),
                "This is a development fixture explanation of a saved assessment, not a prediction of job loss "
                        + "or a claim of certainty about the future.");
    }

    @Override
    public NarrativeSource source() {
        return NarrativeSource.LOCAL_FIXTURE;
    }

    @Override
    public String providerIdentifier() {
        return "local-fixture";
    }

    private ProfessionalNarrative insufficientInformation(ProfessionalNarrativeContext context) {
        return new ProfessionalNarrative(
                "More detail is needed about your " + context.profession() + " work",
                "The saved assessment does not contain enough information for the four measures.",
                "A fuller description of the work is needed before a useful interpretation can be provided.",
                "The available description is not detailed enough to identify dependable exposure drivers.",
                "The available description is not detailed enough to identify dependable sources of human value.",
                "Complementarity cannot yet be explained without a complete assessment.",
                "Role adaptability cannot yet be explained without a complete assessment.",
                List.of(
                        "Describe the tasks that take most of your working time.",
                        "Add examples of decisions, relationships and accountability in the role.",
                        "Include the tools, systems and specialist skills used in day-to-day work."),
                "This development fixture does not infer missing facts or predict employment outcomes.");
    }

    private String band(ProfessionalNarrativeContext context, ProfessionalMeasure measure) {
        return context.measures().stream()
                .filter(item -> item.measure() == measure)
                .findFirst().orElseThrow().band().toLowerCase();
    }

    /**
     * Explains AI Exposure from Java's selected factors: the strongest factor raising it and the strongest lowering
     * it, so whichever of them is dominant for the result is always included. With no material factor on either
     * side, the result is attributed to no specific characteristic.
     */
    private String exposureExplanation(ProfessionalNarrativeContext context) {
        boolean drivers = !context.exposureDrivers().isEmpty();
        boolean protective = !context.protectiveFactors().isEmpty();
        if (!drivers && !protective) {
            return "No individual characteristic of the work was strong enough to highlight. The " + band(context,
                    ProfessionalMeasure.AI_EXPOSURE) + " AI Exposure result reflects the combined effect of several "
                    + "smaller characteristics of the work you described.";
        }
        StringBuilder explanation = new StringBuilder();
        if (drivers) {
            explanation.append("The assessment identifies ")
                    .append(describe(context.exposureDrivers().getFirst().direction()))
                    .append(" as an important reason AI may affect parts of the work.");
        }
        if (protective) {
            explanation.append(drivers ? " It also identifies " : "The assessment identifies ")
                    .append(describe(context.protectiveFactors().getFirst().direction()))
                    .append(" as an important reason the work has relatively little overlap with what AI can do "
                            + "through digital systems.");
        }
        return explanation.toString();
    }

    private String describe(ProfessionalFactorDirection direction) {
        return switch (direction) {
            case HIGH_TASK_REPEATABILITY -> "repeatable or standardised tasks";
            case LOW_TASK_REPEATABILITY -> "work that is less repetitive or standardised";
            case HIGH_DIGITAL_EXECUTABILITY -> "work that can be performed through digital systems";
            case LOW_DIGITAL_EXECUTABILITY -> "work that cannot be carried out entirely through digital systems";
            case HIGH_HUMAN_TRUST_DEPENDENCY -> "trust, persuasion and working relationships";
            case LOW_HUMAN_TRUST_DEPENDENCY -> "limited dependence on human trust or relationships";
            case HIGH_PHYSICAL_WORLD_DEPENDENCY -> "skilled presence in the physical world";
            case LOW_PHYSICAL_WORLD_DEPENDENCY -> "limited need for physical-world presence";
            case HIGH_CONTEXTUAL_COMPLEXITY -> "understanding complex and changing situations";
            case LOW_CONTEXTUAL_COMPLEXITY -> "clear and predictable working patterns";
            case HIGH_ORIGINAL_JUDGMENT -> "original judgement and synthesis";
            case LOW_ORIGINAL_JUDGMENT -> "established methods rather than original judgement";
            case HIGH_ACCOUNTABILITY_RESPONSIBILITY -> "responsibility for consequential decisions";
            case LOW_ACCOUNTABILITY_RESPONSIBILITY -> "limited responsibility for consequential decisions";
        };
    }
}
