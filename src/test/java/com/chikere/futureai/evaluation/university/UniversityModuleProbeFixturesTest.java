package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.coursemodule.university.application.ModuleScoredJudgment;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The frozen Stage 5C live module captures that Stage 5D works from offline. No classification here: only that each
 * fixture is complete, ordered and inside the {@code university-module-v1} contract.
 */
class UniversityModuleProbeFixturesTest {

    static final Path FIXTURES = Path.of("src", "test", "resources", "university", "module-probe-v1");
    private static final List<String> COURSES = List.of("business-management", "computer-science",
            "english-literature", "graphic-design", "law", "mechanical-engineering", "nursing");
    private static final List<String> SCORED = UniversityModuleSchema.JUDGMENTS.stream()
            .filter(judgment -> !UniversityModuleSchema.TRUTH_JUDGMENTS.contains(judgment)).toList();

    @Test
    void exactlySevenAcceptedFixturesExist() throws IOException {
        try (var files = Files.list(FIXTURES)) {
            assertThat(files.map(path -> path.getFileName().toString()).sorted().toList())
                    .isEqualTo(COURSES.stream().map(course -> course + ".json").toList());
        }
        for (String course : COURSES) {
            JsonNode fixture = fixture(course);
            assertThat(fixture.get("caseId").asText()).isEqualTo(course);
            assertThat(fixture.get("mode").asText()).isEqualTo("LIVE");
            assertThat(fixture.get("schemaVersion").asText()).isEqualTo("university-module-v1");
            assertThat(fixture.get("provider").get("error").isNull()).as(course).isTrue();
            JsonNode validation = fixture.get("validation");
            assertThat(validation.get("adapterStatus").asText()).as(course).isEqualTo("ACCEPTED");
            assertThat(validation.get("expectedAnswers").asInt()).isEqualTo(64);
            assertThat(validation.get("receivedAnswers").asInt()).isEqualTo(64);
            assertThat(validation.get("missing")).isEmpty();
            assertThat(validation.get("unexpected")).isEmpty();
            assertThat(validation.get("invalid")).isEmpty();
            assertThat(validation.get("validModules").asInt()).isEqualTo(8);
        }
    }

    @Test
    void everyFixtureHasEightUniqueOrderedModulesMatchingItsSelection() throws IOException {
        for (String course : COURSES) {
            JsonNode fixture = fixture(course);
            List<String> expectedIds = IntStream.rangeClosed(1, 8).mapToObj(index -> "m" + index).toList();
            assertThat(ids(fixture.get("selectedModules"))).as(course).isEqualTo(expectedIds);
            assertThat(ids(fixture.get("moduleJudgments"))).as(course).isEqualTo(expectedIds);
            for (int index = 0; index < 8; index++) {
                assertThat(fixture.get("moduleJudgments").get(index).get("title").asText())
                        .isEqualTo(fixture.get("selectedModules").get(index).get("title").asText());
            }
            // The selection is the recorded probe selection, so Stage 5D sees exactly what Jev judged.
            var probe = UniversityModuleProbeCases.select(course).getFirst();
            assertThat(titles(fixture.get("selectedModules"))).isEqualTo(probe.selectedModules());
        }
    }

    @Test
    void everyJudgmentIsPresentInRangeAndInsideTheDistributionContract() throws IOException {
        for (String course : COURSES) {
            for (JsonNode module : fixture(course).get("moduleJudgments")) {
                String where = course + " " + module.get("id").asText();
                for (String truth : UniversityModuleSchema.TRUTH_JUDGMENTS) {
                    assertThat(module.get(truth).asDouble()).as(where + " " + truth).isBetween(0.0, 1.0);
                }
                for (String scored : SCORED) {
                    JsonNode judgment = module.get(scored);
                    assertThat(judgment).as(where + " " + scored).isNotNull();
                    assertThat(judgment.get("value").asDouble()).as(where + " " + scored).isBetween(0.0, 4.0);
                    assertThat(judgment.get("confidence").asDouble()).isBetween(0.0, 1.0);
                    Map<Integer, Double> distribution = new LinkedHashMap<>();
                    judgment.get("distribution").properties().forEach(level ->
                            distribution.put(Integer.parseInt(level.getKey()), level.getValue().asDouble()));
                    assertThat(distribution).as(where + " " + scored).isNotEmpty();
                    // The production value object enforces the 0.01 rounding tolerance; it throws if outside.
                    new ModuleScoredJudgment(judgment.get("value").asDouble(), distribution,
                            OptionalDouble.of(judgment.get("confidence").asDouble()), OptionalInt.empty(),
                            Optional.empty());
                }
                assertThat(module.size()).as(where).isEqualTo(2 + UniversityModuleSchema.JUDGMENTS.size());
            }
        }
    }

    @Test
    void theRawAnswersAreKeptAndAnswerExactlyTheQuestionsAsked() throws IOException {
        for (String course : COURSES) {
            JsonNode raw = fixture(course).get("rawProviderResponse");
            List<String> expected = new ArrayList<>();
            for (int index = 1; index <= 8; index++) {
                for (String judgment : UniversityModuleSchema.JUDGMENTS) {
                    expected.add(UniversityModuleSchema.answerName("m" + index, judgment));
                }
            }
            List<String> received = new ArrayList<>();
            raw.get("answers").fieldNames().forEachRemaining(received::add);
            assertThat(received).as(course).containsExactlyInAnyOrderElementsOf(expected);
            raw.get("probabilitySums").properties().forEach(sum -> assertThat(Math.abs(sum.getValue().asDouble() - 1.0))
                    .as(course + " " + sum.getKey()).isLessThanOrEqualTo(ModuleScoredJudgment.DISTRIBUTION_TOLERANCE
                            + 1.0e-9));
        }
    }

    private static JsonNode fixture(String course) throws IOException {
        return UniversityModuleProbe.JSON.readTree(FIXTURES.resolve(course + ".json").toFile());
    }

    private static List<String> ids(JsonNode modules) {
        List<String> ids = new ArrayList<>();
        modules.forEach(module -> ids.add(module.get("id").asText()));
        return ids;
    }

    private static List<String> titles(JsonNode modules) {
        List<String> titles = new ArrayList<>();
        modules.forEach(module -> titles.add(module.get("title").asText()));
        return titles;
    }
}
