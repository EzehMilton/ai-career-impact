package com.chikere.futureai.evaluation.professional;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.professional.IndicatorBand;
import com.chikere.futureai.decision.professional.ProfessionalDecisionFactor;
import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.decision.professional.ProfessionalMeasureResult;
import com.chikere.futureai.judgment.domain.DimensionJudgment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;

final class ProfessionalEvaluationReportWriter {

    private static final String INFORMATION_SUFFICIENT = "information_sufficient";
    private static final List<String> SCORE_DIMENSIONS = List.of(
            "task_repeatability",
            "digital_executability",
            "human_trust_dependency",
            "physical_world_dependency",
            "contextual_complexity",
            "original_judgment",
            "accountability_responsibility",
            "ai_complementarity");

    private static final double LOW_CONFIDENCE_WARNING_THRESHOLD = 0.50;
    private static final double SIMILAR_VECTOR_WARNING_THRESHOLD = 0.15;
    private static final double BAND_BOUNDARY_WARNING_DISTANCE = 0.15;

    private ProfessionalEvaluationReportWriter() {
    }

    static void write(List<ProfessionalEvaluationResult> results, Path reportPath) {
        try {
            Files.createDirectories(reportPath.getParent());
            Files.writeString(reportPath, render(results), StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not write Jev evaluation report to " + reportPath, exception);
        }
    }

    static String render(List<ProfessionalEvaluationResult> results) {
        if (results.isEmpty()) {
            throw new IllegalArgumentException("results must not be empty");
        }
        StringBuilder report = new StringBuilder();
        report.append("# Professional Jev evaluation: professional-v1\n\n")
                .append("> Live evaluation output. These observations are descriptive, not accuracy claims.\n\n")
                .append("- Cases evaluated: ").append(results.size()).append("\n")
                .append("- Judgment schema: `professional-v1`\n")
                .append("- Decision policy: `professional-decision-v1`\n")
                .append("- Jev provenance: resolved model, request ID, and usage are not exposed by the current ")
                .append("vendor-neutral judgment adapter.\n\n");

        results.forEach(result -> appendCase(report, result));
        appendComparisons(report, results);
        appendWarnings(report, results);
        appendMetrics(report, results);
        return report.toString();
    }

    private static void appendCase(StringBuilder report, ProfessionalEvaluationResult result) {
        ProfessionalEvaluationCase evaluationCase = result.evaluationCase();
        Map<String, DimensionJudgment> judgments = byId(result);
        DimensionJudgment information = required(judgments, INFORMATION_SUFFICIENT);

        report.append("## ").append(escape(evaluationCase.profession()))
                .append(" (`").append(evaluationCase.id()).append("`)\n\n")
                .append("**Purpose:** ").append(escape(evaluationCase.rationale())).append("\n\n")
                .append("**Information sufficiency:** ")
                .append(format(requiredNumeric(information))).append("\n\n")
                .append("| Atomic judgment | Value (0–4) | Nearest label | Confidence | Probability distribution |\n")
                .append("|---|---:|---|---:|---|\n");

        SCORE_DIMENSIONS.forEach(dimensionId -> {
            DimensionJudgment judgment = required(judgments, dimensionId);
            report.append("| ").append(escape(judgment.name()))
                    .append(" (`").append(dimensionId).append("`) | ")
                    .append(format(requiredNumeric(judgment))).append(" | ")
                    .append(escape(judgment.categoricalValue().orElse("—"))).append(" | ")
                    .append(format(requiredConfidence(judgment))).append(" | ")
                    .append(formatDistribution(judgment.probabilityDistribution())).append(" |\n");
        });

        report.append("\n### Professional decision\n\n");
        if (result.decision().measures().isEmpty()) {
            report.append("No authoritative 0–10 measures were produced because more information is required.\n\n");
        }
        else {
            report.append("| Measure | Indicator (0–10) | Band | Confidence |\n")
                    .append("|---|---:|---|---:|\n");
            for (ProfessionalMeasure measure : ProfessionalMeasure.values()) {
                ProfessionalMeasureResult measureResult = result.decision().measure(measure).orElseThrow();
                report.append("| ").append(displayName(measure)).append(" | ")
                        .append(format(measureResult.indicator())).append(" | ")
                        .append(measureResult.band()).append(" | ")
                        .append(format(measureResult.confidence())).append(" |\n");
            }
            report.append("\n");
        }

        report.append("**Assessment confidence:** ")
                .append(result.decision().assessmentDecision().confidence()).append("  \n")
                .append("**Assessment status:** ")
                .append(result.decision().assessmentDecision().status()).append("\n\n")
                .append("### Explainability\n\n")
                .append("**Top exposure drivers:** ")
                .append(formatFactors(result.decision().topExposureDrivers())).append("  \n")
                .append("**Top protective factors:** ")
                .append(formatFactors(result.decision().topProtectiveFactors())).append("\n\n");
    }

    private static void appendComparisons(
            StringBuilder report,
            List<ProfessionalEvaluationResult> results) {
        report.append("## Context sensitivity comparisons\n\n")
                .append("Differences show the second case minus the first. They are observations, not correctness labels.\n\n");
        Map<String, ProfessionalEvaluationResult> byCaseId = new LinkedHashMap<>();
        results.forEach(result -> byCaseId.put(result.evaluationCase().id(), result));

        ProfessionalEvaluationDataset.COMPARISON_PAIRS.forEach(pair -> {
            report.append("### `").append(pair.firstId()).append("` vs `")
                    .append(pair.secondId()).append("`\n\n");
            ProfessionalEvaluationResult first = byCaseId.get(pair.firstId());
            ProfessionalEvaluationResult second = byCaseId.get(pair.secondId());
            if (first == null || second == null) {
                report.append("Comparison not available because this run did not include both cases.\n\n");
                return;
            }
            report.append("| Measure | ").append(pair.firstId()).append(" | ")
                    .append(pair.secondId()).append(" | Difference |\n")
                    .append("|---|---:|---:|---:|\n");
            appendDifference(report, "Information sufficiency",
                    informationSufficiency(first), informationSufficiency(second));
            SCORE_DIMENSIONS.forEach(dimensionId -> appendDifference(
                    report,
                    dimensionId,
                    judgmentValue(first, dimensionId),
                    judgmentValue(second, dimensionId)));
            appendDecisionDifference(report, "AI Exposure", ProfessionalMeasure.AI_EXPOSURE, first, second);
            appendDecisionDifference(report, "Human Advantage", ProfessionalMeasure.HUMAN_ADVANTAGE, first, second);
            appendDecisionDifference(report, "Role Adaptability", ProfessionalMeasure.ROLE_ADAPTABILITY, first, second);
            report.append("\n");
        });
    }

    private static void appendWarnings(
            StringBuilder report,
            List<ProfessionalEvaluationResult> results) {
        List<String> warnings = new ArrayList<>();
        results.forEach(result -> {
            if (result.evaluationCase().id().equals("insufficient_information")
                    && informationSufficiency(result) >= 0.60) {
                warnings.add("`insufficient_information` returned information sufficiency "
                        + format(informationSufficiency(result)) + " (at or above 0.60).");
            }
            result.judgments().judgments().stream()
                    .filter(judgment -> SCORE_DIMENSIONS.contains(judgment.dimensionId()))
                    .filter(judgment -> requiredConfidence(judgment) < LOW_CONFIDENCE_WARNING_THRESHOLD)
                    .forEach(judgment -> warnings.add("`" + result.evaluationCase().id() + "` / `"
                            + judgment.dimensionId() + "` has low Score confidence "
                            + format(requiredConfidence(judgment)) + "."));
            result.decision().measures().values().stream()
                    .filter(measure -> nearBandBoundary(measure.indicator()))
                    .forEach(measure -> warnings.add("`" + result.evaluationCase().id() + "` / `"
                            + measure.measure() + "` is close to a band threshold at "
                            + format(measure.indicator()) + "."));
        });

        Map<String, ProfessionalEvaluationResult> byCaseId = new LinkedHashMap<>();
        results.forEach(result -> byCaseId.put(result.evaluationCase().id(), result));
        ProfessionalEvaluationDataset.COMPARISON_PAIRS.forEach(pair -> {
            ProfessionalEvaluationResult first = byCaseId.get(pair.firstId());
            ProfessionalEvaluationResult second = byCaseId.get(pair.secondId());
            if (first != null && second != null) {
                double meanDifference = SCORE_DIMENSIONS.stream()
                        .mapToDouble(id -> Math.abs(judgmentValue(first, id) - judgmentValue(second, id)))
                        .average()
                        .orElseThrow();
                if (meanDifference < SIMILAR_VECTOR_WARNING_THRESHOLD) {
                    warnings.add("Comparison `" + pair.firstId() + "` vs `" + pair.secondId()
                            + "` produced almost identical Score vectors (mean absolute difference "
                            + format(meanDifference) + ").");
                }
            }
        });

        report.append("## Warnings\n\n");
        if (warnings.isEmpty()) {
            report.append("No configured evaluation hypotheses were triggered.\n\n");
        }
        else {
            warnings.forEach(warning -> report.append("- ").append(warning).append("\n"));
            report.append("\n");
        }
    }

    private static void appendMetrics(
            StringBuilder report,
            List<ProfessionalEvaluationResult> results) {
        double averageInformation = results.stream()
                .mapToDouble(ProfessionalEvaluationReportWriter::informationSufficiency)
                .average()
                .orElseThrow();
        List<Double> scoreConfidences = results.stream()
                .flatMap(result -> result.judgments().judgments().stream())
                .filter(judgment -> SCORE_DIMENSIONS.contains(judgment.dimensionId()))
                .map(judgment -> requiredConfidence(judgment))
                .toList();
        double averageConfidence = scoreConfidences.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
        double lowestConfidence = scoreConfidences.stream().mapToDouble(Double::doubleValue).min().orElseThrow();

        EnumMap<AssessmentStatus, Long> statuses = new EnumMap<>(AssessmentStatus.class);
        for (AssessmentStatus status : AssessmentStatus.values()) {
            statuses.put(status, results.stream()
                    .filter(result -> result.decision().assessmentDecision().status() == status)
                    .count());
        }

        report.append("## Descriptive metrics\n\n")
                .append("- Average information sufficiency: ").append(format(averageInformation)).append("\n")
                .append("- Average Score confidence: ").append(format(averageConfidence)).append("\n")
                .append("- Lowest Score confidence: ").append(format(lowestConfidence)).append("\n")
                .append("- READY cases: ").append(statuses.get(AssessmentStatus.READY)).append("\n")
                .append("- READY_WITH_CAUTION cases: ")
                .append(statuses.get(AssessmentStatus.READY_WITH_CAUTION)).append("\n")
                .append("- MORE_INFORMATION_REQUIRED cases: ")
                .append(statuses.get(AssessmentStatus.MORE_INFORMATION_REQUIRED)).append("\n\n")
                .append("### Indicator-band distributions\n\n");

        for (ProfessionalMeasure measure : ProfessionalMeasure.values()) {
            EnumMap<IndicatorBand, Long> bands = new EnumMap<>(IndicatorBand.class);
            for (IndicatorBand band : IndicatorBand.values()) {
                bands.put(band, results.stream()
                        .map(result -> result.decision().measure(measure))
                        .flatMap(java.util.Optional::stream)
                        .filter(result -> result.band() == band)
                        .count());
            }
            report.append("- ").append(displayName(measure)).append(": LOW=")
                    .append(bands.get(IndicatorBand.LOW)).append(", MODERATE=")
                    .append(bands.get(IndicatorBand.MODERATE)).append(", HIGH=")
                    .append(bands.get(IndicatorBand.HIGH)).append("\n");
        }
    }

    private static Map<String, DimensionJudgment> byId(ProfessionalEvaluationResult result) {
        LinkedHashMap<String, DimensionJudgment> byId = new LinkedHashMap<>();
        result.judgments().judgments().forEach(judgment -> {
            if (byId.put(judgment.dimensionId(), judgment) != null) {
                throw new IllegalStateException("Duplicate judgment in evaluation result: " + judgment.dimensionId());
            }
        });
        return byId;
    }

    private static DimensionJudgment required(Map<String, DimensionJudgment> judgments, String dimensionId) {
        DimensionJudgment judgment = judgments.get(dimensionId);
        if (judgment == null) {
            throw new IllegalStateException("Missing required evaluation judgment: " + dimensionId);
        }
        return judgment;
    }

    private static double requiredNumeric(DimensionJudgment judgment) {
        return judgment.numericValue().orElseThrow(() ->
                new IllegalStateException("Missing numeric value for " + judgment.dimensionId()));
    }

    private static double requiredConfidence(DimensionJudgment judgment) {
        return judgment.confidenceValue().orElseThrow(() ->
                new IllegalStateException("Missing confidence value for " + judgment.dimensionId()));
    }

    private static double informationSufficiency(ProfessionalEvaluationResult result) {
        return judgmentValue(result, INFORMATION_SUFFICIENT);
    }

    private static double judgmentValue(ProfessionalEvaluationResult result, String dimensionId) {
        return requiredNumeric(required(byId(result), dimensionId));
    }

    private static void appendDecisionDifference(
            StringBuilder report,
            String label,
            ProfessionalMeasure measure,
            ProfessionalEvaluationResult first,
            ProfessionalEvaluationResult second) {
        OptionalDouble firstValue = indicator(first.decision(), measure);
        OptionalDouble secondValue = indicator(second.decision(), measure);
        if (firstValue.isEmpty() || secondValue.isEmpty()) {
            report.append("| ").append(label).append(" | — | — | — |\n");
            return;
        }
        appendDifference(report, label, firstValue.getAsDouble(), secondValue.getAsDouble());
    }

    private static OptionalDouble indicator(ProfessionalDecisionResult decision, ProfessionalMeasure measure) {
        return decision.measure(measure)
                .map(result -> OptionalDouble.of(result.indicator()))
                .orElseGet(OptionalDouble::empty);
    }

    private static void appendDifference(StringBuilder report, String label, double first, double second) {
        report.append("| ").append(escape(label)).append(" | ")
                .append(format(first)).append(" | ")
                .append(format(second)).append(" | ")
                .append(formatSigned(second - first)).append(" |\n");
    }

    private static boolean nearBandBoundary(double indicator) {
        return Math.abs(indicator - 3.5) <= BAND_BOUNDARY_WARNING_DISTANCE
                || Math.abs(indicator - 7.0) <= BAND_BOUNDARY_WARNING_DISTANCE;
    }

    private static String formatDistribution(Map<String, Double> distribution) {
        if (distribution.isEmpty()) {
            return "—";
        }
        return distribution.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + format(entry.getValue()))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String formatFactors(List<ProfessionalDecisionFactor> factors) {
        if (factors.isEmpty()) {
            return "None";
        }
        return factors.stream()
                .map(factor -> "`" + factor.direction() + "` (signed effect "
                        + formatSigned(factor.signedEffect()) + ")")
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private static String displayName(ProfessionalMeasure measure) {
        return switch (measure) {
            case AI_EXPOSURE -> "AI Exposure";
            case HUMAN_ADVANTAGE -> "Human Advantage";
            case AI_COMPLEMENTARITY -> "AI Complementarity";
            case ROLE_ADAPTABILITY -> "Role Adaptability";
        };
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private static String formatSigned(double value) {
        return String.format(Locale.ROOT, "%+.3f", value);
    }

    private static String escape(String text) {
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
