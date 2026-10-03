package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.alevel.ALevelDecisionFactor;
import com.chikere.futureai.decision.alevel.ALevelDecisionResult;
import com.chikere.futureai.decision.alevel.ALevelFactorDirection;
import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.decision.alevel.ALevelMeasureResult;
import com.chikere.futureai.decision.alevel.OptionalityBand;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceBundle;
import com.chikere.futureai.evidence.alevel.application.EvidenceStatus;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrative;
import com.chikere.futureai.narrative.alevel.application.NarrativeStatus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Pure deterministic composition of already persisted historical data.
 *
 * <p>It cannot call Jev, the decision policy, OpenAI, an evidence provider or any external API.
 * FREE and PAID use exactly the same saved status, confidence, measures, bands and factors;
 * payment changes presentation and access only.</p>
 */
public final class ALevelReportAssembler {

    private static final String ADMISSIONS_DISCLAIMER =
            "Future Optionality describes the capabilities and pathways your current combination appears "
                    + "to keep open. It is not a prediction of university admission, not a guarantee of "
                    + "career success, and not a verdict on your subject choices.";
    private static final String METHODOLOGY_CAVEAT =
            "This is a structured reading of the combination you described. It is not an admissions "
                    + "decision, not official careers guidance, and not a ranking of subjects.";
    private static final String CROSS_JOURNEY_CAVEAT =
            "A-level Future Optionality, university Future Readiness and professional AI Exposure are "
                    + "different constructs on different scales. Their numbers must not be compared.";
    private static final String EVIDENCE_SEPARATION_NOTE =
            "These are factual notes with their own sources. They are shown separately because subject "
                    + "requirements are facts to check, not part of your assessment, and they do not change "
                    + "any measure above.";

    public ALevelReport assemble(ALevelReportSource source, ALevelReportTier tier) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(tier);
        var assessment = source.assessment();
        var state = assessment.state();
        var decision = assessment.result().decision();
        boolean paid = tier == ALevelReportTier.PAID;
        Optional<ALevelNarrative> savedNarrative = source.narrative()
                .filter(value -> value.status() == NarrativeStatus.AVAILABLE)
                .flatMap(value -> value.narrative());
        String interpretation = savedNarrative.map(ALevelNarrative::summary)
                .orElseGet(() -> deterministicInterpretation(decision));
        String headline = paid
                ? savedNarrative.map(ALevelNarrative::headline).orElse("Your detailed A-level report")
                : "Your A-level subject assessment";

        return new ALevelReport(ALevelReportVersions.V1, tier,
                assessment.result().source() == AssessmentSource.LOCAL_FIXTURE, assessment.createdAt(),
                state.selectedSubjects(), state.predictedGrades(), headline,
                new ALevelReport.AssessmentSummary(decision.assessmentDecision().status(),
                        decision.assessmentDecision().confidence(), interpretation,
                        ADMISSIONS_DISCLAIMER, METHODOLOGY_CAVEAT),
                measures(decision, savedNarrative, paid),
                decision.topStrengths().stream().map(this::factorLabel).toList(),
                decision.topDevelopmentOpportunities().stream().map(this::factorLabel).toList(),
                narrative(savedNarrative, paid),
                paid ? savedNarrative.map(ALevelNarrative::practicalActions).orElse(List.of()) : List.of(),
                paid ? guidance(decision) : List.of(),
                preferredAreas(state.preferredDegreeAreas(), state.preferredCareerAreas(), paid),
                evidence(source.evidence(), paid),
                methodology(source));
    }

    private List<ALevelReport.MeasureSummary> measures(ALevelDecisionResult decision,
            Optional<ALevelNarrative> narrative, boolean paid) {
        if (decision.assessmentDecision().status() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return List.of();
        }
        return Arrays.stream(ALevelMeasure.values()).map(measure -> {
            ALevelMeasureResult result = decision.measure(measure).orElseThrow();
            Optional<String> detail = paid
                    ? narrative.flatMap(value -> detailedExplanation(value, measure)) : Optional.empty();
            return new ALevelReport.MeasureSummary(measure, result.indicator(), result.band(),
                    result.confidence(), conciseExplanation(measure), detail);
        }).toList();
    }

    private ALevelReport.NarrativeContent narrative(Optional<ALevelNarrative> narrative, boolean paid) {
        if (narrative.isEmpty()) {
            return new ALevelReport.NarrativeContent(false, Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        }
        ALevelNarrative value = narrative.orElseThrow();
        return new ALevelReport.NarrativeContent(true,
                Optional.of(value.summary()),
                Optional.of(value.whatThisMeans()),
                paid ? Optional.of(value.futureOptionalityExplanation()) : Optional.empty(),
                paid ? Optional.of(value.strengthsExplanation()) : Optional.empty(),
                paid ? Optional.of(value.degreePathwayExplanation()) : Optional.empty(),
                paid ? Optional.of(value.analyticalFoundationExplanation()) : Optional.empty(),
                paid ? Optional.of(value.humanAndCreativeExplanation()) : Optional.empty(),
                paid ? Optional.of(value.transferabilityExplanation()) : Optional.empty(),
                paid ? Optional.of(value.adaptabilityExplanation()) : Optional.empty(),
                paid ? Optional.of(value.developmentOpportunitiesExplanation()) : Optional.empty(),
                paid ? Optional.of(value.cautionOrLimitations()) : Optional.empty());
    }

    /**
     * Deterministic guidance. Nothing here ever suggests changing or dropping an A-level: every
     * item is something a student can do alongside the subjects they already have.
     */
    private List<ALevelReport.PracticalGuidance> guidance(ALevelDecisionResult decision) {
        if (decision.assessmentDecision().status() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return List.of();
        }
        EnumSet<ALevelFactorDirection> directions = factorDirections(decision);
        List<ALevelReport.PracticalGuidance> guidance = new ArrayList<>();
        guidance.add(new ALevelReport.PracticalGuidance(GuidanceArea.COURSE_REQUIREMENT_RESEARCH,
                GuidanceCategory.WORTH_STRENGTHENING, "Check specific course requirements early",
                "If a particular degree interests you, look up that course's own published subject "
                        + "requirements rather than relying on general advice."));
        OptionalityBand adaptability = decision.measure(ALevelMeasure.AI_ERA_ADAPTABILITY)
                .orElseThrow().band();
        guidance.add(new ALevelReport.PracticalGuidance(GuidanceArea.DIGITAL_AND_DATA_LITERACY,
                adaptability == OptionalityBand.BROAD
                        ? GuidanceCategory.KEEP_DEVELOPING : GuidanceCategory.CONSIDER_SUPPLEMENTING,
                "Build basic coding and data literacy",
                "A little practical experience with spreadsheets, data or simple code goes a long way "
                        + "alongside any subject combination."));
        add(guidance, directions, ALevelFactorDirection.LOW_QUANTITATIVE_REASONING,
                GuidanceArea.QUANTITATIVE_CONFIDENCE, GuidanceCategory.CONSIDER_SUPPLEMENTING,
                "Develop quantitative confidence",
                "Working with numbers and statistics outside your subjects can widen the options "
                        + "available to you later.");
        add(guidance, directions, ALevelFactorDirection.LOW_WRITTEN_VERBAL_COMMUNICATION,
                GuidanceArea.WRITTEN_COMMUNICATION, GuidanceCategory.CONSIDER_SUPPLEMENTING,
                "Strengthen extended writing",
                "Practice at building a written argument is useful for almost every degree and most "
                        + "kinds of work.");
        add(guidance, directions, ALevelFactorDirection.LOW_TECHNICAL_DIGITAL_FOUNDATION,
                GuidanceArea.DIGITAL_AND_DATA_LITERACY, GuidanceCategory.CONSIDER_SUPPLEMENTING,
                "Add some technical grounding",
                "An online course, club or personal project can add technical grounding without "
                        + "changing anything about your A-levels.");
        add(guidance, directions, ALevelFactorDirection.LOW_SUBJECT_SKILL_DIVERSITY,
                GuidanceArea.BREADTH_AND_VARIETY, GuidanceCategory.CONSIDER_SUPPLEMENTING,
                "Look for variety outside your subjects",
                "Activities that use different kinds of capability from your subjects can broaden what "
                        + "you are able to show.");
        add(guidance, directions, ALevelFactorDirection.LOW_CONTEXTUAL_SOCIAL_UNDERSTANDING,
                GuidanceArea.CONTEXTUAL_UNDERSTANDING, GuidanceCategory.CONSIDER_SUPPLEMENTING,
                "Read widely about people and systems",
                "Reading about history, economics or society adds context that complements technical "
                        + "and analytical work.");
        add(guidance, directions, ALevelFactorDirection.HIGH_ORIGINAL_JUDGMENT_CREATIVITY,
                GuidanceArea.CREATIVE_AND_INDEPENDENT_WORK, GuidanceCategory.KEEP_DEVELOPING,
                "Keep a record of your independent work",
                "Your combination already builds independent and original work; keeping examples makes "
                        + "it visible to others.");
        add(guidance, directions, ALevelFactorDirection.HIGH_ANALYTICAL_REASONING,
                GuidanceArea.ANALYTICAL_REASONING, GuidanceCategory.KEEP_DEVELOPING,
                "Keep building structured reasoning",
                "This is already a clear strength of your combination and transfers almost anywhere.");
        return List.copyOf(guidance);
    }

    private ALevelReport.PreferredAreaContext preferredAreas(List<String> degreeAreas,
            List<String> careerAreas, boolean paid) {
        if (!paid) {
            return new ALevelReport.PreferredAreaContext(List.of(), List.of(),
                    "Context for the degree and career areas you mentioned is included in the full report.",
                    "This assessment describes your subject combination, not any individual outcome.");
        }
        if (degreeAreas.isEmpty() && careerAreas.isEmpty()) {
            return new ALevelReport.PreferredAreaContext(List.of(), List.of(),
                    "You have not named any degree or career areas yet. That is completely normal at this "
                            + "stage, and this report stays focused on the capabilities your subjects build.",
                    "This assessment describes your subject combination, not any individual outcome.");
        }
        return new ALevelReport.PreferredAreaContext(degreeAreas, careerAreas,
                "These are relevant areas to research further. The capabilities above are skills that may "
                        + "help, and it is always worth checking the published subject requirements of any "
                        + "specific course you are considering.",
                "This is context only. The assessment does not judge these areas, does not rank them, does "
                        + "not confirm that you meet any requirement, and does not recommend that you enter "
                        + "any of them.");
    }

    private ALevelReport.EvidenceContent evidence(Optional<ALevelEvidenceBundle> stored, boolean paid) {
        if (!paid) {
            return new ALevelReport.EvidenceContent(EvidenceStatus.NOT_REQUESTED,
                    "Verified factual course context appears in the full report where available.",
                    EVIDENCE_SEPARATION_NOTE, List.of());
        }
        if (stored.isEmpty() || stored.orElseThrow().status() == EvidenceStatus.FAILED
                || stored.orElseThrow().status() == EvidenceStatus.PENDING) {
            return new ALevelReport.EvidenceContent(EvidenceStatus.FAILED,
                    "Factual course context is currently unavailable.", EVIDENCE_SEPARATION_NOTE, List.of());
        }
        ALevelEvidenceBundle bundle = stored.orElseThrow();
        return new ALevelReport.EvidenceContent(bundle.status(),
                bundle.status() == EvidenceStatus.PARTIAL
                        ? "Some factual context is unavailable; saved items are shown."
                        : "Saved factual context related to these subjects and areas.",
                EVIDENCE_SEPARATION_NOTE,
                bundle.items().stream().map(item -> new ALevelReport.EvidenceItem(
                        item.category().name(), item.title(), item.summary(), item.sourceName(),
                        item.sourceUrl(), item.publisher(), item.geography(), item.publicationDate(),
                        item.retrievedAt(), item.metricName(), item.metricValue(), item.metricUnit(),
                        item.sourceQuality(), item.relevanceNote())).toList());
    }

    private ALevelReport.Methodology methodology(ALevelReportSource source) {
        return new ALevelReport.Methodology(source.assessment().result().judgmentSchemaVersion(),
                source.assessment().result().decisionPolicyVersion(),
                source.narrative().map(value -> value.promptSchemaVersion()),
                source.evidence().map(ALevelEvidenceBundle::evidenceVersion),
                ALevelReportVersions.V1,
                List.of(ADMISSIONS_DISCLAIMER, METHODOLOGY_CAVEAT, CROSS_JOURNEY_CAVEAT,
                        "The weights behind these measures are provisional product policy, not validated "
                                + "educational research, and no subject is awarded points of its own.",
                        "Subject requirements for specific courses are factual information shown only in "
                                + "the evidence section with their own source; they never affect a measure.",
                        "The assessment reads the combination as you described it and does not see any "
                                + "school, exam board or admissions data."));
    }

    private String deterministicInterpretation(ALevelDecisionResult decision) {
        if (decision.assessmentDecision().status() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return "A little more detail about your subjects is needed before this can be interpreted "
                    + "usefully.";
        }
        var optionality = decision.measure(ALevelMeasure.FUTURE_OPTIONALITY).orElseThrow().band();
        var pathways = decision.measure(ALevelMeasure.DEGREE_PATHWAY_BREADTH).orElseThrow().band();
        return "This assessment suggests " + lower(optionality.name()) + " future optionality, with "
                + lower(pathways.name()) + " breadth across plausible degree pathways.";
    }

    private Optional<String> detailedExplanation(ALevelNarrative narrative, ALevelMeasure measure) {
        return Optional.of(switch (measure) {
            case FUTURE_OPTIONALITY -> narrative.futureOptionalityExplanation();
            case DEGREE_PATHWAY_BREADTH -> narrative.degreePathwayExplanation();
            case ANALYTICAL_FOUNDATION -> narrative.analyticalFoundationExplanation();
            case HUMAN_AND_CREATIVE_STRENGTH -> narrative.humanAndCreativeExplanation();
            case SKILL_TRANSFERABILITY -> narrative.transferabilityExplanation();
            case AI_ERA_ADAPTABILITY -> narrative.adaptabilityExplanation();
        });
    }

    private String conciseExplanation(ALevelMeasure measure) {
        return switch (measure) {
            case FUTURE_OPTIONALITY -> "How wide a range of plausible next steps this combination "
                    + "currently appears to keep open.";
            case DEGREE_PATHWAY_BREADTH -> "How many different higher-education directions this "
                    + "combination appears to leave available.";
            case ANALYTICAL_FOUNDATION -> "How much structured reasoning, quantitative work and "
                    + "technical grounding the combination builds.";
            case HUMAN_AND_CREATIVE_STRENGTH -> "How much communication, interpretation, creativity and "
                    + "understanding of context the combination builds.";
            case SKILL_TRANSFERABILITY -> "How widely the capabilities these subjects build apply "
                    + "beyond the subjects themselves.";
            case AI_ERA_ADAPTABILITY -> "How well the mix of capabilities supports learning and working "
                    + "alongside AI tools.";
        };
    }

    private EnumSet<ALevelFactorDirection> factorDirections(ALevelDecisionResult decision) {
        EnumSet<ALevelFactorDirection> values = EnumSet.noneOf(ALevelFactorDirection.class);
        decision.topStrengths().forEach(value -> values.add(value.direction()));
        decision.topDevelopmentOpportunities().forEach(value -> values.add(value.direction()));
        return values;
    }

    private void add(List<ALevelReport.PracticalGuidance> target, Set<ALevelFactorDirection> directions,
            ALevelFactorDirection direction, GuidanceArea area, GuidanceCategory category,
            String title, String suggestion) {
        if (directions.contains(direction)) {
            target.add(new ALevelReport.PracticalGuidance(area, category, title, suggestion));
        }
    }

    private String factorLabel(ALevelDecisionFactor factor) {
        return switch (factor.direction()) {
            case HIGH_QUANTITATIVE_REASONING -> "Your combination gives you a strong quantitative foundation";
            case LOW_QUANTITATIVE_REASONING -> "Your combination develops less quantitative work than some alternatives";
            case HIGH_ANALYTICAL_REASONING -> "Your combination builds strong structured analysis and problem solving";
            case LOW_ANALYTICAL_REASONING -> "Your combination develops less structured analysis than some alternatives";
            case HIGH_WRITTEN_VERBAL_COMMUNICATION -> "Your combination builds strong written argument and explanation";
            case LOW_WRITTEN_VERBAL_COMMUNICATION -> "Your combination involves less extended writing than some alternatives";
            case HIGH_ORIGINAL_JUDGMENT_CREATIVITY -> "Your combination builds strong interpretation, originality and design";
            case LOW_ORIGINAL_JUDGMENT_CREATIVITY -> "Your combination leaves less room for original and independent work than some alternatives";
            case HIGH_CONTEXTUAL_SOCIAL_UNDERSTANDING -> "Your combination builds a strong understanding of human and social context";
            case LOW_CONTEXTUAL_SOCIAL_UNDERSTANDING -> "Your combination engages less with human and social context than some alternatives";
            case HIGH_TECHNICAL_DIGITAL_FOUNDATION -> "Your combination builds a strong technical and digital foundation";
            case LOW_TECHNICAL_DIGITAL_FOUNDATION -> "Your combination develops less technical and digital depth than some alternatives";
            case HIGH_SUBJECT_SKILL_DIVERSITY -> "Your subjects build a genuinely varied mix of capability types";
            case LOW_SUBJECT_SKILL_DIVERSITY -> "Your subjects build fairly similar kinds of capability to one another";
            case HIGH_DEGREE_PATHWAY_BREADTH -> "Your combination keeps a broad range of degree routes open";
            case LOW_DEGREE_PATHWAY_BREADTH -> "Your combination points towards a more focused set of degree routes";
            case HIGH_SKILL_TRANSFERABILITY -> "The capabilities you are building transfer widely";
            case LOW_SKILL_TRANSFERABILITY -> "The capabilities you are building are more closely tied to one area";
            case HIGH_AI_ERA_COMPLEMENTARITY -> "Your combination gives a good foundation for working alongside AI tools";
            case LOW_AI_ERA_COMPLEMENTARITY -> "Your combination offers a narrower foundation for AI-assisted work than some alternatives";
        };
    }

    private String lower(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
