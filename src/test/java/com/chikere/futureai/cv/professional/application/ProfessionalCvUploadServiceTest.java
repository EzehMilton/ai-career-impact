package com.chikere.futureai.cv.professional.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ProfessionalCvUploadServiceTest {
    private static final String RAW = "PRIVATE RAW CV TEXT long enough to pass the minimum extraction threshold. "
            + "It contains contact details and career history that must never be retained in the result.";

    @Test
    void rawBytesAndTextAreTransientAndOnlyStructuredProfileLeavesService(CapturedOutput output) {
        var seen = new AtomicReference<String>();
        ProfessionalCvParser parser = document -> new ProfessionalCvText(RAW, OptionalInt.of(2),
                ProfessionalCvVersions.PARSER_V2);
        ProfessionalProfileExtractor extractor = extractor("test-extractor-v9", text -> {
            seen.set(text);
            return profile();
        });
        var service = new ProfessionalCvUploadService(parser, Optional.of(extractor),
                new ProfessionalProfileValidator(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var document = new ProfessionalCvDocument("cv.pdf", ProfessionalCvDocumentType.PDF,
                "%PDF-private-bytes".getBytes());

        var result = service.extractProfile(document);

        assertThat(seen).hasValue(RAW);
        assertThat(result.inputMetadata().extractorVersion()).contains("test-extractor-v9");
        assertThat(result.inputMetadata().parserVersion()).contains(ProfessionalCvVersions.PARSER_V2);
        assertThat(result.profile().toString()).doesNotContain(RAW, "PRIVATE RAW CV TEXT", "private-bytes");
        assertThat(result.inputMetadata().toString()).doesNotContain(RAW, "private-bytes");
        assertThat(output.getAll())
                .contains("Professional CV parsed", "Professional profile extraction started",
                        "Professional profile extraction succeeded")
                .doesNotContain(RAW, "PRIVATE RAW CV TEXT", "Software Architect", "Design platforms");
    }

    @Test
    void unavailableExtractorAndEmptyDocumentFailWithoutGuessing() {
        ProfessionalCvParser parser = document -> new ProfessionalCvText(RAW, OptionalInt.empty(),
                ProfessionalCvVersions.PARSER_V2);
        var service = new ProfessionalCvUploadService(parser, Optional.empty(),
                new ProfessionalProfileValidator(), Clock.systemUTC());
        assertThatThrownBy(() -> service.extractProfile(document()))
                .isInstanceOf(ProfessionalProfileExtractionException.class);

        var empty = new ProfessionalCvUploadService(document -> new ProfessionalCvText("", OptionalInt.empty(),
                ProfessionalCvVersions.PARSER_V2), Optional.of(extractor(text -> profile())),
                new ProfessionalProfileValidator(), Clock.systemUTC());
        assertThatThrownBy(() -> empty.extractProfile(document()))
                .isInstanceOf(ProfessionalProfileExtractionException.class);
    }

    @Test
    void providerFailureAndInvalidProfileAreSurfacedWithoutFixtureFallback(CapturedOutput output) {
        ProfessionalCvParser parser = document -> new ProfessionalCvText(RAW, OptionalInt.of(1),
                ProfessionalCvVersions.PARSER_V2);
        var failed = new ProfessionalCvUploadService(parser,
                Optional.of(extractor(text -> {
                    throw new ProfessionalProfileExtractionException("private provider detail");
                })), new ProfessionalProfileValidator(), Clock.systemUTC());

        assertThatThrownBy(() -> failed.extractProfile(document()))
                .isInstanceOf(ProfessionalProfileExtractionException.class)
                .hasMessage("private provider detail");

        ProfessionalProfile invalid = new ProfessionalProfile("", "", "", "", "Too short", List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), OptionalInt.empty(),
                List.of(), ProfessionalProfileCompleteness.PARTIAL, List.of());
        var malformed = new ProfessionalCvUploadService(parser, Optional.of(extractor(text -> invalid)),
                new ProfessionalProfileValidator(), Clock.systemUTC());

        assertThatThrownBy(() -> malformed.extractProfile(document()))
                .isInstanceOf(ProfessionalProfileExtractionException.class)
                .hasMessageContaining("enough reliable career information");
        assertThat(output.getAll())
                .contains("Professional profile extraction failed",
                        "Professional profile extraction rejected by validation")
                .doesNotContain("private provider detail", RAW);
    }

    private static ProfessionalProfileExtractor extractor(Function<String, ProfessionalProfile> extraction) {
        return extractor("test-extractor", extraction);
    }

    private static ProfessionalProfileExtractor extractor(String version,
            Function<String, ProfessionalProfile> extraction) {
        return new ProfessionalProfileExtractor() {
            @Override
            public ProfessionalProfile extract(String cvText) {
                return extraction.apply(cvText);
            }

            @Override
            public String version() {
                return version;
            }
        };
    }

    private ProfessionalProfile profile() {
        return new ProfessionalProfile("Software Architect", "", "", "",
                "Designs dependable software platforms and resolves complex technical trade-offs.",
                List.of("Design platforms"), List.of("Java"), List.of("Communication"),
                List.of(), List.of(), List.of(), OptionalInt.empty(),
                List.of(), ProfessionalProfileCompleteness.PARTIAL, List.of());
    }

    private ProfessionalCvDocument document() {
        return new ProfessionalCvDocument("cv.pdf", ProfessionalCvDocumentType.PDF, "%PDF-x".getBytes());
    }
}
