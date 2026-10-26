package com.chikere.futureai.livesmoke;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSmokeTraceFilesTest {

    @TempDir Path directory;

    @Test
    void theDefaultTraceDirectoryIsOutsideTargetInBothThePropertyAndTheBeanDefault() throws Exception {
        var properties = new java.util.Properties();
        try (var in = Files.newInputStream(Path.of("src/main/resources/application-live-smoke.properties"))) {
            properties.load(in);
        }
        assertThat(properties.getProperty("app.live-smoke.trace-directory"))
                .isEqualTo("${APP_LIVE_SMOKE_TRACE_DIRECTORY:.live-smoke/traces}");
        var method = java.util.Arrays.stream(LiveSmokeConfiguration.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals("liveSmokeTraceFiles")).findFirst().orElseThrow();
        var value = method.getParameters()[1].getAnnotation(org.springframework.beans.factory.annotation.Value.class);
        assertThat(value.value()).isEqualTo("${app.live-smoke.trace-directory:.live-smoke/traces}");
        assertThat(Files.readString(Path.of(".gitignore")).lines()).contains(".live-smoke/");
    }

    @Test
    void tracesInTheDefaultLocationSurviveAMavenCleanOfTarget() throws Exception {
        // A stand-in working directory: Maven clean deletes target/, never .live-smoke/.
        Path workingDirectory = directory.resolve("workspace");
        Path target = Files.createDirectories(workingDirectory.resolve("target/classes"));
        Files.writeString(target.resolve("Built.class"), "bytes");
        var files = new LiveSmokeTraceFiles(workingDirectory.resolve(".live-smoke/traces"), List.of());
        UUID id = UUID.randomUUID();
        files.accept("t", id, "{\"stage\":\"SUMMARY\"}");

        try (var walk = Files.walk(workingDirectory.resolve("target"))) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
        }

        assertThat(workingDirectory.resolve("target")).doesNotExist();
        assertThat(files.fileFor(id)).hasValue(workingDirectory.resolve(".live-smoke/traces/" + id + ".jsonl")
                .toAbsolutePath().normalize());
        assertThat(Files.readString(files.fileFor(id).orElseThrow())).isEqualTo("{\"stage\":\"SUMMARY\"}\n");
        // A fresh instance after the clean finds the same file: nothing depends on target/.
        assertThat(new LiveSmokeTraceFiles(workingDirectory.resolve(".live-smoke/traces"), List.of()).fileFor(id))
                .isPresent();
    }

    @Test
    void theTraceDirectoryIsCreatedWhenItDoesNotExist() {
        Path missing = directory.resolve("not-yet/.live-smoke/traces");
        assertThat(missing).doesNotExist();

        new LiveSmokeTraceFiles(missing, List.of());

        assertThat(missing).isDirectory();
    }

    @Test
    void eventsBeforeTheAssessmentIdJoinTheirOwnAssessmentsFileOnly() throws Exception {
        var files = new LiveSmokeTraceFiles(directory, List.of());
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        files.accept("trace-1", null, "{\"stage\":\"FORM_INPUT\",\"n\":1}");
        files.accept("trace-2", null, "{\"stage\":\"FORM_INPUT\",\"n\":2}");
        files.accept("trace-1", first, "{\"stage\":\"PERSISTENCE\",\"n\":1}");
        files.accept("trace-1", null, "{\"stage\":\"SUMMARY\",\"n\":1}");
        files.accept("report-trace", first, "{\"stage\":\"REPORT_SUMMARY\",\"n\":1}");
        files.accept("trace-2", second, "{\"stage\":\"PERSISTENCE\",\"n\":2}");
        files.accept(null, null, "{\"stage\":\"ORPHAN\"}");

        assertThat(Files.readAllLines(files.fileFor(first).orElseThrow())).containsExactly(
                "{\"stage\":\"FORM_INPUT\",\"n\":1}", "{\"stage\":\"PERSISTENCE\",\"n\":1}",
                "{\"stage\":\"SUMMARY\",\"n\":1}", "{\"stage\":\"REPORT_SUMMARY\",\"n\":1}");
        assertThat(Files.readAllLines(files.fileFor(second).orElseThrow())).containsExactly(
                "{\"stage\":\"FORM_INPUT\",\"n\":2}", "{\"stage\":\"PERSISTENCE\",\"n\":2}");
        try (var listing = Files.list(directory)) {
            assertThat(listing).hasSize(2);
        }
    }

    @Test
    void configuredSecretsAreReplacedBeforeAnythingReachesDisk() throws Exception {
        var files = new LiveSmokeTraceFiles(directory, List.of("sk-live-openai-0123456789", "", "short"));
        UUID id = UUID.randomUUID();

        files.accept("t", id, "{\"prompt\":\"use sk-live-openai-0123456789 please, a short note\"}");

        assertThat(Files.readString(files.fileFor(id).orElseThrow()))
                .contains("[REDACTED]", "a short note").doesNotContain("sk-live-openai-0123456789");
    }

    @Test
    void metadataIsWrittenOnlyForAnAssessmentThatHasATraceAndNothingOutsideTheDirectoryIsReachable() throws Exception {
        var files = new LiveSmokeTraceFiles(directory, List.of());
        UUID traced = UUID.randomUUID();
        files.accept("t", traced, "{\"stage\":\"SUMMARY\"}");

        files.annotate(traced, LiveSmokeJourney.PROFESSIONAL, "registered-nurse");
        files.annotate(UUID.randomUUID(), LiveSmokeJourney.UNIVERSITY, LiveSmokeTraceFiles.CUSTOM);

        assertThat(Files.readAllLines(files.fileFor(traced).orElseThrow())).last().asString()
                .contains("\"stage\":\"LIVE_SMOKE_METADATA\"", "\"smokeCaseId\":\"registered-nurse\"",
                        "\"journey\":\"PROFESSIONAL\"");
        try (var listing = Files.list(directory)) {
            assertThat(listing).hasSize(1);
        }
        Files.writeString(directory.getParent().resolve("outside.jsonl"), "secret");
        assertThat(files.fileFor(null)).isEmpty();
        assertThat(files.fileFor(UUID.randomUUID())).isEmpty();
    }
}
