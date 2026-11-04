package com.chikere.futureai.narrative.university.paid;

import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ActionPlanNote;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.AiStudyStrategy;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.HumanCapabilityInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ModuleLearningInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.SkillInsight;

import java.util.ArrayList;
import java.util.List;

/**
 * A university-paid-narrative-v2 draft that passes every rule for any context, one entry per id in order. Hand-written,
 * deliberately subject-neutral test copy, not model output: each module's uses are phrased differently so no two
 * modules share a template. The calibrated, course-specific examples are in the detailed-report calibration fixtures.
 */
public final class UniversityNarratives {

    private static final String[] ORDINALS = {"first", "second", "third", "fourth", "fifth", "sixth", "seventh",
            "eighth", "ninth", "tenth", "eleventh", "twelfth"};

    /** Four differently worded pairs of uses, so modules never share a template; module titles fill them in. */
    private static final String[][] USES = {
            {"Asking for a second explanation of the hardest idea in %s, then restating it in your own words",
                    "Turning your lecture notes on %s into recall questions you answer before checking"},
            {"Requesting a worked contrast between two approaches covered in %s",
                    "Getting critique on a short plan you wrote for a %s task"},
            {"Generating unfamiliar scenarios that apply %s, which you work through alone",
                    "Comparing your own summary of a %s reading with an AI summary to spot gaps"},
            {"Building a glossary for %s that you then test yourself on",
                    "Listing common misconceptions about %s and checking which ones you hold"}};

    private UniversityNarratives() {
    }

    public static UniversityPaidNarrative valid(UniversityPaidNarrativeContext context) {
        return valid(context, "");
    }

    /** The same shape with different wording: a later provider answer that a stored report must ignore. */
    public static UniversityPaidNarrative different(UniversityPaidNarrativeContext context) {
        return valid(context, "Looking again, ");
    }

    private static UniversityPaidNarrative valid(UniversityPaidNarrativeContext context, String lead) {
        List<ModuleLearningInsight> learning = new ArrayList<>();
        for (int index = 0; index < context.aiLearningModules().size(); index++) {
            var module = context.aiLearningModules().get(index);
            String m = module.module();
            String[] uses = USES[index % USES.length];
            learning.add(new ModuleLearningInsight(module.id(),
                    lead + "Much of " + m + " is material you can rehearse and question, so AI is most useful as a "
                            + "patient study partner whose answers you test.",
                    List.of(uses[0].formatted(m), uses[1].formatted(m)),
                    List.of("Explaining the central ideas of " + m + " without notes"),
                    "For one week, attempt a " + m + " exercise yourself first, then ask AI to question your approach "
                            + "and note what you would change."));
        }
        List<HumanCapabilityInsight> capability = context.ownCapabilityModules().stream().map(module ->
                new HumanCapabilityInsight(module.id(),
                        lead + "What " + module.module() + " asks of you grows only through your own attempts, "
                                + "feedback and reflection, so it is worth protecting from shortcuts.",
                        "Be able to talk someone through your reasoning in " + module.module()
                                + " without prompts.")).toList();
        List<SkillInsight> skills = context.skillPriorities().stream().map(skill -> new SkillInsight(skill.id(),
                lead + "This priority comes from the pattern across your modules and shapes how you study alongside AI.",
                List.of("Showing " + skill.skill().toLowerCase() + " in your own written work",
                        "Talking through " + skill.skill().toLowerCase() + " with classmates or tutors"))).toList();
        List<ActionPlanNote> notes = new ArrayList<>();
        List<String> actionIds = context.actionIds();
        for (int index = 0; index < actionIds.size(); index++) {
            notes.add(new ActionPlanNote(actionIds.get(index), "Keep this " + ORDINALS[index % ORDINALS.length]
                    + " step small and write down what you learned from it."));
        }
        return new UniversityPaidNarrative(
                lead + "Your course combines material that AI may help you rehearse with work that depends on your own "
                        + "understanding and practice.\n\nThe sections below set out where AI may support learning in "
                        + "each module and what you need to be able to do yourself.",
                "Read together, the three measures describe a course where AI support and your own capability both "
                        + "matter.",
                "Across your analysed modules, some lend themselves to AI-supported revision while others rely on "
                        + "your own practice and judgement.",
                learning, capability, skills, notes,
                new AiStudyStrategy(List.of("Rehearsing explanations and recall questions with AI after lectures."),
                        List.of("Working core problems and readings yourself before asking AI."),
                        List.of("Checking AI explanations against your lecture notes and course material."),
                        List.of("Keeping dated drafts and notes that show how your work developed."),
                        List.of("Collecting feedback you acted on and examples of reasoning you can explain.")),
                "Studying well alongside AI means using it to question and rehearse while keeping the core of the "
                        + "learning your own.");
    }
}
