package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidance;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidanceAction;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidanceItem;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleFlag;
import com.chikere.futureai.decision.university.UniversityProfileMeasure;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeOutcome;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeStatus;
import com.chikere.futureai.report.university.application.UniversityCourseProfile;
import com.chikere.futureai.report.university.application.UniversityCourseProfileComposer;
import com.chikere.futureai.report.university.application.UniversityReportVersions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Pure composition of {@code university-report-v2} from frozen artefacts. It calls no provider and recalculates
 * nothing: the course profile comes from the stored v2 result, module classifications from the module snapshot, and
 * every recommendation, safeguard and action from the deterministic guidance. The paid narrative only fills five
 * perspectives; without it, deterministic copy does.
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

    private final UniversityCourseProfileComposer profiles = new UniversityCourseProfileComposer();

    /** The paid-narrative input: decided facts only, no probabilities, rule ids or diagnostics. */
    public UniversityPaidNarrativeContext narrativeContext(StoredUniversityAssessment assessment,
            UniversityModuleAnalysisSnapshot modules, UniversityGuidance guidance) {
        UniversityCourseProfile profile = profiles.compose(assessment.result().profile());
        return new UniversityPaidNarrativeContext(assessment.id(), modules.snapshotId(), assessment.state().courseTitle(),
                profile.measures().stream().map(measure -> new UniversityPaidNarrativeContext.Measure(measure.name(),
                        measure.band(), measure.strengthenedBy(), measure.heldBackBy())).toList(),
                modules.items().stream().map(item -> new UniversityPaidNarrativeContext.Module(
                        item.judgments().moduleTitle(), item.displayLabel())).toList(),
                guidance.skillPriorities().stream().map(skill -> skill.title()).toList(),
                guidance.safeguards().stream().map(UniversityGuidanceItem::title).toList(),
                Stream.of(guidance.next30Days(), guidance.thisTerm(), guidance.next6To12Months())
                        .flatMap(List::stream).flatMap(action -> action.themes().stream())
                        .map(theme -> theme.title()).distinct().toList(),
                modules.items().stream().anyMatch(item -> item.classification()
                        .hasFlag(UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT)));
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
        return new UniversityDetailedReport(UniversityReportVersions.V2, state.courseTitle(),
                state.institution().orElse(""), profile, perspectives(profile, assessment, narrative),
                modules(modules, state.keyModules().size()), AI_LEARNING_CAUTION, entries(guidance.aiLearningOpportunities()),
                entries(guidance.ownCapabilityAreas()), entries(guidance.technicalFoundations()),
                guidance.skillPriorities().stream().map(skill -> new UniversityDetailedReport.Skill(skill.title(),
                        skill.explanation(), titles(modules, skill.moduleIds()))).toList(),
                entries(guidance.safeguards()),
                List.of(phase("Next 30 days", guidance.next30Days()), phase("This term", guidance.thisTerm()),
                        phase("Next 6 to 12 months", guidance.next6To12Months())),
                LIMITATIONS,
                new UniversityDetailedReport.Provenance(assessment.id(), modules.snapshotId(),
                        assessment.result().decisionPolicyVersion(), modules.schemaVersion(), modules.policyVersion(),
                        guidance.policyVersion(), narrative.narrativeVersion(), narrative.status().name(),
                        UniversityReportVersions.PDF_V2));
    }

    private static UniversityDetailedReport.Perspectives perspectives(UniversityCourseProfile profile,
            StoredUniversityAssessment assessment, UniversityPaidNarrativeOutcome outcome) {
        if (outcome.status() == UniversityPaidNarrativeStatus.AVAILABLE) {
            UniversityPaidNarrative narrative = outcome.narrative().orElseThrow();
            return new UniversityDetailedReport.Perspectives(true, narrative.overallPerspective().strip(),
                    narrative.aiLearningPerspective().strip(), narrative.humanPracticalPerspective().strip(),
                    narrative.transferablePerspective().strip(), narrative.actionPlanIntroduction().strip());
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
        return new UniversityDetailedReport.Perspectives(false, profile.summary(),
                "The modules below are where AI may help most with learning, preparation, exploration or checking.",
                "These modules place particular weight on your own judgement, practical skill or work with people.",
                transfer,
                "The plan below turns these findings into steps for the next 30 days, this term and the next 6 to 12 "
                        + "months.");
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

    private static List<UniversityDetailedReport.Entry> entries(List<UniversityGuidanceItem> items) {
        return items.stream().map(item -> new UniversityDetailedReport.Entry(item.title(), item.text())).toList();
    }

    private static UniversityDetailedReport.Phase phase(String title, List<UniversityGuidanceAction> actions) {
        return new UniversityDetailedReport.Phase(title, actions.stream().map(UniversityGuidanceAction::text).toList());
    }

    private static List<String> titles(UniversityModuleAnalysisSnapshot snapshot, List<Integer> moduleIds) {
        return snapshot.items().stream().filter(item -> moduleIds.contains(item.moduleId()))
                .map(item -> item.judgments().moduleTitle()).limit(3).toList();
    }
}
