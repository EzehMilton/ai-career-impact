package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.decision.university.UniversityDecisionFactor;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionResult;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.decision.university.UniversityMeasureResult;
import com.chikere.futureai.evaluation.university.UniversityBaselineCases.BaselineCase;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/**
 * Stage 5B baseline capture: runs each course once through a {@link UniversityJudgmentProvider} (real Jev live, a
 * synthetic one in the dry run) and the unchanged {@link UniversityDecisionPolicyV1}, then writes the captures.
 *
 * <p>No Spring context, persistence, narrative, evidence or payment is involved: the harness holds only a judgment
 * provider and the decision policy. The README states observations; it never recommends a policy.</p>
 */
final class UniversityBaselineHarness {

    static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAXIMUM_ERROR_LENGTH = 300;
    /** A measure this far from its seven-course mean is called out as unusually high or low. */
    static final double UNUSUAL_DISTANCE = 2.0;
    /** Two measures whose values stay this close on average across the courses are called out as clustered. */
    static final double CLUSTER_MEAN_DIFFERENCE = 0.5;

    enum Mode { LIVE, DRY_RUN }

    record CaseRun(BaselineCase baselineCase, Optional<AssessmentJudgmentSet> judgments,
            Optional<UniversityDecisionResult> decision, int jevCalls, long durationMs, Optional<String> error) {
    }

    private final UniversityJudgmentProvider provider;
    private final UniversityDecisionPolicyV1 policy;

    UniversityBaselineHarness(UniversityJudgmentProvider provider, UniversityDecisionPolicyV1 policy) {
        this.provider = Objects.requireNonNull(provider);
        this.policy = Objects.requireNonNull(policy);
    }

    List<CaseRun> run(List<BaselineCase> cases) {
        List<CaseRun> runs = new ArrayList<>();
        for (BaselineCase baselineCase : cases) {
            long started = System.nanoTime();
            AssessmentJudgmentSet judgments;
            try {
                judgments = provider.judge(baselineCase.state());
            }
            catch (RuntimeException failure) {
                runs.add(new CaseRun(baselineCase, Optional.empty(), Optional.empty(), 1, millisSince(started),
                        Optional.of("provider: " + describe(failure))));
                continue;
            }
            long durationMs = millisSince(started);
            try {
                runs.add(new CaseRun(baselineCase, Optional.of(judgments), Optional.of(policy.decide(judgments)), 1,
                        durationMs, Optional.empty()));
            }
            catch (RuntimeException failure) {
                runs.add(new CaseRun(baselineCase, Optional.of(judgments), Optional.empty(), 1, durationMs,
                        Optional.of("policy: " + describe(failure))));
            }
        }
        return List.copyOf(runs);
    }

    /** Writes results.json, README.md and one JSON capture per course into {@code directory}. */
    static void write(Path directory, Mode mode, String model, Instant startedAt, List<CaseRun> runs)
            throws IOException {
        Files.createDirectories(directory);
        for (CaseRun run : runs) {
            Files.write(directory.resolve(run.baselineCase().id() + ".json"),
                    JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(capture(mode, model, startedAt, run)));
        }
        Files.write(directory.resolve("results.json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(results(mode, model, startedAt, runs)));
        Files.writeString(directory.resolve("README.md"), readme(mode, model, startedAt, runs), StandardCharsets.UTF_8);
    }

    static Map<String, Object> capture(Mode mode, String model, Instant startedAt, CaseRun run) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("caseId", run.baselineCase().id());
        out.put("mode", mode.name());
        out.put("capturedAt", startedAt.toString());
        out.put("input", input(run.baselineCase().state()));
        Map<String, Object> provider = new LinkedHashMap<>();
        provider.put("source", mode == Mode.LIVE ? "JEV" : "SYNTHETIC_DRY_RUN");
        provider.put("configuredModel", model);
        provider.put("jevCalls", run.jevCalls());
        provider.put("durationMs", run.durationMs());
        provider.put("error", run.error().orElse(null));
        provider.put("note", "Provider request ID, resolved model and token usage are not exposed by the adapter.");
        out.put("provider", provider);
        out.put("judgmentSet", run.judgments().map(UniversityBaselineHarness::judgmentSet).orElse(null));
        out.put("decision", run.decision().map(UniversityBaselineHarness::decision).orElse(null));
        return out;
    }

    private static Map<String, Object> input(UniversityAssessmentState state) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("courseTitle", state.courseTitle());
        out.put("courseDescription", state.courseDescription());
        out.put("keyModules", state.keyModules());
        out.put("skillsDeveloped", state.skillsDeveloped());
        out.put("preferredCareerAreas", state.preferredCareerAreas());
        out.put("institution", state.institution().orElse(null));
        out.put("country", state.country().orElse(null));
        return out;
    }

    /** The provider's judgments exactly as the adapter returned them. */
    private static Map<String, Object> judgmentSet(AssessmentJudgmentSet set) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("journey", set.journey().name());
        out.put("schemaVersion", set.schemaVersion());
        out.put("judgments", set.judgments().stream().map(judgment -> {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("dimensionId", judgment.dimensionId());
            value.put("name", judgment.name());
            value.put("numericValue", judgment.numericValue().isPresent() ? judgment.numericValue().getAsDouble() : null);
            value.put("categoricalValue", judgment.categoricalValue().orElse(null));
            value.put("confidence", judgment.confidence().map(Enum::name).orElse(null));
            value.put("confidenceValue",
                    judgment.confidenceValue().isPresent() ? judgment.confidenceValue().getAsDouble() : null);
            value.put("probabilityDistribution", judgment.probabilityDistribution());
            return value;
        }).toList());
        return out;
    }

    private static Map<String, Object> decision(UniversityDecisionResult decision) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("policyVersion", decision.policyVersion());
        out.put("informationSufficiency", decision.informationSufficiency());
        out.put("status", decision.assessmentDecision().status().name());
        out.put("confidence", decision.assessmentDecision().confidence().name());
        out.put("headline", decision.assessmentDecision().resultLabel());
        Map<String, Object> measures = new LinkedHashMap<>();
        decision.measures().forEach((measure, result) -> measures.put(measure.name(), measure(result)));
        out.put("measures", measures);
        out.put("topStrengths", decision.topStrengths().stream().map(UniversityBaselineHarness::factor).toList());
        out.put("topDevelopmentOpportunities",
                decision.topDevelopmentOpportunities().stream().map(UniversityBaselineHarness::factor).toList());
        return out;
    }

    private static Map<String, Object> measure(UniversityMeasureResult result) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("indicator", result.indicator());
        out.put("band", result.band().name());
        out.put("confidence", result.confidence());
        return out;
    }

    private static Map<String, Object> factor(UniversityDecisionFactor factor) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("dimensionId", factor.dimensionId());
        out.put("direction", factor.direction().name());
        out.put("score", factor.score());
        out.put("weight", factor.weight());
        out.put("signedEffect", factor.signedEffect());
        return out;
    }

    static Map<String, Object> results(Mode mode, String model, Instant startedAt, List<CaseRun> runs) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("stage", "5B university baseline capture");
        out.put("mode", mode.name());
        out.put("startedAt", startedAt.toString());
        out.put("configuredModel", model);
        out.put("schemaVersion", "university-v1");
        out.put("policyVersion", UniversityDecisionPolicyV1.POLICY_VERSION);
        out.put("totalJevCalls", runs.stream().mapToInt(CaseRun::jevCalls).sum());
        out.put("courses", runs.stream().map(run -> {
            Map<String, Object> course = new LinkedHashMap<>();
            course.put("caseId", run.baselineCase().id());
            course.put("course", run.baselineCase().state().courseTitle());
            course.put("informationSufficiency", run.decision().map(UniversityDecisionResult::informationSufficiency)
                    .orElse(null));
            course.put("confidence", run.decision().map(value -> value.assessmentDecision().confidence().name())
                    .orElse(null));
            course.put("status", run.decision().map(value -> value.assessmentDecision().status().name()).orElse(null));
            Map<String, Object> scores = new LinkedHashMap<>();
            Map<String, Object> bands = new LinkedHashMap<>();
            run.decision().ifPresent(decision -> decision.measures().forEach((measure, result) -> {
                scores.put(measure.name(), result.indicator());
                bands.put(measure.name(), result.band().name());
            }));
            course.put("measureScores", scores);
            course.put("measureBands", bands);
            course.put("jevCalls", run.jevCalls());
            course.put("durationMs", run.durationMs());
            course.put("providerError", run.error().orElse(null));
            course.put("schemaVersion", run.judgments().map(AssessmentJudgmentSet::schemaVersion).orElse(null));
            course.put("policyVersion", run.decision().map(UniversityDecisionResult::policyVersion).orElse(null));
            return course;
        }).toList());
        return out;
    }

    static String readme(Mode mode, String model, Instant startedAt, List<CaseRun> runs) {
        StringBuilder out = new StringBuilder("# University baseline capture (Stage 5B)\n\n")
                .append("- Mode: `").append(mode).append("`")
                .append(mode == Mode.DRY_RUN ? " (synthetic judgments: plumbing check only, not evidence)" : "")
                .append("\n- Started: ").append(startedAt)
                .append("\n- Configured Jev model: `").append(model).append("`")
                .append("\n- Judgment schema: `university-v1`; decision policy: `")
                .append(UniversityDecisionPolicyV1.POLICY_VERSION).append("` (unchanged)")
                .append("\n- Jev calls: ").append(runs.stream().mapToInt(CaseRun::jevCalls).sum())
                .append("\n- OpenAI, Stripe, persistence, narrative and evidence: not used\n\n")
                .append("This file reports observations only. It makes no policy recommendation.\n\n")
                .append("## Measures\n\n| Course | Information | Status | Confidence | Future Readiness | "
                        + "AI Complementarity | Human Differentiation | Skill Transferability | Career Adaptability "
                        + "| Jev ms |\n|---|---:|---|---|---|---|---|---|---|---:|\n");
        for (CaseRun run : runs) {
            out.append("| ").append(run.baselineCase().state().courseTitle()).append(" | ");
            if (run.decision().isEmpty()) {
                out.append("— | ERROR | — | — | — | — | — | — | ").append(run.durationMs()).append(" |\n");
                continue;
            }
            UniversityDecisionResult decision = run.decision().orElseThrow();
            out.append(format(decision.informationSufficiency())).append(" | ")
                    .append(decision.assessmentDecision().status()).append(" | ")
                    .append(decision.assessmentDecision().confidence()).append(" | ");
            for (UniversityMeasure measure : UniversityMeasure.values()) {
                out.append(decision.measure(measure).map(value -> format(value.indicator()) + " "
                        + value.band().name()).orElse("—")).append(" | ");
            }
            out.append(run.durationMs()).append(" |\n");
        }

        out.append("\n## Raw judgments (score 0–4, confidence in brackets)\n\n| Course | Information |");
        List<String> dimensions = UniversityDecisionPolicyV1.SCORE_DIMENSIONS;
        dimensions.forEach(dimension -> out.append(' ').append(dimension).append(" |"));
        out.append("\n|---|---:|").append("---:|".repeat(dimensions.size())).append('\n');
        for (CaseRun run : runs) {
            out.append("| ").append(run.baselineCase().state().courseTitle()).append(" | ");
            if (run.judgments().isEmpty()) {
                out.append("— |").append(" — |".repeat(dimensions.size())).append('\n');
                continue;
            }
            Map<String, DimensionJudgment> byId = new LinkedHashMap<>();
            run.judgments().orElseThrow().judgments().forEach(judgment -> byId.put(judgment.dimensionId(), judgment));
            out.append(value(byId.get("information_sufficient"))).append(" |");
            dimensions.forEach(dimension -> out.append(' ').append(value(byId.get(dimension))).append(" |"));
            out.append('\n');
        }

        List<CaseRun> scored = runs.stream().filter(run -> run.decision().isPresent()
                && !run.decision().orElseThrow().measures().isEmpty()).toList();
        out.append("\n## Observations\n\n")
                .append("- Errors: ").append(names(runs.stream().filter(run -> run.error().isPresent()).toList()))
                .append("\n- More information required: ").append(names(runs.stream().filter(run -> run.decision()
                        .filter(value -> value.assessmentDecision().status().name()
                                .equals("MORE_INFORMATION_REQUIRED")).isPresent()).toList()))
                .append("\n- Ready with caution: ").append(names(runs.stream().filter(run -> run.decision()
                        .filter(value -> value.assessmentDecision().status().name().equals("READY_WITH_CAUTION"))
                        .isPresent()).toList()))
                .append('\n');
        if (scored.size() >= 2) {
            out.append("\n### Highest and lowest per measure\n\n| Measure | Mean | Highest | Lowest |\n|---|---:|---|---|\n");
            for (UniversityMeasure measure : UniversityMeasure.values()) {
                Function<CaseRun, Double> of = run -> run.decision().orElseThrow().measure(measure).orElseThrow()
                        .indicator();
                CaseRun high = scored.stream().max((a, b) -> Double.compare(of.apply(a), of.apply(b))).orElseThrow();
                CaseRun low = scored.stream().min((a, b) -> Double.compare(of.apply(a), of.apply(b))).orElseThrow();
                out.append("| ").append(measure).append(" | ").append(format(mean(scored, of))).append(" | ")
                        .append(high.baselineCase().id()).append(" (").append(format(of.apply(high))).append(") | ")
                        .append(low.baselineCase().id()).append(" (").append(format(of.apply(low))).append(") |\n");
            }
            out.append("\n### Unusually high or low (at least ").append(format(UNUSUAL_DISTANCE))
                    .append(" from the measure's mean across these courses)\n\n");
            List<String> unusual = new ArrayList<>();
            for (UniversityMeasure measure : UniversityMeasure.values()) {
                Function<CaseRun, Double> of = run -> run.decision().orElseThrow().measure(measure).orElseThrow()
                        .indicator();
                double mean = mean(scored, of);
                scored.stream().filter(run -> Math.abs(of.apply(run) - mean) >= UNUSUAL_DISTANCE).forEach(run ->
                        unusual.add("- " + run.baselineCase().id() + ": " + measure + " " + format(of.apply(run))
                                + " (mean " + format(mean) + ")"));
            }
            out.append(unusual.isEmpty() ? "- none\n" : String.join("\n", unusual) + "\n");
            out.append("\n### Measure clustering\n\nMean absolute difference between two measures across the scored "
                    + "courses, and their Pearson correlation. Pairs closer than ")
                    .append(format(CLUSTER_MEAN_DIFFERENCE))
                    .append(" on average are marked.\n\n| Measures | Mean abs difference | Correlation | Close |\n"
                            + "|---|---:|---:|---|\n");
            UniversityMeasure[] measures = UniversityMeasure.values();
            for (int first = 0; first < measures.length; first++) {
                for (int second = first + 1; second < measures.length; second++) {
                    UniversityMeasure a = measures[first];
                    UniversityMeasure b = measures[second];
                    double[] x = scored.stream().mapToDouble(run -> indicator(run, a)).toArray();
                    double[] y = scored.stream().mapToDouble(run -> indicator(run, b)).toArray();
                    double difference = 0;
                    for (int index = 0; index < x.length; index++) difference += Math.abs(x[index] - y[index]);
                    difference /= x.length;
                    out.append("| ").append(a).append(" / ").append(b).append(" | ").append(format(difference))
                            .append(" | ").append(correlation(x, y)).append(" | ")
                            .append(difference < CLUSTER_MEAN_DIFFERENCE ? "yes" : "").append(" |\n");
                }
            }
        }
        return out.toString();
    }

    private static double indicator(CaseRun run, UniversityMeasure measure) {
        return run.decision().orElseThrow().measure(measure).orElseThrow().indicator();
    }

    private static double mean(List<CaseRun> runs, Function<CaseRun, Double> of) {
        return runs.stream().mapToDouble(of::apply).average().orElse(Double.NaN);
    }

    private static String correlation(double[] x, double[] y) {
        double meanX = java.util.Arrays.stream(x).average().orElseThrow();
        double meanY = java.util.Arrays.stream(y).average().orElseThrow();
        double covariance = 0, varianceX = 0, varianceY = 0;
        for (int index = 0; index < x.length; index++) {
            covariance += (x[index] - meanX) * (y[index] - meanY);
            varianceX += (x[index] - meanX) * (x[index] - meanX);
            varianceY += (y[index] - meanY) * (y[index] - meanY);
        }
        return varianceX == 0 || varianceY == 0 ? "—" : format(covariance / Math.sqrt(varianceX * varianceY));
    }

    private static String names(List<CaseRun> runs) {
        return runs.isEmpty() ? "none" : String.join(", ", runs.stream().map(run -> run.baselineCase().id()).toList());
    }

    private static String value(DimensionJudgment judgment) {
        if (judgment == null || judgment.numericValue().isEmpty()) return "—";
        String value = format(judgment.numericValue().getAsDouble());
        return judgment.confidenceValue().isPresent()
                ? value + " (" + format(judgment.confidenceValue().getAsDouble()) + ")" : value;
    }

    static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private static long millisSince(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000;
    }

    /** The failure type and a bounded message; the provider never receives or returns credentials. */
    private static String describe(RuntimeException failure) {
        String message = Optional.ofNullable(failure.getMessage()).orElse("");
        if (message.length() > MAXIMUM_ERROR_LENGTH) message = message.substring(0, MAXIMUM_ERROR_LENGTH) + "…";
        return failure.getClass().getSimpleName() + (message.isEmpty() ? "" : ": " + message);
    }
}
