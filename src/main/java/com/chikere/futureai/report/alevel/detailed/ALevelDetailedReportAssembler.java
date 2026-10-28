package com.chikere.futureai.report.alevel.detailed;

import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceBundle;
import com.chikere.futureai.evidence.alevel.application.EvidenceStatus;
import com.chikere.futureai.guidance.alevel.ALevelGuidance;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrative;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeContext;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeOutcome;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeStatus;
import com.chikere.futureai.report.alevel.application.ALevelReportVersions;
import com.chikere.futureai.report.alevel.application.ALevelSubjectProfile;
import com.chikere.futureai.report.alevel.application.ALevelSubjectProfileComposer;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Pure composition of the {@code a-level-report-v2} from frozen artefacts. It calls no provider and decides nothing:
 * the subject profile is the stored decision as the free result words it, the guidance is the deterministic policy's,
 * and the narrative only supplies explanations. Without an AVAILABLE narrative, deterministic copy fills the five
 * explanations. Evidence appears only when a configured provider stored some, and never changes anything else.
 */
public final class ALevelDetailedReportAssembler {

    static final List<String> LIMITATIONS = List.of(
            "This report describes your subject combination, not your ability, your grades or you as a person.",
            "It does not predict university offers, admission, employment or salary.",
            "It does not check entry requirements: each course publishes its own subject and grade requirements.",
            "AI tools, and school, college and exam-board rules on using them, change over time, so check the "
                    + "current rules.",
            "Any factual course information is shown separately with its source and never changes your profile.");

    static final String DETERMINISTIC_ACTION_PLAN_INTRODUCTION = "The plan below has three stages: things to start "
            + "now, things to build during the rest of sixth form, and things to check when you make applications.";

    private final ALevelSubjectProfileComposer profiles = new ALevelSubjectProfileComposer();

    /** The paid-narrative input: decided facts only, no scores, confidence values, grades or free text. */
    public ALevelPaidNarrativeContext narrativeContext(StoredALevelAssessment assessment, ALevelGuidance guidance) {
        ALevelSubjectProfile profile = profile(assessment);
        var state = assessment.state();
        return new ALevelPaidNarrativeContext(assessment.id(), state.selectedSubjects(),
                measures(profile).stream().map(measure -> new ALevelPaidNarrativeContext.Measure(measure.name(),
                        measure.bandLabel(), measure.drivers(), measure.limits())).toList(),
                titles(guidance.strengths()), titles(guidance.areasToStrengthen()),
                titles(guidance.skillPriorities()),
                titles(Stream.of(guidance.actionPlan().now(), guidance.actionPlan().sixthForm(),
                        guidance.actionPlan().applications()).flatMap(List::stream).toList()),
                titles(guidance.safeguards()), state.preferredDegreeAreas(), state.preferredCareerAreas());
    }

    public ALevelDetailedReport assemble(StoredALevelAssessment assessment, ALevelGuidance guidance,
            ALevelPaidNarrativeOutcome narrative, Optional<ALevelEvidenceBundle> evidence) {
        Objects.requireNonNull(assessment);
        Objects.requireNonNull(guidance);
        Objects.requireNonNull(narrative);
        Objects.requireNonNull(evidence);
        ALevelSubjectProfile profile = profile(assessment);
        Optional<ALevelPaidNarrative> prose = narrative.status() == ALevelPaidNarrativeStatus.AVAILABLE
                ? narrative.narrative() : Optional.empty();
        var options = guidance.options();
        List<ALevelDetailedReport.EvidenceItem> evidenceItems = evidenceItems(evidence);
        return new ALevelDetailedReport(ALevelReportVersions.V2, assessment.state().selectedSubjects(), profile,
                perspectives(profile, guidance, prose), entries(guidance.combinationShape()),
                entries(guidance.strengths()), entries(guidance.areasToStrengthen()),
                new ALevelDetailedReport.Options(options.canSay(), options.cannotSay(), entries(options.checks()),
                        options.gradesNote().orElse("")),
                new ALevelDetailedReport.AiStudy(guidance.aiStudy().caution(), entries(guidance.aiStudy().uses())),
                entries(guidance.skillPriorities()),
                new ALevelDetailedReport.ActionPlan(entries(guidance.actionPlan().now()),
                        entries(guidance.actionPlan().sixthForm()), entries(guidance.actionPlan().applications())),
                entries(guidance.safeguards()), evidenceItems, LIMITATIONS,
                new ALevelDetailedReport.Provenance(assessment.id(), assessment.result().judgmentSchemaVersion(),
                        assessment.result().decisionPolicyVersion(), guidance.policyVersion(),
                        narrative.narrativeVersion(), narrative.status().name(),
                        evidenceItems.isEmpty() ? "" : evidence.orElseThrow().evidenceVersion(),
                        ALevelReportVersions.PDF_V2));
    }

    private ALevelSubjectProfile profile(StoredALevelAssessment assessment) {
        if (!assessment.result().usesSubjectProfile()) {
            throw new IllegalArgumentException("a-level-report-v2 is only for a-level-decision-v2 assessments");
        }
        return profiles.compose(assessment.result().profile());
    }

    private static List<ALevelSubjectProfile.Measure> measures(ALevelSubjectProfile profile) {
        return List.of(profile.optionsKeptOpen(), profile.capabilityProfile().analyticalAndQuantitative(),
                profile.capabilityProfile().communicationInterpretationAndContext(), profile.learningAlongsideAi());
    }

    private static ALevelDetailedReport.Perspectives perspectives(ALevelSubjectProfile profile,
            ALevelGuidance guidance, Optional<ALevelPaidNarrative> prose) {
        if (prose.isPresent()) {
            ALevelPaidNarrative text = prose.get();
            return new ALevelDetailedReport.Perspectives(true, text.profileExplanation(), text.capabilityExplanation(),
                    text.optionsExplanation(), text.aiLearningExplanation(), text.actionPlanIntroduction());
        }
        return new ALevelDetailedReport.Perspectives(false, profile.summary(), profile.capabilityProfile().note(),
                String.join(" ", guidance.options().canSay()) + " Always check the published entry requirements of "
                        + "the courses you are considering.",
                guidance.combinationShape().getLast().text() + " " + guidance.aiStudy().caution(),
                DETERMINISTIC_ACTION_PLAN_INTRODUCTION);
    }

    /** Only stored, successful evidence with items; an absent or failed lookup shows no evidence section at all. */
    private static List<ALevelDetailedReport.EvidenceItem> evidenceItems(Optional<ALevelEvidenceBundle> evidence) {
        return evidence.filter(bundle -> bundle.status() == EvidenceStatus.AVAILABLE
                        || bundle.status() == EvidenceStatus.PARTIAL)
                .map(bundle -> bundle.items().stream().map(item -> new ALevelDetailedReport.EvidenceItem(
                        item.category().name(), item.title(), item.summary(), item.sourceName(),
                        item.sourceUrl().map(Object::toString).orElse(""), item.retrievedAt())).toList())
                .orElse(List.of());
    }

    private static List<ALevelDetailedReport.Entry> entries(List<ALevelGuidance.Item> items) {
        return items.stream().map(item -> new ALevelDetailedReport.Entry(item.title(), item.text(), item.provenance()))
                .toList();
    }

    private static List<String> titles(List<ALevelGuidance.Item> items) {
        return items.stream().map(ALevelGuidance.Item::title).toList();
    }
}
