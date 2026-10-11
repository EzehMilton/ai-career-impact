package com.chikere.futureai.assessment.professional.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfessionalAssessmentInputMetadataTest {
    private static final Instant PARSED = Instant.parse("2026-10-15T12:00:00Z");

    @Test
    void cvUploadCarriesTheExtractorVersionNextToTheParserVersion() {
        var metadata = ProfessionalAssessmentInputMetadata.cvUpload("cv.pdf", "application/pdf",
                "professional-cv-parser-v2", "professional-cv-extractor-v2", PARSED);

        assertThat(metadata.source()).isEqualTo(ProfessionalInputSource.CV_UPLOAD);
        assertThat(metadata.parserVersion()).contains("professional-cv-parser-v2");
        assertThat(metadata.extractorVersion()).contains("professional-cv-extractor-v2");
    }

    @Test
    void manualInputHasNoExtractorVersion() {
        assertThat(ProfessionalAssessmentInputMetadata.manual().extractorVersion()).isEmpty();
    }

    @Test
    void manualInputWithAnExtractorVersionIsRejected() {
        assertThatThrownBy(() -> new ProfessionalAssessmentInputMetadata(ProfessionalInputSource.MANUAL,
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("professional-cv-extractor-v2"),
                Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("manual input must not contain CV metadata");
    }

    @Test
    void cvUploadWithoutAnExtractorVersionIsRejected() {
        assertThatThrownBy(() -> new ProfessionalAssessmentInputMetadata(ProfessionalInputSource.CV_UPLOAD,
                Optional.of("cv.pdf"), Optional.of("application/pdf"), Optional.of("professional-cv-parser-v2"),
                Optional.empty(), Optional.of(PARSED)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CV upload metadata must be complete");
    }

    @Test
    void extractorVersionMustNotBeNull() {
        assertThatThrownBy(() -> new ProfessionalAssessmentInputMetadata(ProfessionalInputSource.MANUAL,
                Optional.empty(), Optional.empty(), Optional.empty(), null, Optional.empty()))
                .isInstanceOf(NullPointerException.class);
    }
}
