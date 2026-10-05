package com.chikere.futureai.cv.professional.application;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfessionalCvUploadServiceTest {
    private static final String RAW = "PRIVATE RAW CV TEXT long enough to pass the minimum extraction threshold. "
            + "It contains contact details and career history that must never be retained in the result.";

    @Test
    void rawBytesAndTextAreTransientAndOnlyStructuredProfileLeavesService() {
        var seen = new AtomicReference<String>();
        ProfessionalCvParser parser = document -> new ProfessionalCvText(RAW, OptionalInt.of(2),
                ProfessionalCvVersions.PARSER_V2);
        ProfessionalProfileExtractor extractor = text -> {
            seen.set(text);
            return profile();
        };
        var service = new ProfessionalCvUploadService(parser, Optional.of(extractor),
                new ProfessionalProfileValidator(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var document = new ProfessionalCvDocument("cv.pdf", ProfessionalCvDocumentType.PDF,
                "%PDF-private-bytes".getBytes());

        var result = service.extractProfile(document);

        assertThat(seen).hasValue(RAW);
        assertThat(result.profile().toString()).doesNotContain(RAW, "PRIVATE RAW CV TEXT", "private-bytes");
        assertThat(result.inputMetadata().toString()).doesNotContain(RAW, "private-bytes");
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
                ProfessionalCvVersions.PARSER_V2), Optional.of(text -> profile()),
                new ProfessionalProfileValidator(), Clock.systemUTC());
        assertThatThrownBy(() -> empty.extractProfile(document()))
                .isInstanceOf(ProfessionalProfileExtractionException.class);
    }

    private ProfessionalProfile profile() {
        return new ProfessionalProfile("Software Architect",
                "Designs dependable software platforms and resolves complex technical trade-offs.",
                List.of("Design platforms"), List.of("Java"), List.of("Communication"),
                List.of(), List.of(), List.of(), OptionalInt.empty(),
                ProfessionalProfileCompleteness.PARTIAL, List.of());
    }

    private ProfessionalCvDocument document() {
        return new ProfessionalCvDocument("cv.pdf", ProfessionalCvDocumentType.PDF, "%PDF-x".getBytes());
    }
}
