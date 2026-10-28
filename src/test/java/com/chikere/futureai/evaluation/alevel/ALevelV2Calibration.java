package com.chikere.futureai.evaluation.alevel;

import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.evaluation.alevel.ALevelV2CandidatePolicy.Decision;
import com.chikere.futureai.evaluation.alevel.ALevelV2CandidatePolicy.Judgment;
import com.chikere.futureai.evaluation.alevel.ALevelV2CandidatePolicy.Measure;
import com.chikere.futureai.evaluation.alevel.ALevelV2CandidatePolicy.MeasureResult;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * Stage A1/A2: runs the candidate {@code a-level-decision-v2} over the seven calibration combinations, with no
 * provider call. The judgments are loaded from a file whose {@code source} is printed on every report, so the current
 * {@code OFFLINE_CALIBRATION} values are never mistaken for Jev evidence. Replacing the file with live
 * {@code a-level-v2} captures (source {@code JEV}) recalibrates without code changes.
 */
final class ALevelV2Calibration {

    static final Path OFFLINE_JUDGMENTS = Path.of("src", "test", "resources", "a-level", "calibration-v2",
            "offline-calibration.json");

    record Judged(String source, String schemaVersion, Map<String, CaseJudgments> cases) {
    }

    record CaseJudgments(String origin, double information, Map<String, Judgment> judgments) {
    }

    record Row(ALevelV2CalibrationCases.CalibrationCase calibrationCase, CaseJudgments judgments, Decision decision,
            double rejectedOptionsWithBridge, Map<ALevelMeasure, Double> v1Indicators) {
    }

    private final ALevelV2CandidatePolicy policy = new ALevelV2CandidatePolicy();

    static Judged load(Path file) throws IOException {
        JsonNode root = new ObjectMapper().readTree(file.toFile());
        Map<String, CaseJudgments> cases = new LinkedHashMap<>();
        for (JsonNode node : root.get("cases")) {
            Map<String, Judgment> judgments = new LinkedHashMap<>();
            node.get("judgments").properties().forEach(entry -> judgments.put(entry.getKey(),
                    new Judgment(entry.getValue().get("score").asDouble(), entry.getValue().get("confidence").asDouble())));
            cases.put(node.get("id").asText(), new CaseJudgments(node.get("origin").asText(),
                    node.get("informationSufficiency").asDouble(), judgments));
        }
        return new Judged(root.get("source").asText(), root.get("schemaVersion").asText(), cases);
    }

    List<Row> run(Judged judged) {
        List<Row> rows = new ArrayList<>();
        for (var calibrationCase : ALevelV2CalibrationCases.ALL) {
            CaseJudgments judgments = judged.cases().get(calibrationCase.id());
            if (judgments == null) {
                throw new IllegalStateException("no judgments for calibration case " + calibrationCase.id());
            }
            Decision decision = policy.decide(judgments.information(), judgments.judgments());
            Map<ALevelMeasure, Double> v1 = new LinkedHashMap<>();
            calibrationCase.v1FixtureId().ifPresent(fixture -> {
                var result = new ALevelDecisionPolicyV1().decide(new LocalFixtureALevelJudgmentProvider(fixture)
                        .judge(calibrationCase.state()));
                result.measures().forEach((measure, value) -> v1.put(measure, value.indicator()));
            });
            rows.add(new Row(calibrationCase, judgments, decision, optionsWithBridge(judgments.judgments()), v1));
        }
        return rows;
    }

    /**
     * Rejected candidate B for Options Kept Open, kept only as a labelled comparison: 0.45 family coverage, 0.30
     * diversity and 0.25 of the weaker capability side. It correlated +0.97 with the approved two-primitive formula,
     * only compressed scores, and made breadth depend on a non-linear derived term.
     */
    static double optionsWithBridge(Map<String, Judgment> judgments) {
        double analytical = side(Measure.ANALYTICAL_AND_QUANTITATIVE, judgments);
        double communication = side(Measure.COMMUNICATION_INTERPRETATION_AND_CONTEXT, judgments);
        return 10.0 * (0.45 * judgments.get(ALevelV2CandidateSchema.DISCIPLINE_FAMILY_COVERAGE).score() / 4.0
                + 0.30 * judgments.get(ALevelV2CandidateSchema.SUBJECT_SKILL_DIVERSITY).score() / 4.0
                + 0.25 * Math.min(analytical, communication));
    }

    private static double side(Measure measure, Map<String, Judgment> judgments) {
        return measure.weights.entrySet().stream()
                .mapToDouble(weight -> weight.getValue() * judgments.get(weight.getKey()).score() / 4.0).sum();
    }

    static double pearson(List<Row> rows, ToDoubleFunction<Row> first, ToDoubleFunction<Row> second) {
        double[] x = rows.stream().mapToDouble(first).toArray();
        double[] y = rows.stream().mapToDouble(second).toArray();
        double meanX = java.util.Arrays.stream(x).average().orElseThrow();
        double meanY = java.util.Arrays.stream(y).average().orElseThrow();
        double covariance = 0.0;
        double varianceX = 0.0;
        double varianceY = 0.0;
        for (int index = 0; index < x.length; index++) {
            covariance += (x[index] - meanX) * (y[index] - meanY);
            varianceX += (x[index] - meanX) * (x[index] - meanX);
            varianceY += (y[index] - meanY) * (y[index] - meanY);
        }
        return covariance / Math.sqrt(varianceX * varianceY);
    }

    static ToDoubleFunction<Row> indicator(Measure measure) {
        return row -> row.decision().measures().get(measure).indicator();
    }

    String report(Judged judged, List<Row> rows) {
        StringBuilder out = new StringBuilder();
        out.append("# A-level decision v2 calibration\n\n")
                .append("**Judgment source: ").append(judged.source()).append("** (schema ")
                .append(judged.schemaVersion()).append(")\n\n");
        if ("OFFLINE_CALIBRATION".equals(judged.source())) {
            out.append("> These judgments are synthetic calibration data, not Jev output and not evidence about any "
                    + "subject. The seven-case live capture is a release gate.\n\n");
        }
        out.append("Policy `").append(ALevelV2CandidatePolicy.POLICY_VERSION).append("`. No overall score.\n\n");
        out.append("| Combination | Status | Options Kept Open | Analytical & Quantitative | Communication, "
                + "Interpretation & Context | Learning Alongside AI |\n|---|---|---|---|---|---|\n");
        for (Row row : rows) {
            out.append("| ").append(String.join(" + ", row.calibrationCase().state().selectedSubjects())).append(" | ")
                    .append(row.decision().status());
            for (Measure measure : Measure.values()) {
                MeasureResult result = row.decision().measures().get(measure);
                out.append(" | ").append(result.shownScore()).append(' ').append(result.bandLabel())
                        .append(result.lowerConfidence() ? " (lower confidence)" : "");
            }
            out.append(" |\n");
        }
        out.append("\n## Drivers and limits\n\n");
        for (Row row : rows) {
            out.append("**").append(String.join(" + ", row.calibrationCase().state().selectedSubjects()))
                    .append("**\n\n");
            for (Measure measure : Measure.values()) {
                MeasureResult result = row.decision().measures().get(measure);
                out.append("- ").append(measure.displayName).append(": strengthened by ")
                        .append(phrases(measure, result.drivers(), true)).append("; held back by ")
                        .append(measure.showsLimits ? phrases(measure, result.limits(), false) : "(never explained)")
                        .append('\n');
            }
            out.append('\n');
        }
        out.append("## Correlations between scored dimensions (n = ").append(rows.size()).append(")\n\n");
        Map<String, ToDoubleFunction<Row>> series = new LinkedHashMap<>();
        for (Measure measure : Measure.values()) {
            series.put(measure.displayName, indicator(measure));
        }
        series.put("Rejected candidate B: Options Kept Open with bridge", Row::rejectedOptionsWithBridge);
        List<String> names = List.copyOf(series.keySet());
        for (int first = 0; first < names.size(); first++) {
            for (int second = first + 1; second < names.size(); second++) {
                out.append("- ").append(names.get(first)).append(" ~ ").append(names.get(second)).append(": ")
                        .append(format(pearson(rows, series.get(names.get(first)), series.get(names.get(second)))))
                        .append('\n');
            }
        }
        out.append("\n## v1 baseline (a-level-decision-v1 over the v1 local development fixtures)\n\n")
                .append("| Combination | Future Optionality | Degree Pathway Breadth | Analytical Foundation | Human and "
                        + "Creative Strength | Skill Transferability | AI Era Adaptability |\n|---|---|---|---|---|---|---|\n");
        for (Row row : rows) {
            if (row.v1Indicators().isEmpty()) continue;
            out.append("| ").append(String.join(" + ", row.calibrationCase().state().selectedSubjects()));
            for (ALevelMeasure measure : ALevelMeasure.values()) {
                out.append(" | ").append(format(row.v1Indicators().get(measure)));
            }
            out.append(" |\n");
        }
        return out.toString();
    }

    private static String phrases(Measure measure, List<ALevelV2CandidatePolicy.Factor> factors, boolean driver) {
        if (factors.isEmpty()) return "(none material)";
        return String.join("; ", factors.stream().map(factor -> ALevelV2CandidatePolicy.phrase(measure, factor, driver))
                .toList());
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
