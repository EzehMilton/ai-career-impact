package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.decision.professional.*;
import com.chikere.futureai.evidence.professional.application.*;
import com.chikere.futureai.narrative.professional.application.NarrativeStatus;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrative;

import java.util.*;

/** Pure deterministic composition of already persisted historical data. */
public final class ProfessionalReportAssembler {
    private static final String JOB_LOSS_DISCLAIMER =
            "AI Exposure describes characteristics of the work. It is not a probability that you will lose your job.";
    private static final String METHODOLOGY_CAVEAT =
            "This is a structured assessment of the work described, not a prediction or guarantee about the future.";

    public ProfessionalReport assemble(ProfessionalReportSource source, ProfessionalReportTier tier) {
        Objects.requireNonNull(source); Objects.requireNonNull(tier);
        var assessment = source.assessment();
        var decision = assessment.result().decision();
        Optional<ProfessionalNarrative> savedNarrative = source.narrative()
                .filter(value -> value.status() == NarrativeStatus.AVAILABLE)
                .flatMap(value -> value.narrative());
        String interpretation = savedNarrative.map(ProfessionalNarrative::summary)
                .orElseGet(() -> deterministicInterpretation(decision));
        String headline = tier == ProfessionalReportTier.PAID
                ? savedNarrative.map(ProfessionalNarrative::headline).orElse("Your detailed professional report")
                : "Your professional assessment";
        return new ProfessionalReport(ProfessionalReportVersions.CURRENT, tier,
                assessment.result().source() == AssessmentSource.LOCAL_FIXTURE, assessment.createdAt(),
                assessment.state().profession(), headline,
                new ProfessionalReport.AssessmentSummary(decision.assessmentDecision().status(),
                        decision.assessmentDecision().confidence(), interpretation,
                        JOB_LOSS_DISCLAIMER, METHODOLOGY_CAVEAT),
                measures(decision, savedNarrative, tier),
                decision.topExposureDrivers().stream().map(this::factorLabel).toList(),
                decision.topProtectiveFactors().stream().map(this::factorLabel).toList(),
                narrative(savedNarrative, tier),
                tier == ProfessionalReportTier.PAID
                        ? savedNarrative.map(ProfessionalNarrative::practicalActions).orElse(List.of()) : List.of(),
                tier == ProfessionalReportTier.PAID ? skillPriorities(decision) : List.of(),
                tier == ProfessionalReportTier.PAID ? workFocus(assessment, decision) : emptyWorkFocus(),
                evidence(source.evidence(), tier),
                tier == ProfessionalReportTier.PAID ? classification(source) : Optional.empty(),
                methodology(source));
    }

    private List<ProfessionalReport.MeasureSummary> measures(ProfessionalDecisionResult decision,
            Optional<ProfessionalNarrative> narrative, ProfessionalReportTier tier) {
        if (decision.assessmentDecision().status() == AssessmentStatus.MORE_INFORMATION_REQUIRED) return List.of();
        return Arrays.stream(ProfessionalMeasure.values()).map(measure -> {
            ProfessionalMeasureResult result = decision.measure(measure).orElseThrow();
            Optional<String> detail = tier == ProfessionalReportTier.PAID
                    ? narrative.flatMap(value -> detailedExplanation(value, measure)) : Optional.empty();
            return new ProfessionalReport.MeasureSummary(measure, result.indicator(), result.band(),
                    result.confidence(), conciseExplanation(measure), detail);
        }).toList();
    }

    private ProfessionalReport.NarrativeContent narrative(Optional<ProfessionalNarrative> narrative,
            ProfessionalReportTier tier) {
        if (narrative.isEmpty()) return new ProfessionalReport.NarrativeContent(false, Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
        ProfessionalNarrative value = narrative.orElseThrow();
        boolean paid = tier == ProfessionalReportTier.PAID;
        return new ProfessionalReport.NarrativeContent(true, Optional.of(value.summary()),
                Optional.of(value.whatThisMeans()),
                paid ? Optional.of(value.exposureExplanation()) : Optional.empty(),
                paid ? Optional.of(value.humanAdvantageExplanation()) : Optional.empty(),
                paid ? Optional.of(value.complementarityExplanation()) : Optional.empty(),
                paid ? Optional.of(value.adaptabilityExplanation()) : Optional.empty(),
                paid ? Optional.of(value.cautionOrLimitations()) : Optional.empty());
    }

    private List<ProfessionalReport.SkillDevelopmentPriority> skillPriorities(ProfessionalDecisionResult decision) {
        EnumSet<ProfessionalFactorDirection> directions = factorDirections(decision);
        List<ProfessionalReport.SkillDevelopmentPriority> priorities = new ArrayList<>();
        var complementarity = decision.measure(ProfessionalMeasure.AI_COMPLEMENTARITY).orElseThrow().band();
        priorities.add(priority(SkillDevelopmentCategory.AI_TOOL_FLUENCY,
                complementarity == IndicatorBand.HIGH ? SkillPriorityLevel.HIGH_PRIORITY : SkillPriorityLevel.USEFUL,
                "AI-tool fluency", "Build practical skill using relevant AI tools while retaining human review."));
        if (directions.contains(ProfessionalFactorDirection.HIGH_TASK_REPEATABILITY)
                || directions.contains(ProfessionalFactorDirection.HIGH_DIGITAL_EXECUTABILITY)) {
            priorities.add(priority(SkillDevelopmentCategory.PROCESS_AUTOMATION, SkillPriorityLevel.USEFUL,
                    "Process automation", "Learn to identify repeatable digital steps that can be assisted safely."));
        }
        addFactorPriority(priorities, directions, ProfessionalFactorDirection.HIGH_CONTEXTUAL_COMPLEXITY,
                SkillDevelopmentCategory.DOMAIN_EXPERTISE, "Domain expertise",
                "Deep context helps you judge when a technically plausible output does not fit the real situation.");
        addFactorPriority(priorities, directions, ProfessionalFactorDirection.HIGH_HUMAN_TRUST_DEPENDENCY,
                SkillDevelopmentCategory.HUMAN_COMMUNICATION, "Human communication",
                "Trust, explanation and relationship skills remain important to successful performance.");
        addFactorPriority(priorities, directions, ProfessionalFactorDirection.HIGH_ORIGINAL_JUDGMENT,
                SkillDevelopmentCategory.ORIGINAL_JUDGEMENT, "Original judgement",
                "Strengthen synthesis, framing and decision-making where standard answers are insufficient.");
        addFactorPriority(priorities, directions, ProfessionalFactorDirection.HIGH_ACCOUNTABILITY_RESPONSIBILITY,
                SkillDevelopmentCategory.ACCOUNTABILITY_AND_GOVERNANCE, "Accountability and governance",
                "Be able to document, challenge and take responsibility for consequential decisions involving AI.");
        if (complementarity != IndicatorBand.LOW) {
            priorities.add(priority(SkillDevelopmentCategory.TECHNICAL_DEPTH, SkillPriorityLevel.USEFUL,
                    "Complementary technical depth", "Understand the limits and appropriate use of tools in your domain."));
        }
        return List.copyOf(priorities);
    }

    private ProfessionalReport.ProfessionalWorkFocus workFocus(
            com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment assessment,
            ProfessionalDecisionResult decision) {
        EnumSet<ProfessionalFactorDirection> directions = factorDirections(decision);
        List<ProfessionalReport.WorkFocusGuidance> areas = new ArrayList<>();
        if (directions.contains(ProfessionalFactorDirection.HIGH_TASK_REPEATABILITY)
                || directions.contains(ProfessionalFactorDirection.HIGH_DIGITAL_EXECUTABILITY)) {
            areas.add(new ProfessionalReport.WorkFocusGuidance(WorkFocusCategory.AI_ASSISTANCE_OPPORTUNITY,
                    "Look for repeatable digital preparation work",
                    "Explore assistance for drafting, organising or checking repeatable work, with proportionate review.",
                    List.of()));
        }
        if (directions.contains(ProfessionalFactorDirection.HIGH_ACCOUNTABILITY_RESPONSIBILITY)
                || directions.contains(ProfessionalFactorDirection.HIGH_ORIGINAL_JUDGMENT)) {
            areas.add(new ProfessionalReport.WorkFocusGuidance(WorkFocusCategory.HUMAN_OVERSIGHT,
                    "Keep consequential judgement human-led",
                    "Retain clear human review and accountability where decisions have meaningful consequences.",
                    List.of()));
        }
        if (directions.contains(ProfessionalFactorDirection.HIGH_HUMAN_TRUST_DEPENDENCY)
                || directions.contains(ProfessionalFactorDirection.HIGH_CONTEXTUAL_COMPLEXITY)
                || directions.contains(ProfessionalFactorDirection.HIGH_PHYSICAL_WORLD_DEPENDENCY)) {
            areas.add(new ProfessionalReport.WorkFocusGuidance(WorkFocusCategory.STRENGTHEN_HUMAN_VALUE,
                    "Strengthen context-heavy and human-facing work",
                    "Invest in the parts of the role that depend on trust, situational context or real-world presence.",
                    List.of()));
        }
        if (!assessment.state().responsibilities().isEmpty()) {
            areas.add(new ProfessionalReport.WorkFocusGuidance(WorkFocusCategory.USER_REVIEW_REQUIRED,
                    "Review your submitted responsibilities individually",
                    "The saved assessment is role-level and did not judge each responsibility separately.",
                    assessment.state().responsibilities()));
        }
        return new ProfessionalReport.ProfessionalWorkFocus(areas,
                "This guidance uses saved role-level bands and factors. It does not assign task-level scores or claim that any individual responsibility is automatable.");
    }

    private ProfessionalReport.ProfessionalWorkFocus emptyWorkFocus() {
        return new ProfessionalReport.ProfessionalWorkFocus(List.of(),
                "Detailed work-focus guidance is part of the full report.");
    }

    private ProfessionalReport.EvidenceContent evidence(Optional<ProfessionalEvidenceBundle> stored,
            ProfessionalReportTier tier) {
        if (tier == ProfessionalReportTier.FREE) {
            return new ProfessionalReport.EvidenceContent(EvidenceStatus.NOT_REQUESTED,
                    "Verified external career context appears in the full report where available.", List.of());
        }
        if (stored.isEmpty() || stored.orElseThrow().status() == EvidenceStatus.FAILED
                || stored.orElseThrow().status() == EvidenceStatus.PENDING) {
            return new ProfessionalReport.EvidenceContent(EvidenceStatus.FAILED,
                    "External career context is currently unavailable.", List.of());
        }
        ProfessionalEvidenceBundle bundle = stored.orElseThrow();
        return new ProfessionalReport.EvidenceContent(bundle.status(),
                bundle.status() == EvidenceStatus.PARTIAL
                        ? "Some external career context is unavailable; saved items are shown."
                        : "Saved external career context related to this occupation.",
                bundle.items().stream().map(item -> new ProfessionalReport.EvidenceItem(
                        item.category().name(), item.title(), item.summary(), item.sourceName(), item.sourceUrl(),
                        item.publisher(), item.geography(), item.publicationDate(), item.retrievedAt(),
                        item.metricName(), item.metricValue(), item.metricUnit(), item.sourceQuality(),
                        item.relevanceNote())).toList());
    }

    private Optional<ProfessionalReport.OccupationClassification> classification(ProfessionalReportSource source) {
        return source.classification().map(stored -> {
            var value = stored.classification();
            return new ProfessionalReport.OccupationClassification(value.canonicalOccupation(),
                    value.classificationSystem().name(), value.classificationCode(), value.quality().name(),
                    stored.classifiedAt(), value.classifierVersion());
        });
    }

    private ProfessionalReport.Methodology methodology(ProfessionalReportSource source) {
        return new ProfessionalReport.Methodology(source.assessment().result().judgmentSchemaVersion(),
                source.assessment().result().decisionPolicyVersion(),
                source.narrative().map(value -> value.promptSchemaVersion()),
                source.evidence().map(ProfessionalEvidenceBundle::evidenceVersion),
                source.classification().map(value -> value.classification().classifierVersion()),
                ProfessionalReportVersions.CURRENT,
                List.of(JOB_LOSS_DISCLAIMER, METHODOLOGY_CAVEAT,
                        "External evidence is contextual and does not validate or determine the assessment scores.",
                        "Work-focus and skill guidance is deterministic presentation guidance, not a separate assessment."));
    }

    private String deterministicInterpretation(ProfessionalDecisionResult decision) {
        if (decision.assessmentDecision().status() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return "More information is required before the role can be interpreted reliably.";
        }
        var exposure = decision.measure(ProfessionalMeasure.AI_EXPOSURE).orElseThrow().band();
        var human = decision.measure(ProfessionalMeasure.HUMAN_ADVANTAGE).orElseThrow().band();
        return "This assessment suggests " + lower(exposure.name()) + " AI exposure alongside "
                + lower(human.name()) + " human advantage in the work described.";
    }

    private Optional<String> detailedExplanation(ProfessionalNarrative narrative, ProfessionalMeasure measure) {
        return Optional.of(switch (measure) {
            case AI_EXPOSURE -> narrative.exposureExplanation();
            case HUMAN_ADVANTAGE -> narrative.humanAdvantageExplanation();
            case AI_COMPLEMENTARITY -> narrative.complementarityExplanation();
            case ROLE_ADAPTABILITY -> narrative.adaptabilityExplanation();
        });
    }

    private String conciseExplanation(ProfessionalMeasure measure) {
        return switch (measure) {
            case AI_EXPOSURE -> "How much the work overlaps with characteristics AI can perform through digital systems.";
            case HUMAN_ADVANTAGE -> "How strongly the work relies on human trust, context, physical presence, judgement and accountability.";
            case AI_COMPLEMENTARITY -> "How much scope there is for AI assistance while meaningful human contribution remains.";
            case ROLE_ADAPTABILITY -> "How much the structure of the role can accommodate technological change "
                    + "while retaining meaningful human contribution.";
        };
    }

    private EnumSet<ProfessionalFactorDirection> factorDirections(ProfessionalDecisionResult decision) {
        EnumSet<ProfessionalFactorDirection> values = EnumSet.noneOf(ProfessionalFactorDirection.class);
        decision.topExposureDrivers().forEach(value -> values.add(value.direction()));
        decision.topProtectiveFactors().forEach(value -> values.add(value.direction()));
        return values;
    }

    private void addFactorPriority(List<ProfessionalReport.SkillDevelopmentPriority> target,
            Set<ProfessionalFactorDirection> directions, ProfessionalFactorDirection direction,
            SkillDevelopmentCategory category, String title, String rationale) {
        if (directions.contains(direction)) target.add(priority(category, SkillPriorityLevel.HIGH_PRIORITY, title, rationale));
    }

    private ProfessionalReport.SkillDevelopmentPriority priority(SkillDevelopmentCategory category,
            SkillPriorityLevel level, String title, String rationale) {
        return new ProfessionalReport.SkillDevelopmentPriority(category, level, title, rationale);
    }

    private String factorLabel(ProfessionalDecisionFactor factor) {
        return ProfessionalFactorDescriptions.describe(factor.direction());
    }

    private String lower(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
