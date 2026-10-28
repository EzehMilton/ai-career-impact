package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleJevProvider;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleStateMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.Usage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The Stage 5C probe run offline: the production adapter with a mocked TypeSafe client answering synthetically.
 * Proves the selection, the output files and the independent raw-answer validation, and that the live probe cannot
 * start from an ordinary test run.
 */
class UniversityModuleProbeTest {

    @Test
    void everyProbeCaseSelectsAtMostEightDistinctModulesFromItsFrozenCourse() {
        assertThat(UniversityModuleProbeCases.ALL).hasSize(7);
        for (var probe : UniversityModuleProbeCases.ALL) {
            var modules = probe.baselineCase().state().keyModules();
            assertThat(probe.selectedModules()).as(probe.baselineCase().id()).hasSizeBetween(1, 8)
                    .doesNotHaveDuplicates().allSatisfy(module -> assertThat(modules).contains(module));
            assertThat(probe.selectionNote()).isNotBlank();
            assertThat(probe.selectedModules().size() + probe.omittedModules().size()).isEqualTo(modules.size());
            // The request carries the frozen course context but never the preferred career areas.
            var request = probe.request();
            assertThat(request.courseTitle()).isEqualTo(probe.baselineCase().state().courseTitle());
            assertThat(request.skillsDeveloped()).isEqualTo(probe.baselineCase().state().skillsDeveloped());
            assertThat(request.keyedModules().values()).containsExactlyElementsOf(probe.selectedModules());
        }
        assertThat(UniversityModuleProbeCases.select("")).isEqualTo(UniversityModuleProbeCases.ALL);
        assertThat(UniversityModuleProbeCases.select("nursing")).singleElement()
                .satisfies(probe -> assertThat(probe.baselineCase().id()).isEqualTo("nursing"));
        assertThatThrownBy(() -> UniversityModuleProbeCases.select("nurse")).hasMessageContaining("Unknown");
    }

    @Test
    void theDryRunProbesAllSevenCoursesWithOneCallEachAndWritesEveryOutput() throws Exception {
        var fake = new FakeJev(answers -> { });
        var runs = new UniversityModuleProbe(provider(fake.client), fake).run(UniversityModuleProbeCases.ALL);
        Path directory = Path.of("target", "university-module-probe-dry-run");
        UniversityModuleProbe.write(directory, "DRY_RUN", "synthetic", Instant.parse("2026-10-28T12:00:00Z"), runs);

        assertThat(fake.calls).isEqualTo(7);
        assertThat(runs).allSatisfy(run -> {
            assertThat(run.result()).isPresent();
            assertThat(run.validation().orElseThrow().validModules()).isEqualTo(run.probeCase().selectedModules().size());
            assertThat(run.validation().orElseThrow().missingOrInvalid()).isZero();
        });
        for (String id : List.of("computer-science", "business-management", "nursing", "law", "graphic-design",
                "mechanical-engineering", "english-literature")) {
            JsonNode capture = UniversityModuleProbe.JSON.readTree(directory.resolve(id + ".json").toFile());
            assertThat(capture.get("selectedModules").get(0).get("id").asText()).isEqualTo("m1");
            assertThat(capture.get("moduleJudgments")).hasSize(capture.get("selectedModules").size());
            assertThat(capture.get("rawProviderResponse").get("answers")).isNotEmpty();
            assertThat(capture.get("courseContext").get("preferredCareerAreas").asText()).startsWith("not sent");
            assertThat(capture.get("aiLearningAssistanceInvariant").asText()).contains("does not determine");
        }
        JsonNode results = UniversityModuleProbe.JSON.readTree(directory.resolve("results.json").toFile());
        assertThat(results.get("totalJevCalls").asInt()).isEqualTo(7);
        assertThat(results.get("schemaVersion").asText()).isEqualTo("university-module-v1");
        assertThat(results.get("courses").get(0).get("inputTokens").asInt()).isEqualTo(1800);
        assertThat(Files.readString(directory.resolve("README.md"))).contains("plumbing check only",
                "Clarity below", "does not determine what use is permitted");
    }

    @Test
    void aMalformedResponseIsRejectedByTheAdapterButStillCapturedAndExplained() throws Exception {
        var fake = new FakeJev(answers -> {
            answers.remove("m3__practical_application");
            answers.put("m9__human_judgement", answers.get("m1__human_judgement"));
            answers.put("m2__interpersonal_work", new ScoreAnswer(2.0, Map.of(), Map.of(1, 0.7, 3, 0.7), 0.8));
        });
        var runs = new UniversityModuleProbe(provider(fake.client), fake)
                .run(UniversityModuleProbeCases.select("law"));
        Path directory = Files.createTempDirectory("module-probe");
        UniversityModuleProbe.write(directory, "DRY_RUN", "synthetic", Instant.now(), runs);

        var run = runs.getFirst();
        assertThat(run.result()).isEmpty();
        assertThat(run.error()).hasValueSatisfying(error -> assertThat(error).contains("did not match"));
        var validation = run.validation().orElseThrow();
        assertThat(validation.missing()).containsExactly("m3__practical_application");
        assertThat(validation.unexpected()).containsExactly("m9__human_judgement");
        assertThat(validation.invalid()).singleElement().satisfies(value -> assertThat(value)
                .startsWith("m2__interpersonal_work"));
        assertThat(validation.validModules()).isEqualTo(6);
        JsonNode capture = UniversityModuleProbe.JSON.readTree(directory.resolve("law.json").toFile());
        assertThat(capture.get("validation").get("adapterStatus").asText()).isEqualTo("REJECTED");
        assertThat(capture.get("moduleJudgments").isNull()).isTrue();
        assertThat(capture.get("rawProviderResponse").isNull()).isFalse();
    }

    @Test
    void theLiveProbeCannotRunFromAnOrdinaryTestRun() throws Exception {
        assertThat(System.getProperty("university.module.probe.enabled")).isNull();
        assertThat(UniversityModuleProbeLiveIT.class.getSimpleName()).endsWith("IT");
        assertThat(UniversityModuleProbeLiveIT.class.getAnnotation(EnabledIfSystemProperty.class).named())
                .isEqualTo("university.module.probe.enabled");
        assertThat(UniversityModuleProbeLiveIT.class.getAnnotation(EnabledIfEnvironmentVariable.class).named())
                .isEqualTo("TYPESAFE_API_KEY");
        String pom = Files.readString(Path.of("pom.xml"));
        String build = pom.substring(pom.indexOf("<build>"), pom.indexOf("<profiles>"));
        assertThat(build).doesNotContain("maven-failsafe-plugin");
        assertThat(pom.split("UniversityModuleProbeLiveIT", -1)).hasSize(2);
        int include = pom.indexOf("UniversityModuleProbeLiveIT");
        assertThat(pom.substring(pom.lastIndexOf("<profile>", include), include)).contains(
                "<id>live-university-module-probe</id>",
                "<university.module.probe.enabled>true</university.module.probe.enabled>");
    }

    private static UniversityModuleJevProvider provider(TypeSafeClient client) {
        return new UniversityModuleJevProvider(client, new UniversityModuleSchema(), new UniversityModuleStateMapper(),
                "synthetic", ProviderCostTracker.disabled());
    }

    /** A mocked TypeSafe client that answers whatever it is asked, synthetically, then lets a test spoil it. */
    private static final class FakeJev implements UniversityModuleProbe.RawResponses {
        final TypeSafeClient client = mock(TypeSafeClient.class);
        int calls;
        private SystemOneResponse last;

        @SuppressWarnings("unchecked")
        FakeJev(Consumer<Map<String, Answer>> spoil) {
            when(client.systemOne(anyMap(), anyMap())).thenAnswer(invocation -> {
                calls++;
                Map<String, Question> questions = invocation.getArgument(1);
                Map<String, Answer> answers = new LinkedHashMap<>();
                List<String> names = new ArrayList<>(questions.keySet());
                for (int index = 0; index < names.size(); index++) {
                    String name = names.get(index);
                    answers.put(name, questions.get(name) instanceof Noul ? new NoulAnswer(0.9)
                            : new ScoreAnswer(index % 5, Map.of(), Map.of(index % 5, 1.0), 0.8));
                }
                spoil.accept(answers);
                last = new SystemOneResponse("synthetic", answers, new Usage(1800, 500), "req-" + calls);
                return last;
            });
        }

        @Override
        public Optional<SystemOneResponse> takeResponse() {
            SystemOneResponse response = last;
            last = null;
            return Optional.ofNullable(response);
        }
    }
}
