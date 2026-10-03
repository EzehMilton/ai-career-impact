package com.chikere.futureai.report.university.application;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.decision.university.ReadinessBand;
import com.chikere.futureai.decision.university.UniversityDecisionFactor;
import com.chikere.futureai.decision.university.UniversityDecisionResult;
import com.chikere.futureai.decision.university.UniversityFactorDirection;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.decision.university.UniversityMeasureResult;
import com.chikere.futureai.evidence.university.application.EvidenceStatus;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceBundle;
import com.chikere.futureai.narrative.university.application.NarrativeStatus;
import com.chikere.futureai.narrative.university.application.UniversityNarrative;

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
public final class UniversityReportAssembler {

    private static final String EMPLOYMENT_DISCLAIMER =
            "Future Readiness describes capabilities this course appears to develop. It is not a prediction "
                    + "about any particular job and it is not a guarantee of employment.";
    private static final String METHODOLOGY_CAVEAT =
            "This is a structured assessment of the course as you described it, not a ranking, an official "
                    + "source, or a verdict on whether you should study it.";
    private static final String CROSS_JOURNEY_CAVEAT =
            "University Future Readiness and professional AI Exposure are different constructs on different "
                    + "scales. The two numbers must not be compared with each other.";

    public UniversityReport assemble(UniversityReportSource source, UniversityReportTier tier) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(tier);
        var assessment = source.assessment();
        var state = assessment.state();
        var decision = assessment.result().decision();
        boolean paid = tier == UniversityReportTier.PAID;
        Optional<UniversityNarrative> savedNarrative = source.narrative()
                .filter(value -> value.status() == NarrativeStatus.AVAILABLE)
                .flatMap(value -> value.narrative());
        String interpretation = savedNarrative.map(UniversityNarrative::summary)
                .orElseGet(() -> deterministicInterpretation(decision));
        String headline = paid
                ? savedNarrative.map(UniversityNarrative::headline).orElse("Your detailed course report")
                : "Your course assessment";

        return new UniversityReport(UniversityReportVersions.V1, tier,
                assessment.result().source() == AssessmentSource.LOCAL_FIXTURE, assessment.createdAt(),
                state.courseTitle(), state.institution(), headline,
                new UniversityReport.AssessmentSummary(decision.assessmentDecision().status(),
                        decision.assessmentDecision().confidence(), interpretation,
                        EMPLOYMENT_DISCLAIMER, METHODOLOGY_CAVEAT),
                measures(decision, savedNarrative, paid),
                decision.topStrengths().stream().map(this::factorLabel).toList(),
                decision.topDevelopmentOpportunities().stream().map(this::factorLabel).toList(),
                narrative(savedNarrative, paid),
                paid ? savedNarrative.map(UniversityNarrative::practicalActions).orElse(List.of()) : List.of(),
                paid ? skillPriorities(decision) : List.of(),
                careerAreas(state.preferredCareerAreas(), paid),
                evidence(source.evidence(), paid),
                methodology(source));
    }

    private List<UniversityReport.MeasureSummary> measures(UniversityDecisionResult decision,
            Optional<UniversityNarrative> narrative, boolean paid) {
        if (decision.assessmentDecision().status() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return List.of();
        }
        return Arrays.stream(UniversityMeasure.values()).map(measure -> {
            UniversityMeasureResult result = decision.measure(measure).orElseThrow();
            Optional<String> detail = paid
                    ? narrative.flatMap(value -> detailedExplanation(value, measure)) : Optional.empty();
            return new UniversityReport.MeasureSummary(measure, result.indicator(), result.band(),
                    result.confidence(), conciseExplanation(measure), detail);
        }).toList();
    }

    private UniversityReport.NarrativeContent narrative(Optional<UniversityNarrative> narrative, boolean paid) {
        if (narrative.isEmpty()) {
            return new UniversityReport.NarrativeContent(false, Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                    Optional.empty(), Optional.empty(), Optional.empty());
        }
        UniversityNarrative value = narrative.orElseThrow();
        return new UniversityReport.NarrativeContent(true,
                Optional.of(value.summary()),
                Optional.of(value.whatThisMeans()),
                paid ? Optional.of(value.futureReadinessExplanation()) : Optional.empty(),
                paid ? Optional.of(value.strengthsExplanation()) : Optional.empty(),
                paid ? Optional.of(value.developmentOpportunitiesExplanation()) : Optional.empty(),
                paid ? Optional.of(value.complementarityExplanation()) : Optional.empty(),
                paid ? Optional.of(value.transferableSkillsExplanation()) : Optional.empty(),
                paid ? Optional.of(value.careerAdaptabilityExplanation()) : Optional.empty(),
                paid ? Optional.of(value.cautionOrLimitations()) : Optional.empty());
    }

    private List<UniversityReport.SkillDevelopmentPriority> skillPriorities(UniversityDecisionResult decision) {
        if (decision.assessmentDecision().status() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return List.of();
        }
        EnumSet<UniversityFactorDirection> directions = factorDirections(decision);
        List<UniversityReport.SkillDevelopmentPriority> priorities = new ArrayList<>();
        ReadinessBand complementarity = decision.measure(UniversityMeasure.AI_COMPLEMENTARITY)
                .orElseThrow().band();
        priorities.add(priority(SkillDevelopmentCategory.AI_TOOL_FLUENCY,
                complementarity == ReadinessBand.STRONG
                        ? SkillPriorityLevel.WORTH_STRENGTHENING : SkillPriorityLevel.USEFUL,
                "Practical AI-tool fluency",
                "Learn where AI tools genuinely help with this kind of work, and where your own checking "
                        + "still matters."));
        addPriority(priorities, directions, UniversityFactorDirection.LOW_ANALYTICAL_REASONING,
                SkillDevelopmentCategory.ANALYTICAL_REASONING, "Structured analysis",
                "Supplementing the course with quantitative or analytical practice may broaden your options.");
        addPriority(priorities, directions, UniversityFactorDirection.LOW_TECHNICAL_DIGITAL_DEPTH,
                SkillDevelopmentCategory.TECHNICAL_DEPTH, "Digital and technical confidence",
                "Adding practical digital or technical skill alongside the course is worth considering.");
        addPriority(priorities, directions, UniversityFactorDirection.LOW_PRACTICAL_REAL_WORLD_APPLICATION,
                SkillDevelopmentCategory.APPLIED_EXPERIENCE, "Applied experience",
                "Projects, placements or society work can supplement a mainly theoretical course.");
        addPriority(priorities, directions, UniversityFactorDirection.LOW_HUMAN_RELATIONSHIP_DEPENDENCY,
                SkillDevelopmentCategory.COMMUNICATION_AND_COLLABORATION, "Communication and collaboration",
                "Opportunities to present, write for others and work in teams are worth seeking out.");
        addPriority(priorities, directions, UniversityFactorDirection.LOW_SKILL_TRANSFERABILITY,
                SkillDevelopmentCategory.BREADTH_AND_TRANSFERABILITY, "Breadth beyond the subject",
                "Optional modules or activities outside the core subject may widen where the skills apply.");
        if (directions.contains(UniversityFactorDirection.HIGH_ORIGINAL_JUDGMENT_CREATIVITY)) {
            priorities.add(priority(SkillDevelopmentCategory.ORIGINAL_JUDGEMENT, SkillPriorityLevel.USEFUL,
                    "Independent judgement", "This course already builds independent judgement; keeping a "
                            + "portfolio of your own work makes it visible to others."));
        }
        return List.copyOf(priorities);
    }

    private UniversityReport.CareerAreaContext careerAreas(List<String> preferred, boolean paid) {
        if (!paid) {
            return new UniversityReport.CareerAreaContext(List.of(),
                    "Career-area context is included in the full report.",
                    "This assessment describes the course, not any individual career outcome.");
        }
        if (preferred.isEmpty()) {
            return new UniversityReport.CareerAreaContext(List.of(),
                    "You did not list any career areas, so this report stays focused on the course itself.",
                    "This assessment describes the course, not any individual career outcome.");
        }
        return new UniversityReport.CareerAreaContext(preferred,
                "These remain relevant areas to explore. The capabilities described above are skills that may "
                        + "help, and additional preparation is worth considering alongside the course.",
                "This is context only. The assessment does not judge these career areas, does not rank them "
                        + "and does not recommend that you enter any of them.");
    }

    private UniversityReport.EvidenceContent evidence(Optional<UniversityEvidenceBundle> stored, boolean paid) {
        if (!paid) {
            return new UniversityReport.EvidenceContent(EvidenceStatus.NOT_REQUESTED,
                    "Verified external course context appears in the full report where available.", List.of());
        }
        if (stored.isEmpty() || stored.orElseThrow().status() == EvidenceStatus.FAILED
                || stored.orElseThrow().status() == EvidenceStatus.PENDING) {
            return new UniversityReport.EvidenceContent(EvidenceStatus.FAILED,
                    "External course context is currently unavailable.", List.of());
        }
        UniversityEvidenceBundle bundle = stored.orElseThrow();
        return new UniversityReport.EvidenceContent(bundle.status(),
                bundle.status() == EvidenceStatus.PARTIAL
                        ? "Some external context is unavailable; saved items are shown."
                        : "Saved external context related to this kind of course.",
                bundle.items().stream().map(item -> new UniversityReport.EvidenceItem(
                        item.category().name(), item.title(), item.summary(), item.sourceName(), item.sourceUrl(),
                        item.publisher(), item.geography(), item.publicationDate(), item.retrievedAt(),
                        item.metricName(), item.metricValue(), item.metricUnit(), item.sourceQuality(),
                        item.relevanceNote())).toList());
    }

    private UniversityReport.Methodology methodology(UniversityReportSource source) {
        return new UniversityReport.Methodology(source.assessment().result().judgmentSchemaVersion(),
                source.assessment().result().decisionPolicyVersion(),
                source.narrative().map(value -> value.promptSchemaVersion()),
                source.evidence().map(UniversityEvidenceBundle::evidenceVersion),
                UniversityReportVersions.V1,
                List.of(EMPLOYMENT_DISCLAIMER, METHODOLOGY_CAVEAT, CROSS_JOURNEY_CAVEAT,
                        "The weights behind these measures are provisional product policy, not validated "
                                + "educational research.",
                        "The assessment reads the course as you described it. It does not read any official "
                                + "course specification or institutional data.",
                        "External context is informational and does not determine or validate the measures."));
    }

    private String deterministicInterpretation(UniversityDecisionResult decision) {
        if (decision.assessmentDecision().status() == AssessmentStatus.MORE_INFORMATION_REQUIRED) {
            return "More information about the course is needed before it can be interpreted usefully.";
        }
        var readiness = decision.measure(UniversityMeasure.FUTURE_READINESS).orElseThrow().band();
        var differentiation = decision.measure(UniversityMeasure.HUMAN_DIFFERENTIATION).orElseThrow().band();
        return "This assessment suggests " + lower(readiness.name()) + " future readiness alongside "
                + lower(differentiation.name()) + " human differentiation in the course as described.";
    }

    private Optional<String> detailedExplanation(UniversityNarrative narrative, UniversityMeasure measure) {
        return Optional.of(switch (measure) {
            case FUTURE_READINESS -> narrative.futureReadinessExplanation();
            case AI_COMPLEMENTARITY -> narrative.complementarityExplanation();
            case HUMAN_DIFFERENTIATION -> narrative.strengthsExplanation();
            case SKILL_TRANSFERABILITY -> narrative.transferableSkillsExplanation();
            case CAREER_ADAPTABILITY -> narrative.careerAdaptabilityExplanation();
        });
    }

    private String conciseExplanation(UniversityMeasure measure) {
        return switch (measure) {
            case FUTURE_READINESS -> "How well this course appears to develop capabilities that stay valuable "
                    + "as AI tools become more capable.";
            case AI_COMPLEMENTARITY -> "How much scope there is to work productively alongside AI while your "
                    + "own contribution still matters.";
            case HUMAN_DIFFERENTIATION -> "How much the course develops judgement, communication, context and "
                    + "practical capability that remain distinctly human.";
            case SKILL_TRANSFERABILITY -> "How widely the capabilities this course develops apply beyond one "
                    + "specific field.";
            case CAREER_ADAPTABILITY -> "How broad the range of plausible pathways from this course appears "
                    + "to be.";
        };
    }

    private EnumSet<UniversityFactorDirection> factorDirections(UniversityDecisionResult decision) {
        EnumSet<UniversityFactorDirection> values = EnumSet.noneOf(UniversityFactorDirection.class);
        decision.topStrengths().forEach(value -> values.add(value.direction()));
        decision.topDevelopmentOpportunities().forEach(value -> values.add(value.direction()));
        return values;
    }

    private void addPriority(List<UniversityReport.SkillDevelopmentPriority> target,
            Set<UniversityFactorDirection> directions, UniversityFactorDirection direction,
            SkillDevelopmentCategory category, String title, String rationale) {
        if (directions.contains(direction)) {
            target.add(priority(category, SkillPriorityLevel.WORTH_STRENGTHENING, title, rationale));
        }
    }

    private UniversityReport.SkillDevelopmentPriority priority(SkillDevelopmentCategory category,
            SkillPriorityLevel level, String title, String rationale) {
        return new UniversityReport.SkillDevelopmentPriority(category, level, title, rationale);
    }

    private String factorLabel(UniversityDecisionFactor factor) {
        return switch (factor.direction()) {
            case HIGH_TECHNICAL_DIGITAL_DEPTH -> "The course appears to build substantial technical and digital capability";
            case LOW_TECHNICAL_DIGITAL_DEPTH -> "The course appears to build less technical or digital capability";
            case HIGH_ANALYTICAL_REASONING -> "The course appears to build strong structured analysis and problem solving";
            case LOW_ANALYTICAL_REASONING -> "The course appears to build less structured or quantitative analysis";
            case HIGH_ORIGINAL_JUDGMENT_CREATIVITY -> "The course appears to build independent judgement, synthesis or original work";
            case LOW_ORIGINAL_JUDGMENT_CREATIVITY -> "The course appears to rely more on set methods than on original work";
            case HIGH_HUMAN_RELATIONSHIP_DEPENDENCY -> "The course appears to build communication, collaboration and work with people";
            case LOW_HUMAN_RELATIONSHIP_DEPENDENCY -> "The course appears to build less interpersonal or collaborative capability";
            case HIGH_CONTEXTUAL_COMPLEXITY -> "The course appears to prepare you for ambiguous and changing situations";
            case LOW_CONTEXTUAL_COMPLEXITY -> "The course appears to work mainly in clearly structured situations";
            case HIGH_PRACTICAL_REAL_WORLD_APPLICATION -> "The course appears to have a strong applied or practical component";
            case LOW_PRACTICAL_REAL_WORLD_APPLICATION -> "The course appears to be mainly theoretical rather than applied";
            case HIGH_AI_COMPLEMENTARITY -> "There appears to be strong scope to work productively alongside AI tools";
            case LOW_AI_COMPLEMENTARITY -> "There appears to be less obvious scope for AI tools to add to this work";
            case HIGH_SKILL_TRANSFERABILITY -> "The capabilities appear to apply across a range of roles and sectors";
            case LOW_SKILL_TRANSFERABILITY -> "The capabilities appear closely tied to one particular context";
            case HIGH_CAREER_BREADTH -> "The course appears to open a broad range of plausible pathways";
            case LOW_CAREER_BREADTH -> "The course appears to point towards a narrower range of pathways";
        };
    }

    private String lower(String value) {
        return value.toLowerCase(Locale.ROOT).replace('_', ' ');
    }
}
