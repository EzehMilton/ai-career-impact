package com.chikere.futureai.evaluation.professional;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalEvaluationReportWriterTest {

    private static final List<String> SCORE_DIMENSIONS = List.of(
            "task_repeatability",
            "digital_executability",
            "human_trust_dependency",
            "physical_world_dependency",
            "contextual_complexity",
            "original_judgment",
            "accountability_responsibility",
            "ai_complementarity");

    @Test
    void rendersJudgmentsDecisionsWarningsComparisonsAndMetricsOffline() {
        ProfessionalEvaluationCase evaluationCase = new ProfessionalEvaluationCase(
                "routine_software_developer",
                "Software Developer",
                "Implements well-defined tickets",
                List.of("Implement CRUD APIs"),
                List.of("Java"),
                3,
                "Probes routine digital work.");
        AssessmentJudgmentSet judgments = judgments();
        ProfessionalDecisionResult decision = new ProfessionalDecisionPolicyV1().decide(judgments);

        String report = ProfessionalEvaluationReportWriter.render(List.of(
                new ProfessionalEvaluationResult(evaluationCase, judgments, decision)));

        assertThat(report).contains(
                "# Professional Jev evaluation: professional-v1",
                "`routine_software_developer`",
                "Information sufficiency",
                "Task repeatability",
                "AI Exposure",
                "Human Advantage",
                "AI Complementarity",
                "Role Adaptability",
                "## Context sensitivity comparisons",
                "Comparison not available because this run did not include both cases.",
                "## Warnings",
                "## Descriptive metrics",
                "Average Score confidence");
    }

    private AssessmentJudgmentSet judgments() {
        ArrayList<DimensionJudgment> judgments = new ArrayList<>();
        judgments.add(new DimensionJudgment(
                "information_sufficient",
                "Information sufficiency",
                OptionalDouble.of(0.90),
                Optional.empty(),
                Optional.empty(),
                OptionalDouble.empty(),
                Map.of()));
        SCORE_DIMENSIONS.forEach(id -> judgments.add(new DimensionJudgment(
                id,
                displayName(id),
                OptionalDouble.of(2.0),
                Optional.of("Middle level"),
                Optional.empty(),
                OptionalDouble.of(0.90),
                Map.of("0", 0.10, "1", 0.20, "2", 0.40, "3", 0.20, "4", 0.10))));
        return new AssessmentJudgmentSet(JourneyType.PROFESSIONAL, "professional-v1", judgments);
    }

    private String displayName(String dimensionId) {
        return switch (dimensionId) {
            case "task_repeatability" -> "Task repeatability";
            case "digital_executability" -> "Digital executability";
            case "human_trust_dependency" -> "Human trust dependency";
            case "physical_world_dependency" -> "Physical-world dependency";
            case "contextual_complexity" -> "Contextual complexity";
            case "original_judgment" -> "Original judgment";
            case "accountability_responsibility" -> "Accountability and responsibility";
            case "ai_complementarity" -> "AI complementarity";
            default -> throw new IllegalArgumentException("Unknown dimension: " + dimensionId);
        };
    }
}
