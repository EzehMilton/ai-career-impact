package com.chikere.futureai.narrative.alevel.fixture;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.alevel.ALevelFactorDirection;
import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrative;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeContext;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.application.NarrativeSource;

import java.util.List;
import java.util.Locale;

/** Deterministic presentation templates for explicit offline UI development. */
public final class LocalFixtureALevelNarrativeGenerator implements ALevelNarrativeGenerator {

    @Override
    public ALevelNarrative generate(ALevelNarrativeContext context) {
        if (context.assessmentStatus() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return insufficientInformation(context);
        }
        String subjects = String.join(", ", context.selectedSubjects());
        String optionality = band(context, ALevelMeasure.FUTURE_OPTIONALITY);
        String pathways = band(context, ALevelMeasure.DEGREE_PATHWAY_BREADTH);
        String analytical = band(context, ALevelMeasure.ANALYTICAL_FOUNDATION);
        String human = band(context, ALevelMeasure.HUMAN_AND_CREATIVE_STRENGTH);
        String transferability = band(context, ALevelMeasure.SKILL_TRANSFERABILITY);
        String adaptability = band(context, ALevelMeasure.AI_ERA_ADAPTABILITY);
        String strength = context.strengths().isEmpty()
                ? "the mix of capabilities these subjects build"
                : describe(context.strengths().getFirst().direction());
        String opportunity = context.developmentOpportunities().isEmpty()
                ? "areas your current subjects emphasise less"
                : describe(context.developmentOpportunities().getFirst().direction());
        String degrees = context.preferredDegreeAreas().isEmpty()
                ? "You have not listed any degree areas yet, which is completely normal at this stage."
                : "You mentioned " + String.join(", ", context.preferredDegreeAreas())
                        + " as areas you are considering. These are worth researching further, and it is "
                        + "always worth checking each specific course's published subject requirements.";

        return new ALevelNarrative(
                "A practical reading of " + subjects,
                "This local fixture explanation reflects the saved assessment: future optionality is "
                        + optionality + " and the breadth of degree pathways is " + pathways + ".",
                "This assessment suggests how wide a range of next steps these subjects currently "
                        + "preserve. It is not a prediction about any particular university place or job.",
                "Future optionality here reflects the balance of pathway breadth, transferable "
                        + "capability, analysis and variety that your combination appears to build.",
                "Your combination appears to give you " + strength + ".",
                "The breadth of degree pathways these subjects keep open is " + pathways
                        + ". If a particular course interests you, check its own published requirements.",
                "Your analytical foundation is " + analytical
                        + ", covering structured reasoning and quantitative confidence.",
                "Your human and creative strength is " + human
                        + ", covering communication, interpretation and understanding of context.",
                "Skill transferability is " + transferability
                        + ", describing how widely these capabilities apply beyond the subjects themselves.",
                "Adaptability for an AI-shaped economy is " + adaptability
                        + ". This describes the capabilities your subjects build, not your own potential.",
                "One area you may want to research further or supplement is " + opportunity + ". "
                        + degrees,
                List.of(
                        "Look at how each of your subjects is actually assessed over the two years.",
                        "Build some basic coding or data literacy alongside your subjects.",
                        "Research the published subject requirements of two or three courses that interest you.",
                        "Practise explaining a complex idea clearly in writing."),
                "This is a development fixture explanation of a saved assessment. It is not official "
                        + "guidance, it does not reflect any university's admissions policy, and it is not "
                        + "a statement about where you will end up.");
    }

    @Override
    public NarrativeSource source() {
        return NarrativeSource.LOCAL_FIXTURE;
    }

    @Override
    public String providerIdentifier() {
        return "local-fixture";
    }

    private ALevelNarrative insufficientInformation(ALevelNarrativeContext context) {
        return new ALevelNarrative(
                "More detail is needed about your subjects",
                "The saved assessment does not contain enough information for the six measures.",
                "A clearer description of the subjects and how you study them is needed first.",
                "Future optionality cannot yet be explained without a complete assessment.",
                "The available description is not detailed enough to identify dependable strengths.",
                "Degree pathway breadth cannot yet be explained without a complete assessment.",
                "Analytical foundation cannot yet be explained without a complete assessment.",
                "Human and creative strength cannot yet be explained without a complete assessment.",
                "Skill transferability cannot yet be explained without a complete assessment.",
                "Adaptability cannot yet be explained without a complete assessment.",
                "Add a little more detail and the assessment can say something useful.",
                List.of(
                        "List each subject you are taking or seriously considering.",
                        "Add what you most enjoy or feel strongest at.",
                        "Mention any degree areas you are curious about, even if you are undecided."),
                "This development fixture does not infer missing facts or predict any outcome.");
    }

    private String band(ALevelNarrativeContext context, ALevelMeasure measure) {
        return context.measures().stream()
                .filter(item -> item.measure() == measure)
                .findFirst().orElseThrow().band().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String describe(ALevelFactorDirection direction) {
        return switch (direction) {
            case HIGH_QUANTITATIVE_REASONING -> "a strong quantitative foundation";
            case LOW_QUANTITATIVE_REASONING -> "quantitative and mathematical confidence";
            case HIGH_ANALYTICAL_REASONING -> "strong structured analysis and problem solving";
            case LOW_ANALYTICAL_REASONING -> "structured analysis and problem solving";
            case HIGH_WRITTEN_VERBAL_COMMUNICATION -> "strong written argument and explanation";
            case LOW_WRITTEN_VERBAL_COMMUNICATION -> "extended writing and argument";
            case HIGH_ORIGINAL_JUDGMENT_CREATIVITY -> "strong interpretation, originality and design";
            case LOW_ORIGINAL_JUDGMENT_CREATIVITY -> "independent interpretation and original work";
            case HIGH_CONTEXTUAL_SOCIAL_UNDERSTANDING -> "a strong grasp of human and social context";
            case LOW_CONTEXTUAL_SOCIAL_UNDERSTANDING -> "understanding of human, social and economic context";
            case HIGH_TECHNICAL_DIGITAL_FOUNDATION -> "a strong technical and digital foundation";
            case LOW_TECHNICAL_DIGITAL_FOUNDATION -> "technical and digital depth";
            case HIGH_SUBJECT_SKILL_DIVERSITY -> "a genuinely varied mix of capability types";
            case LOW_SUBJECT_SKILL_DIVERSITY -> "variety across different kinds of capability";
            case HIGH_DEGREE_PATHWAY_BREADTH -> "a broad range of plausible degree routes";
            case LOW_DEGREE_PATHWAY_BREADTH -> "breadth across a wider range of degree routes";
            case HIGH_SKILL_TRANSFERABILITY -> "capabilities that transfer widely";
            case LOW_SKILL_TRANSFERABILITY -> "capabilities that transfer beyond one field";
            case HIGH_AI_ERA_COMPLEMENTARITY -> "a good foundation for working alongside AI tools";
            case LOW_AI_ERA_COMPLEMENTARITY -> "familiarity with where AI tools can and cannot help";
        };
    }
}
