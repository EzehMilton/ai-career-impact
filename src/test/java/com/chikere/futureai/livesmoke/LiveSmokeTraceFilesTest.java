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
