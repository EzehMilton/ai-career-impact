package com.chikere.futureai.narrative.university.paid;

import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ModuleLearningInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext.Action;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext.GuidanceModule;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext.Measure;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext.Module;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext.Phase;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext.Safeguard;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext.Skill;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The id structure of the live PPE report whose narrative failed IDS_MATCH_DETERMINISTIC_ORDER twice: AI-learning
 * modules module-7, module-3, module-1, module-5 and own-capability modules module-2, module-3, module-1, module-5. The
 * ids are not sequential within a section and modules 1, 3 and 5 appear in both. Test data only: titles not in the
 * live trace, skills and actions are illustrative.
 */
public final class PpeNarrativeContext {

    public static final List<String> AI_LEARNING_IDS = List.of("module-7", "module-3", "module-1", "module-5");
    public static final List<String> OWN_CAPABILITY_IDS = List.of("module-2", "module-3", "module-1", "module-5");

    private PpeNarrativeContext() {
    }

    public static UniversityPaidNarrativeContext context() {
        return context(List.of(learning("module-7", "Quantitative Methods for Economics", TECHNICAL),
                learning("module-3", "Political Theory", JUDGEMENT),
                learning("module-1", "Introduction to Philosophy", JUDGEMENT),
                learning("module-5", "Microeconomics", TECHNICAL)));
    }

    /**
     * The same course with both economics modules among the AI-learning modules, to show two modules of one family
     * must differ: module-5 Microeconomics, module-4 Macroeconomics, module-1 Introduction to Philosophy and module-3
     * Political Theory.
     */
    public static UniversityPaidNarrativeContext withMacroeconomics() {
        return context(List.of(learning("module-5", "Microeconomics", TECHNICAL),
                learning("module-4", "Macroeconomics", TECHNICAL),
                learning("module-1", "Introduction to Philosophy", JUDGEMENT),
                learning("module-3", "Political Theory", JUDGEMENT)));
    }

    private static UniversityPaidNarrativeContext context(List<GuidanceModule> aiLearning) {
        List<String> signals = List.of("human judgement");
        return new UniversityPaidNarrativeContext(UUID.nameUUIDFromBytes("ppe".getBytes()),
                UUID.nameUUIDFromBytes("ppe-modules".getBytes()), "Philosophy, Politics and Economics",
                "An interdisciplinary degree combining philosophy, political theory and economics, with quantitative "
                        + "methods, political institutions and public policy.",
                List.of("Argument and analysis", "Economic reasoning"),
                List.of(new Measure("Working Alongside AI", "HIGH", List.of(), List.of()),
                        new Measure("Human and Practical Capability", "MODERATE", List.of(), List.of()),
                        new Measure("Transferable Capability", "HIGH", List.of(), List.of())),
                List.of(new Module("Introduction to Philosophy", "Human judgement remains central", signals),
                        new Module("Ethics and Public Policy", "Human judgement remains central", signals),
                        new Module("Political Theory", "Human judgement remains central", signals),
                        new Module("Macroeconomics", "Strong technical or quantitative core", List.of("technical depth")),
                        new Module("Microeconomics", "Strong technical or quantitative core", List.of("technical depth")),
                        new Module("Comparative Politics", "Human judgement remains central", signals),
                        new Module("Quantitative Methods for Economics", "Strong technical or quantitative core",
                                List.of("technical depth"))),
                aiLearning,
                List.of(own("module-2", "Ethics and Public Policy", JUDGEMENT),
                        own("module-3", "Political Theory", JUDGEMENT),
                        own("module-1", "Introduction to Philosophy", JUDGEMENT),
                        own("module-5", "Microeconomics", TECHNICAL)),
                List.of("Quantitative Methods for Economics", "Microeconomics"),
                List.of(new Skill("skill-critical-evaluation", "Critical evaluation",
                                "Several modules centre on interpretation and judgement.",
                                List.of("Political Theory", "Introduction to Philosophy")),
                        new Skill("skill-technical-foundations", "Technical foundations",
                                "Several modules have a technical or quantitative core.",
                                List.of("Quantitative Methods for Economics", "Microeconomics")),
                        new Skill("skill-ai-literacy", "AI literacy", "AI tools could support learning in most modules.",
                                List.of("Microeconomics"))),
                List.of(new Safeguard("Checking AI-generated claims and sources",
                        "Check AI summaries of your reading against the original sources.")),
                List.of(new Phase("Next 30 days", List.of(
                                new Action("next_30_days-d3s", "Check one AI-generated summary against the source.",
                                        List.of("Political Theory")),
                                new Action("next_30_days-d4", "Work on the hardest topic without AI first.",
                                        List.of("Quantitative Methods for Economics")))),
                        new Phase("This term", List.of(
                                new Action("this_term-t3", "Form your own interpretation before consulting AI.",
                                        List.of("Introduction to Philosophy")),
                                new Action("this_term-t4", "Solve problems yourself, then check your method with AI.",
                                        List.of("Microeconomics")))),
                        new Phase("Next 6 to 12 months", List.of(
                                new Action("next_6_to_12_months-m4", "Collect examples of your own judgement.",
                                        List.of("Political Theory")),
                                new Action("next_6_to_12_months-m3", "Apply your foundations in a project.",
                                        List.of("Quantitative Methods for Economics"))))),
                false, false);
    }

    private static final String TECHNICAL = "Strong technical or quantitative core";
    private static final String JUDGEMENT = "Human judgement remains central";

    /** With the real guidance-policy fallback sentence for the module's kind, as the live prompt carried it. */
    private static GuidanceModule learning(String id, String title, String classification) {
        return new GuidanceModule(id, title, classification, List.of(), classification.equals(TECHNICAL)
                ? "AI tools could support your learning in " + title + " by explaining concepts, generating practice "
                        + "problems and helping you check your own working."
                : "AI tools could help you prepare for " + title + " by summarising reading, exploring different "
                        + "interpretations and testing your arguments, while the judgement stays yours.");
    }

    private static GuidanceModule own(String id, String title, String classification) {
        return new GuidanceModule(id, title, classification, List.of(), classification.equals(TECHNICAL)
                ? title + " strongly depends on you demonstrating your own capability. Treat it as a place to build, "
                        + "and show, your own work."
                : "Your own judgement is especially important in " + title + ": interpreting, framing problems and "
                        + "reaching conclusions you can explain and defend.");
    }

    /** The same draft with its module-learning entries relabelled, text unchanged. */
    public static UniversityPaidNarrative withLearningIds(UniversityPaidNarrative draft, String... ids) {
        List<ModuleLearningInsight> learning = new ArrayList<>();
        for (int index = 0; index < ids.length; index++) {
            var insight = draft.moduleLearningInsights().get(index);
            learning.add(new ModuleLearningInsight(ids[index], insight.explanation(), insight.aiLearningUses(),
                    insight.independentCapability(), insight.studyExperiment()));
        }
        return new UniversityPaidNarrative(draft.executiveSummary(), draft.measuresTogether(), draft.coursePattern(),
                learning, draft.humanCapabilityInsights(), draft.skillInsights(), draft.actionPlanNotes(),
                draft.aiStudyStrategy(), draft.closingPerspective());
    }
}
