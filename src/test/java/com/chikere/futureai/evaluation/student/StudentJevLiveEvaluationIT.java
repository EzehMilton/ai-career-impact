package com.chikere.futureai.evaluation.student;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJevJudgmentService;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJevStateMapper;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJudgmentSchema;
import com.chikere.futureai.judgment.typesafe.university.UniversityJevJudgmentService;
import com.chikere.futureai.judgment.typesafe.university.UniversityJevStateMapper;
import com.chikere.futureai.judgment.typesafe.university.UniversityJudgmentSchema;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springaicommunity.typesafe.TypeSafeClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit one-case Jev calibration; never selected by Surefire or ordinary verify. */
@EnabledIfEnvironmentVariable(named = "TYPESAFE_API_KEY", matches = ".+")
@EnabledIfSystemProperty(named = "jev.live.evaluation.enabled", matches = "true")
@EnabledIfSystemProperty(named = "jev.eval.journey", matches = "university|a-level")
class StudentJevLiveEvaluationIT {

    @Test
    void evaluatesOneStudentJourneyAndWritesReport() throws IOException {
        String journey = System.getProperty("jev.eval.journey");
        String model = System.getProperty("spring.ai.typesafe.model", "jev-latest");
        TypeSafeClient client = TypeSafeClient.builder()
                .apiKey(System.getenv("TYPESAFE_API_KEY"))
                .defaultModel(model)
                .build();

        Instant startedAt = Instant.now();
        long startNanos = System.nanoTime();
        Evaluation result = switch (journey) {
            case "university" -> university(client, model);
            case "a-level" -> aLevel(client, model);
            default -> throw new IllegalArgumentException("Unknown jev.eval.journey: " + journey);
        };
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startNanos);
        Path report = Path.of("target", "jev-evaluation", journey + "-v1-report.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, render(result, model, startedAt, elapsed), StandardCharsets.UTF_8);
        System.out.println("Student Jev evaluation report written to " + report.toAbsolutePath());
    }

    private Evaluation university(TypeSafeClient client, String model) {
        var state = new UniversityAssessmentState("Computer Science",
                "Study of software design, algorithms, data structures, databases, computer systems and "
                        + "mathematics through programming projects and a final-year project.",
                List.of("Algorithms and data structures", "Software engineering", "Databases",
                        "Computer systems", "Mathematics", "Final-year project"),
                List.of("Programming", "Problem solving", "System design", "Teamwork"),
                List.of(), Optional.empty(), Optional.of("United Kingdom"));
        var schema = new UniversityJudgmentSchema();
        var provider = new UniversityJevJudgmentService(client, schema, new UniversityJevStateMapper(), model);
        AssessmentJudgmentSet judgments = provider.judge(state);
        checkJudgments(judgments, JourneyType.UNIVERSITY, schema.version(), schema.questions().keySet());
        assertThat(provider.source().name()).isEqualTo("JEV");
        assertThat(provider.modelIdentifier()).contains(model);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        List<Measure> measures = decision.measures().values().stream()
                .map(value -> new Measure(value.measure().name(), value.indicator(),
                        value.band().name(), value.confidence()))
                .toList();
        return new Evaluation("computer_science", judgments, decision.policyVersion(),
                decision.assessmentDecision().confidence().name(),
                decision.assessmentDecision().status().name(), measures);
    }

    private Evaluation aLevel(TypeSafeClient client, String model) {
        var state = new ALevelAssessmentState(
                List.of("Mathematics", "Economics", "Geography"), Map.of(),
                List.of("Problem solving", "Analysing data and real-world issues"),
                List.of("Economics", "Geography"), List.of(),
                Optional.of("United Kingdom"), Optional.empty());
        var schema = new ALevelJudgmentSchema();
        var provider = new ALevelJevJudgmentService(client, schema, new ALevelJevStateMapper(), model);
        AssessmentJudgmentSet judgments = provider.judge(state);
        checkJudgments(judgments, JourneyType.A_LEVEL, schema.version(), schema.questions().keySet());
        assertThat(provider.source().name()).isEqualTo("JEV");
        assertThat(provider.modelIdentifier()).contains(model);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        List<Measure> measures = decision.measures().values().stream()
                .map(value -> new Measure(value.measure().name(), value.indicator(),
                        value.band().name(), value.confidence()))
                .toList();
        return new Evaluation("maths_economics_geography", judgments, decision.policyVersion(),
                decision.assessmentDecision().confidence().name(),
                decision.assessmentDecision().status().name(), measures);
    }

    private void checkJudgments(AssessmentJudgmentSet set, JourneyType journey, String schema, Set<String> expected) {
        assertThat(set.journey()).isEqualTo(journey);
        assertThat(set.schemaVersion()).isEqualTo(schema);
        assertThat(set.judgments()).hasSize(expected.size());
        assertThat(set.judgments().stream().map(DimensionJudgment::dimensionId).toList())
                .containsExactlyInAnyOrderElementsOf(expected);
        for (DimensionJudgment judgment : set.judgments()) {
            assertThat(judgment.numericValue()).isPresent();
            double value = judgment.numericValue().orElseThrow();
            if (judgment.dimensionId().equals("information_sufficient")) {
                assertThat(value).isBetween(0.0, 1.0);
            }
            else {
                assertThat(value).isBetween(0.0, 4.0);
                assertThat(judgment.confidenceValue()).isPresent();
                assertThat(judgment.confidenceValue().orElseThrow()).isBetween(0.0, 1.0);
                assertThat(judgment.probabilityDistribution()).isNotEmpty();
                assertThat(judgment.probabilityDistribution().keySet()).isSubsetOf("0", "1", "2", "3", "4");
                assertThat(judgment.probabilityDistribution().values().stream().mapToDouble(Double::doubleValue).sum())
                        .isCloseTo(1.0, org.assertj.core.data.Offset.offset(1.0e-9));
            }
        }
    }

    private String render(Evaluation result, String model, Instant startedAt, Duration elapsed) {
        StringBuilder out = new StringBuilder("# Live Jev evaluation: " + result.caseId() + "\n\n")
                .append("- Journey: `").append(result.judgments().journey()).append("`\n")
                .append("- Source: `JEV`\n")
                .append("- Configured model: `").append(model).append("`\n")
                .append("- Judgment schema: `").append(result.judgments().schemaVersion()).append("`\n")
                .append("- Decision policy: `").append(result.policyVersion()).append("`\n")
                .append("- Started at: ").append(startedAt).append("\n")
                .append("- Elapsed: ").append(elapsed.toMillis()).append(" ms\n")
                .append("- Provider request ID / resolved model / token usage: not exposed by this adapter\n\n")
                .append("| Atomic judgment | Value | Nearest label | Confidence | Probabilities |\n")
                .append("|---|---:|---|---:|---|\n");
        for (DimensionJudgment judgment : result.judgments().judgments()) {
            out.append("| `").append(judgment.dimensionId()).append("` | ")
                    .append(format(judgment.numericValue().orElseThrow())).append(" | ")
                    .append(judgment.categoricalValue().orElse("—").replace("|", "\\|"))
                    .append(" | ")
                    .append(judgment.confidenceValue().isPresent()
                            ? format(judgment.confidenceValue().orElseThrow()) : "—")
                    .append(" | ");
            judgment.probabilityDistribution().forEach((level, probability) ->
                    out.append(level).append('=').append(format(probability)).append(' '));
            out.append("|\n");
        }
        out.append("\n| Java measure | Indicator | Band | Confidence |\n")
                .append("|---|---:|---|---:|\n");
        result.measures().forEach(measure -> out.append("| ").append(measure.name())
                .append(" | ").append(format(measure.indicator()))
                .append(" | ").append(measure.band())
                .append(" | ").append(format(measure.confidence())).append(" |\n"));
        out.append("\n- Assessment confidence: ").append(result.assessmentConfidence())
                .append("\n- Assessment status: ").append(result.status()).append("\n");
        return out.toString();
    }

    private String format(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }

    private record Measure(String name, double indicator, String band, double confidence) {
    }

    private record Evaluation(String caseId, AssessmentJudgmentSet judgments, String policyVersion,
            String assessmentConfidence, String status, List<Measure> measures) {
    }
}
