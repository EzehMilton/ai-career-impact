package com.chikere.futureai.narrative.university.fixture;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.university.UniversityFactorDirection;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.narrative.university.application.NarrativeSource;
import com.chikere.futureai.narrative.university.application.UniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeContext;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;

import java.util.List;
import java.util.Locale;

/** Deterministic presentation templates for explicit offline UI development. */
public final class LocalFixtureUniversityNarrativeGenerator implements UniversityNarrativeGenerator {

    @Override
    public UniversityNarrative generate(UniversityNarrativeContext context) {
        if (context.assessmentStatus() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return insufficientInformation(context);
        }
        String readiness = band(context, UniversityMeasure.FUTURE_READINESS);
        String complementarity = band(context, UniversityMeasure.AI_COMPLEMENTARITY);
        String transferability = band(context, UniversityMeasure.SKILL_TRANSFERABILITY);
        String adaptability = band(context, UniversityMeasure.CAREER_ADAPTABILITY);
        String strength = context.strengths().isEmpty()
                ? "the mix of capabilities described"
                : describe(context.strengths().getFirst().direction());
        String opportunity = context.developmentOpportunities().isEmpty()
                ? "areas the course description does not emphasise"
                : describe(context.developmentOpportunities().getFirst().direction());
        String careers = context.preferredCareerAreas().isEmpty()
                ? "The career areas you are considering were not supplied, so this explanation stays general."
                : "You mentioned " + String.join(", ", context.preferredCareerAreas())
                        + " as areas to explore. These remain relevant areas to look into, and the "
                        + "capabilities above may help you prepare for them.";
        return new UniversityNarrative(
                "A practical reading of " + context.courseTitle(),
                "This local fixture explanation reflects the saved assessment: Future Readiness is "
                        + readiness + " and scope for AI complementarity is " + complementarity + ".",
                "This assessment suggests how well the course appears to develop capabilities that stay "
                        + "useful as AI tools become more capable. It is not a prediction about any particular job.",
                "Future Readiness here reflects the balance of analysis, technical and digital work, "
                        + "independent judgment, scope for AI complementarity and breadth of capability that "
                        + "the course description suggests.",
                "This course appears to develop " + strength + ", which the assessment treats as a strength.",
                "One area you may want to strengthen alongside the course is " + opportunity + ".",
                "There appears to be scope to use AI tools productively alongside these capabilities while "
                        + "your own judgment, context and communication continue to matter.",
                "Skill transferability is " + transferability + ", which describes how widely the "
                        + "capabilities may apply beyond one specific field.",
                "Career adaptability is " + adaptability + ". This describes the breadth of pathways the "
                        + "course appears to open, not your own motivation or circumstances. " + careers,
                List.of(
                        "Look at how the modules you are most interested in are actually assessed.",
                        "Build practical, honest experience with AI tools relevant to this subject.",
                        "Look for project, placement or society work that supplements " + opportunity + ".",
                        "Talk to current students or recent graduates about what the course is like in practice."),
                "This is a development fixture explanation of a saved assessment. It is not official guidance, "
                        + "not a statement about any real institution, and not a guarantee about employment.");
    }

    @Override
    public NarrativeSource source() {
        return NarrativeSource.LOCAL_FIXTURE;
    }

    @Override
    public String providerIdentifier() {
        return "local-fixture";
    }

    private UniversityNarrative insufficientInformation(UniversityNarrativeContext context) {
        return new UniversityNarrative(
                "More detail is needed about " + context.courseTitle(),
                "The saved assessment does not contain enough information about the course for the five measures.",
                "A fuller description of what the course covers is needed before a useful reading can be given.",
                "Future Readiness cannot yet be explained without a complete assessment.",
                "The available description is not detailed enough to identify dependable strengths.",
                "The available description is not detailed enough to identify dependable development areas.",
                "AI complementarity cannot yet be explained without a complete assessment.",
                "Skill transferability cannot yet be explained without a complete assessment.",
                "Career adaptability cannot yet be explained without a complete assessment.",
                List.of(
                        "Add the modules or topics that take up most of the course.",
                        "Describe how the course is studied: essays, exams, labs, projects or placements.",
                        "List the skills the course says you will develop."),
                "This development fixture does not infer missing facts or predict employment outcomes.");
    }

    private String band(UniversityNarrativeContext context, UniversityMeasure measure) {
        return context.measures().stream()
                .filter(item -> item.measure() == measure)
                .findFirst().orElseThrow().band().name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String describe(UniversityFactorDirection direction) {
        return switch (direction) {
            case HIGH_TECHNICAL_DIGITAL_DEPTH -> "substantial technical and digital capability";
            case LOW_TECHNICAL_DIGITAL_DEPTH -> "technical and digital capability";
            case HIGH_ANALYTICAL_REASONING -> "strong structured analysis and problem solving";
            case LOW_ANALYTICAL_REASONING -> "structured analysis and quantitative reasoning";
            case HIGH_ORIGINAL_JUDGMENT_CREATIVITY -> "independent judgment, synthesis and original work";
            case LOW_ORIGINAL_JUDGMENT_CREATIVITY -> "independent judgment and original work";
            case HIGH_HUMAN_RELATIONSHIP_DEPENDENCY -> "communication, collaboration and working with people";
            case LOW_HUMAN_RELATIONSHIP_DEPENDENCY -> "communication and working with people";
            case HIGH_CONTEXTUAL_COMPLEXITY -> "working with ambiguous and changing situations";
            case LOW_CONTEXTUAL_COMPLEXITY -> "working with ambiguity rather than clearly structured problems";
            case HIGH_PRACTICAL_REAL_WORLD_APPLICATION -> "applied, practical and project-based work";
            case LOW_PRACTICAL_REAL_WORLD_APPLICATION -> "applied and real-world practice";
            case HIGH_AI_COMPLEMENTARITY -> "work where AI tools could add a great deal alongside you";
            case LOW_AI_COMPLEMENTARITY -> "familiarity with where AI tools can and cannot help";
            case HIGH_SKILL_TRANSFERABILITY -> "capabilities that apply across many roles and sectors";
            case LOW_SKILL_TRANSFERABILITY -> "capabilities that apply beyond one specific field";
            case HIGH_CAREER_BREADTH -> "a broad range of plausible pathways";
            case LOW_CAREER_BREADTH -> "a wider range of plausible pathways";
        };
    }
}
