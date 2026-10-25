package com.chikere.futureai.judgment.capture.professional;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Development-only decorator that writes what the professional judgment provider was asked and
 * what it answered to one JSON file per successful call. It never changes a result: a failed judge()
 * call writes nothing, and a failed write is reported only by its exception type, never its content.
 */
final class CapturingProfessionalJudgmentProvider implements ProfessionalJudgmentProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(CapturingProfessionalJudgmentProvider.class);
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private final ProfessionalJudgmentProvider delegate;
    private final ProfessionalJevCapture capture;
    private final Path directory;
    private final Clock clock;

    CapturingProfessionalJudgmentProvider(ProfessionalJudgmentProvider delegate, ProfessionalJevCapture capture,
            Path directory, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate);
        this.capture = Objects.requireNonNull(capture);
        this.directory = Objects.requireNonNull(directory);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public AssessmentJudgmentSet judge(ProfessionalAssessmentState state) {
        AssessmentJudgmentSet judgments = delegate.judge(state);
        write(state, judgments);
        return judgments;
    }

    @Override
    public AssessmentSource source() {
        return delegate.source();
    }

    @Override
    public Optional<String> modelIdentifier() {
        return delegate.modelIdentifier();
    }

    @Override
    public Optional<String> fixtureId() {
        return delegate.fixtureId();
    }

    ProfessionalJudgmentProvider delegate() {
        return delegate;
    }

    private void write(ProfessionalAssessmentState state, AssessmentJudgmentSet judgments) {
        Path temporary = null;
        try {
            Instant now = clock.instant();
            var document = capture.document(delegate.source(), now, state, judgments, delegate.modelIdentifier());
            Files.createDirectories(directory);
            Path target = directory.resolve(FILE_TIMESTAMP.format(now) + "-"
                    + UUID.randomUUID().toString().substring(0, 8) + ".json");
            // Written beside the target and moved into place, so a failed write leaves no partial file.
            temporary = Files.createTempFile(directory, ".jev-capture-", ".tmp");
            Files.write(temporary, JSON.writeValueAsBytes(document));
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (Exception exception) {
            // Capture is a development aid: it must never fail or alter an assessment. Only the exception
            // type is logged; its message could contain the path or content.
            deleteQuietly(temporary);
            LOGGER.warn("Professional Jev capture write failed failureType={}", exception.getClass().getSimpleName());
        }
    }

    private static void deleteQuietly(Path path) {
        if (path == null) return;
        try {
            Files.deleteIfExists(path);
        }
        catch (Exception ignored) {
            // Nothing more can be done without recording the failure.
        }
    }
}
