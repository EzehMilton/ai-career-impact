package com.chikere.futureai.livesmoke;

import com.chikere.futureai.trace.AssessmentTraceSink;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes each assessment's trace to {@code <directory>/<assessmentId>.jsonl}: one line per existing trace event,
 * serialised and redacted exactly as logged, plus one LIVE_SMOKE_METADATA line naming the smoke case. Exists only
 * under the live-smoke profile. Files are local development diagnostics, never stored in the database.
 *
 * <p>Events written before the assessment id exists (form input, Jev, scoring) are held per trace id and written
 * once an event of the same trace carries the id. Later report traces carry the id from the start and are
 * appended to the same file. As a second safeguard after the trace's own redaction, every configured secret
 * value is replaced before a line reaches disk.</p>
 */
public final class LiveSmokeTraceFiles implements AssessmentTraceSink {

    /** The case id recorded for an assessment whose inputs match no saved smoke case. */
    public static final String CUSTOM = "CUSTOM";

    private static final Logger LOGGER = LoggerFactory.getLogger(LiveSmokeTraceFiles.class);
    private static final int MAXIMUM_PENDING_TRACES = 100;
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();

    private final Path directory;
    private final List<String> secrets;
    private final Map<String, UUID> assessmentsByTrace = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, UUID> eldest) {
            return size() > 1_000;
        }
    };
    private final Map<String, List<String>> pending = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
            return size() > MAXIMUM_PENDING_TRACES;
        }
    };

    /** @param secrets configured secret values; any that appears in a line is replaced before writing */
    public LiveSmokeTraceFiles(Path directory, List<String> secrets) {
        this.directory = directory.toAbsolutePath().normalize();
        this.secrets = secrets.stream().filter(Objects::nonNull).map(String::strip)
                .filter(value -> value.length() >= 8).distinct().toList();
        try {
            Files.createDirectories(this.directory);
        }
        catch (IOException failure) {
            throw new UncheckedIOException("live-smoke trace directory could not be created", failure);
        }
        LOGGER.warn("LIVE SMOKE TEST MODE writes assessment traces to {}. They contain submitted test data and complete "
                + "AI prompts and responses; they are local diagnostics only.", this.directory);
    }

    public Path directory() {
        return directory;
    }

    @Override
    public synchronized void accept(String assessmentTraceId, UUID assessmentId, String jsonLine) {
        String line = scrub(jsonLine);
        if (assessmentId == null && assessmentTraceId != null) {
            assessmentId = assessmentsByTrace.get(assessmentTraceId);
        }
        if (assessmentId == null) {
            if (assessmentTraceId != null) {
                pending.computeIfAbsent(assessmentTraceId, key -> new ArrayList<>()).add(line);
            }
            return;
        }
        List<String> lines = new ArrayList<>();
        if (assessmentTraceId != null) {
            assessmentsByTrace.put(assessmentTraceId, assessmentId);
            lines.addAll(Optional.ofNullable(pending.remove(assessmentTraceId)).orElse(List.of()));
        }
        lines.add(line);
        append(assessmentId, lines);
    }

    /**
     * Records which smoke case produced the assessment, or {@link #CUSTOM}. Diagnostic only: nothing reads it to
     * score or report. Written only when the assessment already has a trace file.
     */
    public synchronized void annotate(UUID assessmentId, LiveSmokeJourney journey, String smokeCaseId) {
        if (fileFor(assessmentId).isEmpty()) {
            return;
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("stage", "LIVE_SMOKE_METADATA");
        metadata.put("journey", journey.name());
        metadata.put("executionMode", "LIVE_SMOKE");
        metadata.put("assessmentId", assessmentId);
        metadata.put("smokeCaseId", smokeCaseId);
        metadata.put("at", Instant.now().toString());
        try {
            append(assessmentId, List.of(JSON.writeValueAsString(metadata)));
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException("live-smoke metadata could not be serialised", failure);
        }
    }

    /** The trace file of exactly this assessment, if one was written. The path is built only from the UUID. */
    public Optional<Path> fileFor(UUID assessmentId) {
        if (assessmentId == null) {
            return Optional.empty();
        }
        Path file = directory.resolve(assessmentId + ".jsonl").normalize();
        if (!file.getParent().equals(directory) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    private void append(UUID assessmentId, List<String> lines) {
        Path file = directory.resolve(assessmentId + ".jsonl");
        try {
            Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }
        catch (IOException failure) {
            throw new UncheckedIOException("live-smoke trace file could not be written", failure);
        }
    }

    private String scrub(String line) {
        String scrubbed = line;
        for (String secret : secrets) {
            scrubbed = scrubbed.replace(secret, "[REDACTED]");
        }
        return scrubbed;
    }
}
