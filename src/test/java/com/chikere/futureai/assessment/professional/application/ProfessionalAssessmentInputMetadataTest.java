package com.chikere.futureai.assessment.professional.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfessionalAssessmentInputMetadataTest {
    private static final Instant PARSED = Instant.parse("2026-10-15T12:00:00Z");

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
    void extractorVersionMustNotBeNull() {
        assertThatThrownBy(() -> new ProfessionalAssessmentInputMetadata(ProfessionalInputSource.MANUAL,
                Optional.empty(), Optional.empty(), Optional.empty(), null, Optional.empty()))
                .isInstanceOf(NullPointerException.class);
    }
}
