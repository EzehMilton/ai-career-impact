package com.chikere.futureai.narrative.university.paid;

import java.util.List;

/**
 * The {@value UniversityPaidNarrativeVersions#V2} structured narrative. It decides nothing: the report's scores, bands,
 * module classifications and order, skills, safeguards and actions come from frozen Java policy and appear unchanged
 * whether or not this narrative is available. It adds bounded, module-specific interpretation, each item keyed to a
 * deterministic id. Fields may be null when a provider returns malformed output; the validator rejects that.
 */
public record UniversityPaidNarrative(
        String executiveSummary,
        String measuresTogether,
        String coursePattern,
        List<ModuleLearningInsight> moduleLearningInsights,
        List<HumanCapabilityInsight> humanCapabilityInsights,
        List<SkillInsight> skillInsights,
        List<ActionPlanNote> actionPlanNotes,
        AiStudyStrategy aiStudyStrategy,
        String closingPerspective) {

    /**
     * One module selected for AI-learning guidance: why AI may help here, how, what the student must still be able to
     * do alone, and one small study experiment.
     */
    public record ModuleLearningInsight(String moduleId, String explanation, List<String> aiLearningUses,
            List<String> independentCapability, String studyExperiment) {
    }

    /** One module where the student's own capability matters most: why, and what to practise without AI. */
    public record HumanCapabilityInsight(String moduleId, String explanation, String independentPractice) {
    }

    /** One deterministic skill priority, unchanged, with how it shows up in this course. */
    public record SkillInsight(String skillId, String explanation, List<String> inThisCourseThisMeans) {
    }

    /** A practical note on one deterministic action; it must add to the action, never restate it. */
    public record ActionPlanNote(String actionId, String explanation) {
    }

    /** How to study in an AI-shaped university: one to three short items under each heading. */
    public record AiStudyStrategy(List<String> useAi, List<String> learnWithoutAi, List<String> verify,
            List<String> demonstrateOwnCapability, List<String> buildEvidence) {
    }
}
