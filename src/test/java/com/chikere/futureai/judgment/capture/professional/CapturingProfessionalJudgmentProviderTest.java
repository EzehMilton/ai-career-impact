package com.chikere.futureai.judgment.capture.professional;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevStateMapper;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springaicommunity.typesafe.question.Score;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class CapturingProfessionalJudgmentProviderTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-15T12:34:56.789Z"), ZoneOffset.UTC);
    private static final ProfessionalAssessmentState STATE = new ProfessionalAssessmentState("Software Engineer",
            "Builds and maintains online casino game platforms for operator brands.",
            List.of("Review pull requests", "Run the weekly on-call handover"), List.of("Java", "Communication"),
            OptionalInt.of(4));

    @TempDir Path directory;

    @Test
    void writesTheStateQuestionsAndAnswersOfEachSuccessfulCall() throws IOException {
        var delegate = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect");
        var provider = capturing(new ModelNamed(delegate, "jev-latest"), directory);

        AssessmentJudgmentSet result = provider.judge(STATE);

        assertThat(result).isEqualTo(delegate.judge(STATE));
        List<Path> files = files(directory);
        assertThat(files).hasSize(1);
        assertThat(files.getFirst().getFileName().toString()).matches("20261015T123456\\.789Z-[0-9a-f]{8}\\.json");

        JsonNode json = new ObjectMapper().readTree(files.getFirst().toFile());
        assertThat(json.fieldNames()).toIterable().containsExactly(
                "source", "capturedAt", "schemaVersion", "modelIdentifier", "state", "questions", "answers");
        assertThat(json.get("source").asText()).isEqualTo("LOCAL_FIXTURE");
        assertThat(json.get("capturedAt").asText()).isEqualTo("2026-10-15T12:34:56.789Z");
        assertThat(json.get("schemaVersion").asText()).isEqualTo("professional-v1");
        assertThat(json.get("modelIdentifier").asText()).isEqualTo("jev-latest");
        assertThat(new ObjectMapper().convertValue(json.get("state"), java.util.Map.class))
                .isEqualTo(new ProfessionalJevStateMapper().map(STATE));

        var schema = new ProfessionalJudgmentSchema();
        assertThat(json.get("questions").fieldNames()).toIterable()
                .containsExactlyElementsOf(schema.questions().keySet());
        JsonNode repeatability = json.get("questions").get("task_repeatability");
        Score expected = (Score) schema.questions().get("task_repeatability");
        assertThat(repeatability.get("type").asText()).isEqualTo("score");
        assertThat(repeatability.get("instructions").asText()).isEqualTo(expected.instructions().asText());
        assertThat(repeatability.get("levels")).hasSize(5);
        assertThat(repeatability.get("levels").get(0).asText()).isEqualTo(expected.criteria().getFirst().asText());
        JsonNode sufficiency = json.get("questions").get("information_sufficient");
        assertThat(sufficiency.get("type").asText()).isEqualTo("noul");
        assertThat(sufficiency.get("whenTrue").asText()).startsWith("There is enough information");
        assertThat(sufficiency.get("whenFalse").asText()).startsWith("The information is too vague");

        assertThat(json.get("answers").fieldNames()).toIterable()
                .containsExactlyElementsOf(schema.questions().keySet());
        var expectedAnswer = result.judgments().get(1);
        JsonNode answer = json.get("answers").get(expectedAnswer.dimensionId());
        assertThat(answer.fieldNames()).toIterable()
                .containsExactly("value", "nearestLabel", "confidence", "probabilities");
        assertThat(answer.get("value").asDouble()).isEqualTo(expectedAnswer.numericValue().getAsDouble());
        assertThat(answer.get("probabilities").size()).isEqualTo(expectedAnswer.probabilityDistribution().size());
    }

    @Test
    void aFailedWriteNeverAffectsTheResultAndLogsOnlyTheFailureType(CapturedOutput output) throws IOException {
        Path notADirectory = Files.writeString(directory.resolve("occupied"), "a file, not a directory");
        var delegate = new LocalFixtureProfessionalJudgmentProvider("electrician");

        AssessmentJudgmentSet result = capturing(delegate, notADirectory.resolve("captures")).judge(STATE);

        assertThat(result).isEqualTo(delegate.judge(STATE));
        assertThat(files(directory)).containsExactly(notADirectory);
        List<String> warnings = output.getAll().lines().filter(line -> line.contains("Jev capture")).toList();
        assertThat(warnings).singleElement().satisfies(line -> assertThat(line)
                .contains("WARN").matches(".*Professional Jev capture write failed failureType=\\w+Exception$")
                .doesNotContain(directory.toString()).doesNotContain("Software Engineer"));
    }

    @Test
    void nothingIsWrittenWhenTheJudgmentFails() throws IOException {
        ProfessionalJudgmentProvider failing = new ProfessionalJudgmentProvider() {
            @Override
            public AssessmentJudgmentSet judge(ProfessionalAssessmentState state) {
                throw new IllegalStateException("provider unavailable");
            }

            @Override
            public AssessmentSource source() {
                return AssessmentSource.JEV;
            }
        };

        assertThatThrownBy(() -> capturing(failing, directory).judge(STATE))
                .isInstanceOf(IllegalStateException.class).hasMessage("provider unavailable");
        assertThat(files(directory)).isEmpty();
    }

    @Test
    void sourceAndModelIdentifierComeFromTheWrappedProvider() {
        var delegate = new ModelNamed(new LocalFixtureProfessionalJudgmentProvider("electrician"), "jev-latest");
        var provider = capturing(delegate, directory);

        assertThat(provider.source()).isEqualTo(AssessmentSource.LOCAL_FIXTURE);
        assertThat(provider.modelIdentifier()).contains("jev-latest");
    }

    private static CapturingProfessionalJudgmentProvider capturing(ProfessionalJudgmentProvider delegate, Path path) {
        return new CapturingProfessionalJudgmentProvider(delegate,
                new ProfessionalJevCapture(new ProfessionalJevStateMapper(), new ProfessionalJudgmentSchema()),
                path, CLOCK);
    }

    private static List<Path> files(Path path) throws IOException {
        try (Stream<Path> files = Files.list(path)) {
            return files.sorted().toList();
        }
    }

    private record ModelNamed(ProfessionalJudgmentProvider delegate, String model)
            implements ProfessionalJudgmentProvider {
        @Override
        public AssessmentJudgmentSet judge(ProfessionalAssessmentState state) {
            return delegate.judge(state);
        }

        @Override
        public AssessmentSource source() {
            return delegate.source();
        }

        @Override
        public Optional<String> modelIdentifier() {
            return Optional.of(model);
        }
    }
}
