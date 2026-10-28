package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Calibration of the approved {@code university-decision-v2} measures over the frozen Stage 5B captures, with no
 * provider call. {@link #APPROVED} mirrors {@link com.chikere.futureai.decision.university.UniversityDecisionPolicyV2}
 * (Working Alongside AI is candidate B), and a test keeps the two identical. The rejected Stage 5B.2 candidate A is
 * kept only as a labelled historical comparison.
 *
 * <p>Contextual complexity appears in all three approved measures on purpose: judgement when checking AI output in
 * context, judgement in complex situations, and capability that carries across varying contexts.</p>
 */
final class UniversityV2Calibration {

    /** Where the frozen Stage 5B live captures live once copied out of .live-smoke. */
    static final Path FIXTURES = Path.of("src", "test", "resources", "university", "baseline-5b");

    static final double LOW_BELOW = 3.5;
    static final double HIGH_FROM = 7.0;
    /** A factor is material when it moves its measure by at least this much on the 0–10 scale. */
    static final double MATERIALITY = 0.25;

    record Candidate(String id, String name, String meaning, Map<String, Double> weights) {
        Candidate {
            double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
            if (Math.abs(total - 1.0) > 1.0e-9) throw new IllegalArgumentException(id + " weights must sum to 1");
            if (!UniversityDecisionPolicyV1.SCORE_DIMENSIONS.containsAll(weights.keySet())) {
                throw new IllegalArgumentException(id + " uses an unknown dimension");
            }
        }
    }

    /** The approved v2 formulas: candidate B for Working Alongside AI. */
    static final List<Candidate> APPROVED = List.of(
            new Candidate("WORKING_ALONGSIDE_AI", "Working Alongside AI",
                    "How much the course appears to prepare you to use AI productively alongside your own expertise, "
                            + "including the grounding to check and interpret what AI produces.",
                    weights("ai_complementarity", 0.55, "technical_digital_depth", 0.15, "analytical_reasoning", 0.15,
                            "contextual_complexity", 0.15)),
            new Candidate("HUMAN_AND_PRACTICAL_CAPABILITY", "Human and Practical Capability",
                    "How much the course appears to develop work with people, judgement in complex situations, "
                            + "original thinking and hands-on practice.",
                    weights("human_relationship_dependency", 0.30, "practical_real_world_application", 0.25,
                            "contextual_complexity", 0.25, "original_judgment_creativity", 0.20)),
            new Candidate("TRANSFERABLE_CAPABILITY", "Transferable Capability",
                    "How widely the capabilities the course appears to develop apply beyond one field, and how broad "
                            + "a range of plausible pathways it appears to open. Not a prediction of employment.",
                    weights("skill_transferability", 0.40, "career_breadth", 0.35, "analytical_reasoning", 0.15,
                            "contextual_complexity", 0.10)));

    /** Historical comparison only: Stage 5B.2 candidate A for Working Alongside AI, not approved. */
    static final Candidate HISTORICAL_CANDIDATE_A = new Candidate("WORKING_ALONGSIDE_AI_CANDIDATE_A",
            "Working Alongside AI, candidate A (historical, not approved)",
            "The rejected Stage 5B.2 alternative that weights technical depth for checking AI output.",
            weights("ai_complementarity", 0.50, "technical_digital_depth", 0.20, "analytical_reasoning", 0.15,
                    "original_judgment_creativity", 0.15));

    record Dimension(double score, double confidence) {
    }

    record Course(String id, String title, double information, Map<String, Dimension> dimensions,
            Map<String, Double> v1Indicators, Map<String, String> v1Bands, String v1Status, String v1Confidence) {
    }

    record Factor(String dimension, double score, double weight, double effect) {
    }

    record Result(double indicator, String band, double confidence, List<Factor> drivers, List<Factor> limiting,
            List<Factor> contributions) {
    }

    static List<Course> load(Path directory) throws IOException {
        List<Course> courses = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".json")
                    && !path.getFileName().toString().equals("results.json")).sorted().toList()) {
                JsonNode capture = UniversityBaselineHarness.JSON.readTree(file.toFile());
                if (capture.get("judgmentSet") == null || capture.get("judgmentSet").isNull()) continue;
                double information = Double.NaN;
                Map<String, Dimension> dimensions = new LinkedHashMap<>();
                for (JsonNode judgment : capture.get("judgmentSet").get("judgments")) {
                    String id = judgment.get("dimensionId").asText();
                    if (id.equals("information_sufficient")) information = judgment.get("numericValue").asDouble();
                    else dimensions.put(id, new Dimension(judgment.get("numericValue").asDouble(),
                            judgment.get("confidenceValue").asDouble()));
                }
                Map<String, Double> indicators = new LinkedHashMap<>();
                Map<String, String> bands = new LinkedHashMap<>();
                JsonNode decision = capture.get("decision");
                if (decision != null && !decision.isNull()) {
                    decision.get("measures").properties().forEach(entry -> {
                        indicators.put(entry.getKey(), entry.getValue().get("indicator").asDouble());
                        bands.put(entry.getKey(), entry.getValue().get("band").asText());
                    });
                }
                courses.add(new Course(capture.get("caseId").asText(),
                        capture.get("input").get("courseTitle").asText(), information, dimensions, indicators, bands,
                        decision == null || decision.isNull() ? "—" : decision.get("status").asText(),
                        decision == null || decision.isNull() ? "—" : decision.get("confidence").asText()));
            }
        }
        return List.copyOf(courses);
    }

    static Result evaluate(Candidate candidate, Course course) {
        List<Factor> contributions = new ArrayList<>();
        double composite = 0;
        double confidence = 0;
        for (var weight : candidate.weights().entrySet()) {
            Dimension dimension = course.dimensions().get(weight.getKey());
            double normalised = dimension.score() / 4.0;
            composite += weight.getValue() * normalised;
            confidence += weight.getValue() * dimension.confidence();
            // Signed effect on the 0–10 indicator relative to the scale midpoint.
            contributions.add(new Factor(weight.getKey(), dimension.score(), weight.getValue(),
                    10.0 * weight.getValue() * (normalised - 0.5)));
        }
        List<String> order = UniversityDecisionPolicyV1.SCORE_DIMENSIONS;
        Comparator<Factor> bySize = Comparator.<Factor>comparingDouble(factor -> -Math.abs(factor.effect()))
                .thenComparingInt(factor -> order.indexOf(factor.dimension()));
        double indicator = Math.max(0, Math.min(10, composite * 10));
        return new Result(indicator, band(indicator), confidence,
                contributions.stream().filter(factor -> factor.effect() >= MATERIALITY).sorted(bySize).toList(),
                contributions.stream().filter(factor -> factor.effect() <= -MATERIALITY).sorted(bySize).toList(),
                contributions.stream().sorted(bySize).toList());
    }

    static String band(double indicator) {
        return indicator < LOW_BELOW ? "LOW" : indicator < HIGH_FROM ? "MODERATE" : "HIGH";
    }

    /** Which v1 confidence condition held each course below HIGH (v1 thresholds, read-only). */
    static String v1ConfidenceDiagnosis(Course course) {
        var values = course.dimensions().values().stream().mapToDouble(Dimension::confidence).toArray();
        double mean = java.util.Arrays.stream(values).average().orElseThrow();
        double minimum = java.util.Arrays.stream(values).min().orElseThrow();
        String lowest = course.dimensions().entrySet().stream()
                .min(Comparator.comparingDouble(entry -> entry.getValue().confidence())).orElseThrow().getKey();
        long belowSixty = java.util.Arrays.stream(values).filter(value -> value < 0.60).count();
        List<String> failing = new ArrayList<>();
        if (course.information() < 0.80) failing.add("information < 0.80");
        if (mean < 0.80) failing.add("mean " + f(mean) + " < 0.80");
        if (minimum < 0.60) failing.add("minimum " + f(minimum) + " < 0.60 (" + lowest + ")");
        double meanWithoutLowest = (java.util.Arrays.stream(values).sum() - minimum) / (values.length - 1);
        return String.format(Locale.ROOT, "| %s | %s | %s | %s | %s (%s) | %d | %s | %s |", course.id(),
                f(course.information()), f(mean), f(minimum), lowest, f(course.dimensions().get(lowest).confidence()),
                belowSixty, f(meanWithoutLowest), failing.isEmpty() ? "none (would be HIGH)" : String.join("; ", failing));
    }

    static String report(List<Course> courses) {
        StringBuilder out = new StringBuilder("# University decision v2 calibration (approved formulas)\n\n")
                .append("Inputs: ").append(courses.size()).append(" frozen Stage 5B captures. No provider call.\n")
                .append("Bands: LOW < ").append(f(LOW_BELOW)).append(" ≤ MODERATE < ").append(f(HIGH_FROM))
                .append(" ≤ HIGH. Materiality ±").append(f(MATERIALITY)).append(" points.\n\n")
                .append("## Raw judgments\n\n| Course | Info |");
        UniversityDecisionPolicyV1.SCORE_DIMENSIONS.forEach(dimension -> out.append(' ').append(dimension).append(" |"));
        out.append("\n|---|---:|").append("---:|".repeat(9)).append('\n');
        for (Course course : courses) {
            out.append("| ").append(course.id()).append(" | ").append(f(course.information())).append(" |");
            UniversityDecisionPolicyV1.SCORE_DIMENSIONS.forEach(dimension -> {
                Dimension value = course.dimensions().get(dimension);
                out.append(' ').append(f(value.score())).append(" (").append(f(value.confidence())).append(") |");
            });
            out.append('\n');
        }
        out.append("\n## Approved v2 measures against v1\n\n| Course | v1 Future Readiness | v1 AI Compl. | v1 Human Diff. | "
                + "v1 Skill Transf. | v1 Career Adapt. |");
        APPROVED.forEach(candidate -> out.append(" v2 ").append(candidate.name()).append(" |"));
        out.append("\n|---|").append("---|".repeat(5 + APPROVED.size())).append('\n');
        for (Course course : courses) {
            out.append("| ").append(course.id()).append(" |");
            for (String measure : List.of("FUTURE_READINESS", "AI_COMPLEMENTARITY", "HUMAN_DIFFERENTIATION",
                    "SKILL_TRANSFERABILITY", "CAREER_ADAPTABILITY")) {
                Double value = course.v1Indicators().get(measure);
                out.append(' ').append(value == null ? "—" : f(value) + " " + course.v1Bands().get(measure)).append(" |");
            }
            APPROVED.forEach(candidate -> {
                Result result = evaluate(candidate, course);
                out.append(' ').append(f(result.indicator())).append(' ').append(result.band()).append(" |");
            });
            out.append('\n');
        }
        for (Candidate candidate : APPROVED) {
            out.append("\n## ").append(candidate.name()).append(" (`").append(candidate.id()).append("`)\n\n")
                    .append(candidate.meaning()).append("\n\nWeights: ").append(candidate.weights())
                    .append("\n\n| Course | Score | Band | Confidence | Contributions (signed points, largest first) "
                            + "| Material drivers | Material limits |\n|---|---:|---|---:|---|---|---|\n");
            for (Course course : courses) {
                Result result = evaluate(candidate, course);
                out.append("| ").append(course.id()).append(" | ").append(f(result.indicator())).append(" | ")
                        .append(result.band()).append(" | ").append(f(result.confidence())).append(" | ")
                        .append(String.join(", ", result.contributions().stream()
                                .map(factor -> factor.dimension() + " " + signed(factor.effect())).toList()))
                        .append(" | ").append(names(result.drivers())).append(" | ").append(names(result.limiting()))
                        .append(" |\n");
            }
        }
        out.append("\n## v1 confidence diagnosis\n\n| Course | Info | Mean conf. | Min conf. | Lowest dimension | "
                + "Dims < 0.60 | Mean without lowest | Conditions preventing v1 HIGH |\n"
                + "|---|---:|---:|---:|---|---:|---:|---|\n");
        courses.forEach(course -> out.append(v1ConfidenceDiagnosis(course)).append('\n'));
        out.append("\n## Correlation between approved v2 measures\n\n| Pair | Correlation |\n|---|---:|\n");
        for (int first = 0; first < APPROVED.size(); first++) {
            for (int second = first + 1; second < APPROVED.size(); second++) {
                Candidate a = APPROVED.get(first);
                Candidate b = APPROVED.get(second);
                out.append("| ").append(a.id()).append(" / ").append(b.id()).append(" | ")
                        .append(correlation(courses.stream().mapToDouble(course -> evaluate(a, course).indicator()).toArray(),
                                courses.stream().mapToDouble(course -> evaluate(b, course).indicator()).toArray()))
                        .append(" |\n");
            }
        }
        out.append("\n## Historical comparison only: ").append(HISTORICAL_CANDIDATE_A.name()).append("\n\n")
                .append(HISTORICAL_CANDIDATE_A.meaning()).append(" Weights: ").append(HISTORICAL_CANDIDATE_A.weights())
                .append("\n\n| Course | Candidate A | Approved (candidate B) |\n|---|---|---|\n");
        for (Course course : courses) {
            Result historical = evaluate(HISTORICAL_CANDIDATE_A, course);
            Result approved = evaluate(APPROVED.get(0), course);
            out.append("| ").append(course.id()).append(" | ").append(f(historical.indicator())).append(' ')
                    .append(historical.band()).append(" | ").append(f(approved.indicator())).append(' ')
                    .append(approved.band()).append(" |\n");
        }
        return out.toString();
    }

    private static Map<String, Double> weights(Object... pairs) {
        Map<String, Double> weights = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) weights.put((String) pairs[index], (Double) pairs[index + 1]);
        return java.util.Collections.unmodifiableMap(weights);
    }

    private static String names(List<Factor> factors) {
        return factors.isEmpty() ? "—" : String.join(", ", factors.stream().map(Factor::dimension).toList());
    }

    private static String correlation(double[] x, double[] y) {
        if (x.length < 2) return "—";
        double meanX = java.util.Arrays.stream(x).average().orElseThrow();
        double meanY = java.util.Arrays.stream(y).average().orElseThrow();
        double covariance = 0, varianceX = 0, varianceY = 0;
        for (int index = 0; index < x.length; index++) {
            covariance += (x[index] - meanX) * (y[index] - meanY);
            varianceX += (x[index] - meanX) * (x[index] - meanX);
            varianceY += (y[index] - meanY) * (y[index] - meanY);
        }
        return varianceX == 0 || varianceY == 0 ? "—" : f(covariance / Math.sqrt(varianceX * varianceY));
    }

    private static String signed(double value) {
        return String.format(Locale.ROOT, "%+.2f", value);
    }

    private static String f(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private UniversityV2Calibration() {
    }
}
