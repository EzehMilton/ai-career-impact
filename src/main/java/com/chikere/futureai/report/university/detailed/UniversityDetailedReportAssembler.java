package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisItem;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidance;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidanceAction;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidanceItem;
import com.chikere.futureai.coursemodule.university.guidance.UniversitySkillPriority;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleFlag;
import com.chikere.futureai.coursemodule.university.policy.UniversityModulePolicyV1;
import com.chikere.futureai.decision.university.UniversityProfileMeasure;
import com.chikere.futureai.narrative.professional.paid.PlanNoteRestatement;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeOutcome;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeStatus;
import com.chikere.futureai.report.university.application.UniversityCourseProfile;
import com.chikere.futureai.report.university.application.UniversityCourseProfileComposer;
import com.chikere.futureai.report.university.application.UniversityReportVersions;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Pure composition of {@code university-report-v3} from frozen artefacts. It calls no provider and recalculates
 * nothing: the course profile comes from the stored v2 result, module classifications and order from the module
 * snapshot, and every recommendation, safeguard and action from the deterministic guidance. The paid narrative adds a
 * module-by-module practical layer beside that copy, matched by exact id, never instead of it; without a narrative,
 * deterministic copy fills every section, including the AI-study strategy.
 *
 * <p>Stored v2 reports are never assembled again: they are read back and rendered exactly as stored.</p>
 */
public final class UniversityDetailedReportAssembler {

    public static final String AI_LEARNING_CAUTION = "These are areas where AI may help with learning, preparation, "
            + "exploration or checking. This does not mean AI use is permitted in assessed work.";

    static final List<String> LIMITATIONS = List.of(
            "This report assesses the structure of the course and the module information supplied, not your own "
                    + "ability.",
            "It is not a prediction of employment, job prospects or salary.",
            "AI capabilities and university policies change, so some findings may date.",
            "Always check your institution's and each module's assessment rules before using AI in assessed work.",
            "External labour-market evidence is not part of this course profile or its measures.");

    private static final Map<String, String> MODULE_EXPLANATIONS = Map.of(
            "AI_ASSISTED_LEARNING", "AI tools could support much of the learning and preparation in this module.",
            "AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION", "AI may help with learning and preparation here, but "
                    + "the module depends on demonstrating your own capability.",
            "HUMAN_JUDGEMENT_LED", "Interpretation, judgement and work with people lead this module.",
            "PRACTICAL_OR_EXPERIENTIAL", "Practical, hands-on or experiential work leads this module.",
            "TECHNICAL_CORE", "Technical or quantitative depth is at the core of this module.",
            "MIXED", "No single kind of work dominates this module.");

    /**
     * The three fixed guidance phrases that name code for every technical module, and their wording for a course that
     * involves no programming. Presentation only: the stored guidance is unchanged.
     */
    private static final Map<String, String> WITHOUT_PROGRAMMING = Map.of(
            "calculations, code or explanations", "calculations or explanations",
            "calculations, code or technical reasoning", "calculations or technical reasoning",
            "calculation, piece of code or technical explanation", "calculation or technical explanation");

    private static final double HIGH = UniversityModulePolicyV1.HIGH_FROM;

    private final UniversityCourseProfileComposer profiles = new UniversityCourseProfileComposer();

    /** The paid-narrative input: decided facts only, no probabilities, rule ids or diagnostics. */
    public UniversityPaidNarrativeContext narrativeContext(StoredUniversityAssessment assessment,
            UniversityModuleAnalysisSnapshot modules, UniversityGuidance guidance) {
        UniversityCourseProfile profile = profiles.compose(assessment.result().profile());
        var state = assessment.state();
        boolean programming = programming(assessment, modules);
        Map<Integer, UniversityModuleAnalysisItem> items = items(modules);
        return new UniversityPaidNarrativeContext(assessment.id(), modules.snapshotId(), state.courseTitle(),
                state.courseDescription(), state.skillsDeveloped(),
                profile.measures().stream().map(measure -> new UniversityPaidNarrativeContext.Measure(measure.name(),
                        measure.band(), measure.strengthenedBy(), measure.heldBackBy())).toList(),
                modules.items().stream().map(item -> new UniversityPaidNarrativeContext.Module(
                        item.judgments().moduleTitle(), item.displayLabel(), signals(item))).toList(),
                guidanceModules(guidance.aiLearningOpportunities(), items),
                guidanceModules(guidance.ownCapabilityAreas(), items),
                guidance.technicalFoundations().stream().map(UniversityGuidanceItem::title).toList(),
                guidance.skillPriorities().stream().map(skill -> new UniversityPaidNarrativeContext.Skill(skillId(skill),
                        skill.title(), skill.explanation(), titles(modules, skill.moduleIds()))).toList(),
                guidance.safeguards().stream().map(item -> new UniversityPaidNarrativeContext.Safeguard(item.title(),
                        fit(item.text(), programming))).toList(),
                List.of(phaseContext("Next 30 days", guidance.next30Days(), modules, programming),
                        phaseContext("This term", guidance.thisTerm(), modules, programming),
                        phaseContext("Next 6 to 12 months", guidance.next6To12Months(), modules, programming)),
                modules.items().stream().anyMatch(item -> item.classification()
                        .hasFlag(UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT)),
                programming);
    }

    public UniversityDetailedReport assemble(StoredUniversityAssessment assessment,
            UniversityModuleAnalysisSnapshot modules, UniversityGuidance guidance,
            UniversityPaidNarrativeOutcome narrative) {
        Objects.requireNonNull(assessment);
        Objects.requireNonNull(modules);
        Objects.requireNonNull(guidance);
        Objects.requireNonNull(narrative);
        if (!guidance.moduleAnalysisSnapshotId().equals(modules.snapshotId())) {
            throw new IllegalArgumentException("the guidance was not produced from this module analysis");
        }
        UniversityCourseProfile profile = profiles.compose(assessment.result().profile());
        var state = assessment.state();
        boolean programming = programming(assessment, modules);
        Optional<UniversityPaidNarrative> written = narrative.status() == UniversityPaidNarrativeStatus.AVAILABLE
                ? narrative.narrative() : Optional.empty();
        Map<String, UniversityPaidNarrative.SkillInsight> skillInsights = byId(written.map(
                UniversityPaidNarrative::skillInsights), UniversityPaidNarrative.SkillInsight::skillId);
        Map<String, String> notes = new LinkedHashMap<>();
        written.map(UniversityPaidNarrative::actionPlanNotes).orElse(List.of())
                .forEach(note -> notes.put(note.actionId().strip(), note.explanation().strip()));
        return new UniversityDetailedReport(UniversityReportVersions.V3, state.courseTitle(),
                state.institution().orElse(""), profile, perspectives(profile, assessment, written),
                modules(modules, state.keyModules().size()), AI_LEARNING_CAUTION,
                entries(guidance.aiLearningOpportunities(), programming),
                entries(guidance.ownCapabilityAreas(), programming),
                entries(guidance.technicalFoundations(), programming),
                guidance.skillPriorities().stream().map(skill -> new UniversityDetailedReport.Skill(skill.title(),
                        skill.explanation(), titles(modules, skill.moduleIds()),
                        Optional.ofNullable(skillInsights.get(skillId(skill)))
                                .map(UniversityPaidNarrative.SkillInsight::inThisCourseThisMeans).orElse(List.of())))
                        .toList(),
                entries(guidance.safeguards(), programming),
                List.of(phase("Next 30 days", guidance.next30Days(), notes, programming),
                        phase("This term", guidance.thisTerm(), notes, programming),
                        phase("Next 6 to 12 months", guidance.next6To12Months(), notes, programming)),
                LIMITATIONS,
                new UniversityDetailedReport.Provenance(assessment.id(), modules.snapshotId(),
                        assessment.result().decisionPolicyVersion(), modules.schemaVersion(), modules.policyVersion(),
                        guidance.policyVersion(), narrative.narrativeVersion(), narrative.status().name(),
                        UniversityReportVersions.PDF_V3),
                practical(modules, guidance, written, programming));
    }

    private static UniversityDetailedReport.Perspectives perspectives(UniversityCourseProfile profile,
            StoredUniversityAssessment assessment, Optional<UniversityPaidNarrative> written) {
        String aiLearning = "The modules below are where AI may help most with learning, preparation, exploration or "
                + "checking, and what you still need to be able to do yourself in each.";
        String humanPractical = "These modules place particular weight on your own judgement, practical skill or work "
                + "with people.";
        String plan = "The plan below turns these findings into steps for the next 30 days, this term and the next 6 "
                + "to 12 months.";
        if (written.isPresent()) {
            UniversityPaidNarrative narrative = written.get();
            return new UniversityDetailedReport.Perspectives(true, narrative.executiveSummary().strip(), aiLearning,
                    humanPractical, narrative.measuresTogether().strip(), plan, narrative.coursePattern().strip(),
                    narrative.closingPerspective().strip());
        }
        String transfer = switch (assessment.result().profile().measure(UniversityProfileMeasure.TRANSFERABLE_CAPABILITY)
                .orElseThrow().band()) {
            case HIGH -> "The capabilities this course develops appear to transfer widely across different roles and "
                    + "settings.";
            case MODERATE -> "The capabilities this course develops appear to transfer to a moderate range of roles "
                    + "and settings.";
            case LOW -> "The capabilities this course develops appear more closely tied to particular roles and "
                    + "settings, so building breadth alongside it is worth considering.";
        };
        return new UniversityDetailedReport.Perspectives(false, profile.summary(), aiLearning, humanPractical, transfer,
                plan, null, null);
    }

    private static UniversityDetailedReport.Modules modules(UniversityModuleAnalysisSnapshot snapshot, int courseModules) {
        int analysed = snapshot.items().size();
        String note = analysed < courseModules
                ? "This analysis reflects the " + analysed + " modules you selected from the " + courseModules
                        + " listed for the course."
                : "All " + analysed + " modules listed for the course are included.";
        List<UniversityDetailedReport.ModuleCard> cards = snapshot.items().stream().map(item -> {
            List<String> notes = new ArrayList<>();
            if (item.classification().hasFlag(UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE)) {
                notes.add("Demonstrating your own capability is especially important here.");
            }
            if (item.classification().hasFlag(UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT)) {
                notes.add("Professional, clinical or client-facing standards appear relevant.");
            }
            return new UniversityDetailedReport.ModuleCard(item.moduleId(), item.judgments().moduleTitle(),
                    item.displayLabel(), MODULE_EXPLANATIONS.get(item.classification().category().name()), notes);
        }).toList();
        return new UniversityDetailedReport.Modules(note, cards);
    }

    /** The practical layer: the narrative's interpretation beside each fixed guidance item, matched by exact id. */
    private static UniversityDetailedReport.Practical practical(UniversityModuleAnalysisSnapshot modules,
            UniversityGuidance guidance, Optional<UniversityPaidNarrative> written, boolean programming) {
        Map<Integer, UniversityModuleAnalysisItem> items = items(modules);
        Map<String, UniversityPaidNarrative.ModuleLearningInsight> learning = byId(written.map(
                UniversityPaidNarrative::moduleLearningInsights), UniversityPaidNarrative.ModuleLearningInsight::moduleId);
        Map<String, UniversityPaidNarrative.HumanCapabilityInsight> capability = byId(written.map(
                UniversityPaidNarrative::humanCapabilityInsights), UniversityPaidNarrative.HumanCapabilityInsight::moduleId);
        List<UniversityDetailedReport.ModuleLearning> moduleLearning = guidance.aiLearningOpportunities().stream()
                .map(item -> {
                    var insight = Optional.ofNullable(learning.get(moduleId(item)));
                    return new UniversityDetailedReport.ModuleLearning(item.title(), label(item, items),
                            fit(item.text(), programming),
                            insight.map(UniversityPaidNarrative.ModuleLearningInsight::explanation).map(String::strip)
                                    .orElse(null),
                            insight.map(UniversityPaidNarrative.ModuleLearningInsight::aiLearningUses).orElse(List.of()),
                            insight.map(UniversityPaidNarrative.ModuleLearningInsight::independentCapability)
                                    .orElse(List.of()),
                            insight.map(UniversityPaidNarrative.ModuleLearningInsight::studyExperiment)
                                    .map(String::strip).orElse(null));
                }).toList();
        List<UniversityDetailedReport.CapabilityFocus> focus = guidance.ownCapabilityAreas().stream().map(item -> {
            var insight = Optional.ofNullable(capability.get(moduleId(item)));
            return new UniversityDetailedReport.CapabilityFocus(item.title(), fit(item.text(), programming),
                    insight.map(UniversityPaidNarrative.HumanCapabilityInsight::explanation).map(String::strip)
                            .orElse(null),
                    insight.map(UniversityPaidNarrative.HumanCapabilityInsight::independentPractice).map(String::strip)
                            .orElse(null));
        }).toList();
        String technical = guidance.technicalFoundations().isEmpty() ? null
                : "Strengthening your foundations in " + names(guidance.technicalFoundations().stream()
                        .map(UniversityGuidanceItem::title).toList()) + " makes you more effective with AI tools: you "
                        + "are better placed to judge whether AI-generated " + (programming
                        ? "calculations, code or explanations" : "calculations or explanations") + " are correct.";
        var strategy = written.map(UniversityPaidNarrative::aiStudyStrategy)
                .map(s -> new UniversityDetailedReport.StudyStrategy(true, s.useAi(), s.learnWithoutAi(), s.verify(),
                        s.demonstrateOwnCapability(), s.buildEvidence()))
                .orElseGet(() -> deterministicStrategy(modules, guidance, items));
        List<UniversityDetailedReport.Entry> legend = modules.items().stream().map(item ->
                new UniversityDetailedReport.Entry(item.displayLabel(),
                        MODULE_EXPLANATIONS.get(item.classification().category().name()))).distinct().toList();
        return new UniversityDetailedReport.Practical(legend, moduleLearning, focus, technical, strategy);
    }

    /**
     * The AI-study strategy without a narrative: from the stored guidance only, so a fallback report still has the
     * section, in plainer words.
     */
    private static UniversityDetailedReport.StudyStrategy deterministicStrategy(UniversityModuleAnalysisSnapshot modules,
            UniversityGuidance guidance, Map<Integer, UniversityModuleAnalysisItem> items) {
        List<String> useAi = guidance.aiLearningOpportunities().stream().limit(2).map(item -> switch (category(item,
                items)) {
            case HUMAN_JUDGEMENT_LED -> "Use AI to summarise reading and test your arguments in " + item.title()
                    + ", while the judgement stays yours.";
            case AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION -> "Use AI to help you plan and structure your "
                    + "preparation for " + item.title() + ", keeping the content your own.";
            default -> "Use AI to explain difficult topics in " + item.title() + " another way and to quiz you on them.";
        }).toList();
        if (useAi.isEmpty()) {
            useAi = List.of("Use AI to explain difficult topics another way and to quiz you on them.");
        }
        List<String> core = guidance.technicalFoundations().isEmpty()
                ? guidance.ownCapabilityAreas().stream().map(UniversityGuidanceItem::title).toList()
                : guidance.technicalFoundations().stream().map(UniversityGuidanceItem::title).toList();
        List<String> learnWithoutAi = core.isEmpty()
                ? List.of("Work through core problems and readings yourself before asking AI about them.")
                : List.of("Work through the core of " + names(core) + " yourself before asking AI about it.");
        List<String> verify = new ArrayList<>();
        for (UniversityGuidanceItem safeguard : guidance.safeguards()) {
            switch (safeguard.ruleId()) {
                case "SG3_VERIFY_TECHNICAL_OUTPUT" -> verify.add("Check AI-generated technical working step by step "
                        + "against your own.");
                case "SG4_VERIFY_SOURCES" -> verify.add("Check AI summaries, quotations and references against the "
                        + "original sources.");
                case "SG2_PROFESSIONAL_STANDARDS" -> verify.add("Check AI output against approved course material and "
                        + "the professional guidance your course uses.");
                default -> {
                }
            }
        }
        if (verify.isEmpty()) {
            verify.add("Check AI explanations against your lecture notes and course material.");
        }
        List<String> integrity = modules.items().stream().filter(item -> item.classification()
                .hasFlag(UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE))
                .map(item -> item.judgments().moduleTitle()).toList();
        List<String> demonstrate = integrity.isEmpty()
                ? List.of("Keep notes and drafts that show how your own work developed.")
                : List.of("Keep notes, drafts or records that show how your own work in " + names(integrity)
                        + " developed.");
        List<String> evidence = new ArrayList<>(List.of("Collect examples of problems you solved, interpretations "
                + "you reached and feedback you acted on."));
        if (modules.items().stream().anyMatch(item -> item.classification().category()
                == UniversityModuleCategory.PRACTICAL_OR_EXPERIENTIAL)) {
            evidence.add("Keep a record of the practical skills you practised and the feedback you received.");
        }
        return new UniversityDetailedReport.StudyStrategy(false, useAi, learnWithoutAi, List.copyOf(verify),
                demonstrate, evidence);
    }

    private static List<UniversityDetailedReport.Entry> entries(List<UniversityGuidanceItem> items, boolean programming) {
        return items.stream().map(item -> new UniversityDetailedReport.Entry(item.title(), fit(item.text(), programming)))
                .toList();
    }

    /** Each action with its note, unless the note only restates the action shown above it. */
    private static UniversityDetailedReport.Phase phase(String title, List<UniversityGuidanceAction> actions,
            Map<String, String> notes, boolean programming) {
        List<String> texts = actions.stream().map(action -> fit(action.text(), programming)).toList();
        List<UniversityDetailedReport.Step> steps = new ArrayList<>();
        for (int index = 0; index < actions.size(); index++) {
            String note = notes.get(actions.get(index).actionId());
            steps.add(new UniversityDetailedReport.Step(texts.get(index),
                    note == null || PlanNoteRestatement.restates(note, texts.get(index)) ? null : note));
        }
        return new UniversityDetailedReport.Phase(title, texts, steps);
    }

    private static UniversityPaidNarrativeContext.Phase phaseContext(String title, List<UniversityGuidanceAction> actions,
            UniversityModuleAnalysisSnapshot modules, boolean programming) {
        return new UniversityPaidNarrativeContext.Phase(title, actions.stream().map(action ->
                new UniversityPaidNarrativeContext.Action(action.actionId(), fit(action.text(), programming),
                        titles(modules, action.moduleIds()))).toList());
    }

    private static List<UniversityPaidNarrativeContext.GuidanceModule> guidanceModules(List<UniversityGuidanceItem> guidance,
            Map<Integer, UniversityModuleAnalysisItem> items) {
        return guidance.stream().map(item -> new UniversityPaidNarrativeContext.GuidanceModule(moduleId(item),
                item.title(), label(item, items), signals(items.get(item.moduleIds().getFirst())), item.text())).toList();
    }

    /** The plain-language signals of one module: its HIGH characteristics and flags, never a number. */
    private static List<String> signals(UniversityModuleAnalysisItem item) {
        var judgments = item.judgments();
        List<String> signals = new ArrayList<>();
        if (judgments.aiLearningAssistance().value() >= HIGH) signals.add("AI could support much of the learning");
        if (judgments.technicalQuantitative().value() >= HIGH) signals.add("technical or quantitative depth");
        if (judgments.humanJudgement().value() >= HIGH) signals.add("interpretation and judgement");
        if (judgments.interpersonalWork().value() >= HIGH) signals.add("substantial work with other people");
        if (judgments.practicalApplication().value() >= HIGH) signals.add("practical or hands-on work");
        if (judgments.independentDemonstration().value() >= HIGH) signals.add("demonstrating your own capability");
        if (item.classification().hasFlag(UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE)) {
            signals.add("assessed work depends strongly on showing your own capability");
        }
        if (item.classification().hasFlag(UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT)) {
            signals.add("professional, clinical or client-facing standards appear relevant");
        }
        return signals;
    }

    /** Whether the student's own course text involves programming; decides only wording, never content. */
    private static boolean programming(StoredUniversityAssessment assessment, UniversityModuleAnalysisSnapshot modules) {
        var state = assessment.state();
        return UniversityPaidNarrativeContext.programmingVocabularySupported(state.courseTitle(),
                state.courseDescription(), state.skillsDeveloped(),
                modules.items().stream().map(item -> item.judgments().moduleTitle()).toList());
    }

    /** The fixed guidance wording, without "code" for a course that involves no programming. */
    static String fit(String text, boolean programming) {
        if (programming) {
            return text;
        }
        String fitted = text;
        for (var replacement : WITHOUT_PROGRAMMING.entrySet()) {
            fitted = fitted.replace(replacement.getKey(), replacement.getValue());
        }
        return fitted;
    }

    static String moduleId(UniversityGuidanceItem item) {
        return "module-" + item.moduleIds().getFirst();
    }

    static String skillId(UniversitySkillPriority skill) {
        return "skill-" + skill.theme().name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private static Map<Integer, UniversityModuleAnalysisItem> items(UniversityModuleAnalysisSnapshot modules) {
        Map<Integer, UniversityModuleAnalysisItem> byId = new LinkedHashMap<>();
        modules.items().forEach(item -> byId.put(item.moduleId(), item));
        return byId;
    }

    private static String label(UniversityGuidanceItem item, Map<Integer, UniversityModuleAnalysisItem> items) {
        return items.get(item.moduleIds().getFirst()).displayLabel();
    }

    private static UniversityModuleCategory category(UniversityGuidanceItem item,
            Map<Integer, UniversityModuleAnalysisItem> items) {
        return items.get(item.moduleIds().getFirst()).classification().category();
    }

    /** Exact-id lookup; the validator has already held the ids to the deterministic order. */
    private static <T> Map<String, T> byId(Optional<List<T>> values, Function<T, String> id) {
        Map<String, T> byId = new LinkedHashMap<>();
        values.orElse(List.of()).forEach(value -> byId.put(id.apply(value).strip(), value));
        return byId;
    }

    private static List<String> titles(UniversityModuleAnalysisSnapshot snapshot, List<Integer> moduleIds) {
        return snapshot.items().stream().filter(item -> moduleIds.contains(item.moduleId()))
                .map(item -> item.judgments().moduleTitle()).limit(3).toList();
    }

    private static String names(List<String> titles) {
        if (titles.size() == 1) {
            return titles.getFirst();
        }
        return String.join(", ", titles.subList(0, titles.size() - 1)) + " and " + titles.getLast();
    }
}
