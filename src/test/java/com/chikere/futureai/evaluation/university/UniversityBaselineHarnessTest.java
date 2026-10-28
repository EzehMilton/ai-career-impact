package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.evaluation.university.UniversityBaselineHarness.Mode;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.typesafe.university.UniversityDimensionNames;
import com.chikere.futureai.judgment.typesafe.university.UniversityJudgmentSchema;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * The Stage 5B harness run offline: synthetic judgments instead of Jev. Proves the plumbing and the output files,
 * that scoring is the unchanged v1 policy, and that the live run cannot start from an ordinary test run.
 */
class UniversityBaselineHarnessTest {

    /** Synthetic dry-run values, information then the nine scores in schema order. Not evidence of anything. */
    private static final Map<String, double[]> SYNTHETIC = Map.of(
            "computer-science", new double[] {0.9, 3.8, 3.6, 2.8, 1.6, 2.4, 3.0, 3.6, 3.2, 3.2},
            "business-management", new double[] {0.85, 2.0, 2.6, 2.4, 3.0, 2.8, 2.4, 3.0, 3.4, 3.4},
            "nursing", new double[] {0.9, 1.6, 2.4, 2.2, 3.9, 3.4, 3.9, 2.2, 2.2, 1.6},
            "law", new double[] {0.9, 1.4, 3.4, 3.0, 2.6, 3.2, 2.0, 3.0, 3.0, 2.8},
            "graphic-design", new double[] {0.85, 2.8, 1.6, 3.8, 2.4, 2.4, 3.4, 3.0, 2.4, 2.4},
            "mechanical-engineering", new double[] {0.9, 3.4, 3.6, 2.8, 2.0, 2.6, 3.6, 3.0, 2.8, 2.8},
            "english-literature", new double[] {0.9, 0.8, 2.6, 3.4, 2.4, 3.2, 1.0, 2.2, 3.0, 2.8});

    private static final List<String> FILES = List.of("computer-science.json", "business-management.json",
            "nursing.json", "law.json", "graphic-design.json", "mechanical-engineering.json",
            "english-literature.json");

    @Test
    void theDryRunExecutesAllSevenCoursesAndWritesEveryOutput() throws Exception {
        var provider = new Synthetic();
        Instant startedAt = Instant.parse("2026-10-27T12:00:00Z");

        var runs = new UniversityBaselineHarness(provider, new UniversityDecisionPolicyV1())
                .run(UniversityBaselineCases.ALL);
        // A fixed directory under target/ so the dry-run output can be inspected; a clean removes it.
        Path directory = Path.of("target", "university-baseline-dry-run");
        UniversityBaselineHarness.write(directory, Mode.DRY_RUN, "synthetic", startedAt, runs);

        assertThat(runs).hasSize(7);
        assertThat(provider.calls).hasSize(7);
        assertThat(runs).allSatisfy(run -> {
            assertThat(run.jevCalls()).isEqualTo(1);
            assertThat(run.error()).isEmpty();
            assertThat(run.decision()).isPresent();
        });
        assertThat(runs.stream().map(run -> run.baselineCase().id() + ".json").toList()).isEqualTo(FILES);
        for (String file : FILES) assertThat(directory.resolve(file)).isRegularFile();

        JsonNode results = UniversityBaselineHarness.JSON.readTree(directory.resolve("results.json").toFile());
        assertThat(results.get("mode").asText()).isEqualTo("DRY_RUN");
        assertThat(results.get("totalJevCalls").asInt()).isEqualTo(7);
        assertThat(results.get("policyVersion").asText()).isEqualTo("university-decision-v1");
        assertThat(results.get("courses")).hasSize(7);
        for (JsonNode course : results.get("courses")) {
            assertThat(course.get("measureScores")).hasSize(5);
            assertThat(course.get("measureBands")).hasSize(5);
            assertThat(course.get("schemaVersion").asText()).isEqualTo("university-v1");
            assertThat(course.get("providerError").isNull()).isTrue();
        }
        String readme = Files.readString(directory.resolve("README.md"));
        assertThat(readme).contains("DRY_RUN", "plumbing check only", "BA English Literature", "Measure clustering",
                "makes no policy recommendation");
    }

    @Test
    void eachCaptureRecomputesExactlyUnderTheUnchangedV1Policy() throws Exception {
        Path directory = Files.createTempDirectory("university-baseline");
        var runs = new UniversityBaselineHarness(new Synthetic(), new UniversityDecisionPolicyV1())
                .run(UniversityBaselineCases.ALL);
        UniversityBaselineHarness.write(directory, Mode.DRY_RUN, "synthetic", Instant.now(), runs);

        // The pinned v1 headline weights: the harness uses the production policy, not a copy of it.
        assertThat(UniversityDecisionPolicyV1.weightsFor(UniversityMeasure.FUTURE_READINESS)).containsExactly(
                Map.entry("analytical_reasoning", 0.20), Map.entry("technical_digital_depth", 0.15),
                Map.entry("original_judgment_creativity", 0.15), Map.entry("ai_complementarity", 0.15),
                Map.entry("skill_transferability", 0.15), Map.entry("contextual_complexity", 0.10),
                Map.entry("career_breadth", 0.10));
        for (var run : runs) {
            JsonNode capture = UniversityBaselineHarness.JSON.readTree(
                    directory.resolve(run.baselineCase().id() + ".json").toFile());
            assertThat(capture.get("input").get("courseTitle").asText())
                    .isEqualTo(run.baselineCase().state().courseTitle());
            assertThat(capture.get("input").get("keyModules")).hasSize(run.baselineCase().state().keyModules().size());

            var decision = new UniversityDecisionPolicyV1().decide(judgments(capture.get("judgmentSet")));
            assertThat(decision).isEqualTo(run.decision().orElseThrow());
            JsonNode measures = capture.get("decision").get("measures");
            decision.measures().forEach((measure, result) -> {
                assertThat(measures.get(measure.name()).get("indicator").asDouble())
                        .isCloseTo(result.indicator(), within(1.0e-12));
                assertThat(measures.get(measure.name()).get("band").asText()).isEqualTo(result.band().name());
            });
        }
    }

    @Test
    void aProviderFailureIsRecordedAndTheRemainingCoursesStillRun() throws Exception {
        var provider = new Synthetic("law");
        var runs = new UniversityBaselineHarness(provider, new UniversityDecisionPolicyV1())
                .run(UniversityBaselineCases.ALL);
        Path directory = Files.createTempDirectory("university-baseline");
        UniversityBaselineHarness.write(directory, Mode.DRY_RUN, "synthetic", Instant.now(), runs);

        assertThat(provider.calls).hasSize(7);
        var law = runs.stream().filter(run -> run.baselineCase().id().equals("law")).findFirst().orElseThrow();
        assertThat(law.error()).hasValueSatisfying(error -> assertThat(error).startsWith("provider: "));
        assertThat(law.decision()).isEmpty();
        assertThat(runs.stream().filter(run -> run.decision().isPresent())).hasSize(6);
        JsonNode capture = UniversityBaselineHarness.JSON.readTree(directory.resolve("law.json").toFile());
        assertThat(capture.get("judgmentSet").isNull()).isTrue();
        assertThat(Files.readString(directory.resolve("README.md"))).contains("- Errors: law");
    }

    @Test
    void theHarnessHoldsOnlyAJudgmentProviderAndTheDecisionPolicy() {
        // No Stripe, payment, persistence, narrative or OpenAI collaborator can be reached from the harness.
        assertThat(Arrays.stream(UniversityBaselineHarness.class.getDeclaredFields())
                .filter(field -> !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                .map(Field::getType).toList())
                .containsExactly(UniversityJudgmentProvider.class, UniversityDecisionPolicyV1.class,
                        UniversityBaselineHarness.RawResponses.class);
    }

    @Test
    void withoutASelectionAllSevenCasesRun() {
        assertThat(UniversityBaselineCases.select(null)).isEqualTo(UniversityBaselineCases.ALL);
        assertThat(UniversityBaselineCases.select("  ")).isEqualTo(UniversityBaselineCases.ALL);
        var provider = new Synthetic();
        assertThat(new UniversityBaselineHarness(provider, new UniversityDecisionPolicyV1()).runSelected(""))
                .hasSize(7);
        assertThat(provider.calls).hasSize(7);
    }

    @Test
    void selectingNursingCallsTheProviderExactlyOnceWithTheUnchangedInput() {
        var provider = new Synthetic();
        var runs = new UniversityBaselineHarness(provider, new UniversityDecisionPolicyV1()).runSelected("nursing");

        assertThat(provider.calls).containsExactly("nursing");
        assertThat(runs).singleElement().satisfies(run -> {
            assertThat(run.baselineCase()).isSameAs(UniversityBaselineCases.ALL.get(2));
            assertThat(run.jevCalls()).isEqualTo(1);
        });
    }

    @Test
    void anUnknownCaseFailsBeforeAnyProviderCall() {
        var provider = new Synthetic();
        var harness = new UniversityBaselineHarness(provider, new UniversityDecisionPolicyV1());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> harness.runSelected("nurse"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown university.baseline.case 'nurse'").hasMessageContaining("nursing");
        assertThat(provider.calls).isEmpty();
    }

    @Test
    void theRawResponseIsKeptEvenWhenTheAdapterRejectsIt() throws Exception {
        // As in the live Nursing failure: Jev answered, then mapping the answer into a judgment threw.
        Map<String, Object> raw = Map.of("answers", Map.of("career_breadth", Map.of("probabilities", Map.of())),
                "probabilitySums", Map.of("career_breadth", 0.99));
        var runs = new UniversityBaselineHarness(new Synthetic("nursing"), new UniversityDecisionPolicyV1(),
                () -> Optional.of(raw)).runSelected("nursing");
        Path directory = Files.createTempDirectory("university-baseline");
        UniversityBaselineHarness.write(directory, Mode.LIVE, "jev-latest", Instant.now(), runs);

        JsonNode capture = UniversityBaselineHarness.JSON.readTree(directory.resolve("nursing.json").toFile());
        assertThat(capture.get("provider").get("error").asText()).startsWith("provider: ");
        assertThat(capture.get("judgmentSet").isNull()).isTrue();
        assertThat(capture.get("rawProviderResponse").get("probabilitySums").get("career_breadth").asDouble())
                .isEqualTo(0.99);
        assertThat(UniversityBaselineHarness.JSON.readTree(directory.resolve("results.json").toFile())
                .get("courses")).hasSize(1);
    }

    @Test
    void theRecordingClientIsBuiltWithoutCallingJevAndStartsEmpty() {
        var client = RecordingTypeSafeClient.create("not-a-key", "jev-latest");
        assertThat(client.defaultModel()).isEqualTo("jev-latest");
        assertThat(client.take()).isEmpty();
    }

    @Test
    void theLiveBaselineCannotRunFromAnOrdinaryTestRun() throws Exception {
        assertThat(System.getProperty("university.baseline.live.enabled")).isNull();
        // Surefire selects *Test classes; the live class is an IT that only the live-university profile includes.
        assertThat(UniversityBaselineLiveIT.class.getSimpleName()).endsWith("IT");
        assertThat(UniversityBaselineLiveIT.class.getAnnotation(EnabledIfSystemProperty.class).named())
                .isEqualTo("university.baseline.live.enabled");
        assertThat(UniversityBaselineLiveIT.class.getAnnotation(EnabledIfEnvironmentVariable.class).named())
                .isEqualTo("TYPESAFE_API_KEY");

        String pom = Files.readString(Path.of("pom.xml"));
        String build = pom.substring(pom.indexOf("<build>"), pom.indexOf("<profiles>"));
        assertThat(build).as("no failsafe in the default build").doesNotContain("maven-failsafe-plugin");
        assertThat(pom.split("UniversityBaselineLiveIT", -1)).as("included exactly once").hasSize(2);
        int include = pom.indexOf("UniversityBaselineLiveIT");
        String profile = pom.substring(pom.lastIndexOf("<profile>", include), include);
        assertThat(profile).contains("<id>live-university</id>",
                "<university.baseline.live.enabled>true</university.baseline.live.enabled>");
    }

    private static AssessmentJudgmentSet judgments(JsonNode set) {
        List<DimensionJudgment> judgments = new ArrayList<>();
        for (JsonNode node : set.get("judgments")) {
            Map<String, Double> probabilities = new LinkedHashMap<>();
            node.get("probabilityDistribution").properties()
                    .forEach(entry -> probabilities.put(entry.getKey(), entry.getValue().asDouble()));
            judgments.add(new DimensionJudgment(node.get("dimensionId").asText(), node.get("name").asText(),
                    OptionalDouble.of(node.get("numericValue").asDouble()),
                    node.get("categoricalValue").isNull() ? Optional.empty()
                            : Optional.of(node.get("categoricalValue").asText()),
                    Optional.empty(),
                    node.get("confidenceValue").isNull() ? OptionalDouble.empty()
                            : OptionalDouble.of(node.get("confidenceValue").asDouble()),
                    probabilities));
        }
        return new AssessmentJudgmentSet(JourneyType.valueOf(set.get("journey").asText()),
                set.get("schemaVersion").asText(), judgments);
    }

    /** Offline stand-in for Jev, keyed by course title; optionally fails one course. */
    static final class Synthetic implements UniversityJudgmentProvider {
        final List<String> calls = new ArrayList<>();
        private final String failing;

        Synthetic() {
            this("");
        }

        Synthetic(String failing) {
            this.failing = failing;
        }

        @Override
        public AssessmentJudgmentSet judge(UniversityAssessmentState state) {
            String id = UniversityBaselineCases.ALL.stream().filter(value -> value.state().equals(state))
                    .findFirst().orElseThrow().id();
            calls.add(id);
            if (id.equals(failing)) throw new IllegalStateException("synthetic provider failure");
            double[] values = SYNTHETIC.get(id);
            List<DimensionJudgment> judgments = new ArrayList<>();
            judgments.add(new DimensionJudgment(UniversityJudgmentSchema.INFORMATION_SUFFICIENT,
                    UniversityDimensionNames.of(UniversityJudgmentSchema.INFORMATION_SUFFICIENT),
                    OptionalDouble.of(values[0]), Optional.empty(), Optional.empty(), OptionalDouble.empty(),
                    Map.of()));
            for (int index = 0; index < UniversityDecisionPolicyV1.SCORE_DIMENSIONS.size(); index++) {
                String dimension = UniversityDecisionPolicyV1.SCORE_DIMENSIONS.get(index);
                judgments.add(new DimensionJudgment(dimension, UniversityDimensionNames.of(dimension),
                        OptionalDouble.of(values[index + 1]), Optional.empty(), Optional.empty(),
                        OptionalDouble.of(0.85), Map.of(String.valueOf(Math.round(values[index + 1])), 1.0)));
            }
            return new AssessmentJudgmentSet(JourneyType.UNIVERSITY, UniversityJudgmentSchema.VERSION, judgments);
        }

        @Override
        public AssessmentSource source() {
            return AssessmentSource.LOCAL_FIXTURE;
        }
    }
}
