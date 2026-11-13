package com.chikere.futureai.report.alevel.application;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ALevelReportPresentationVersionsTest {

    private static final Instant CUTOVER = Instant.parse("2026-11-20T09:30:00Z");
    private final ALevelReportPresentationVersions versions =
            new ALevelReportPresentationVersions(CUTOVER.toString());

    @Test
    void selectsV2ImmediatelyBeforeTheConfiguredReleaseBoundary() {
        assertThat(versions.forAssessmentCreatedAt(CUTOVER.minusNanos(1)))
                .isEqualTo(ALevelReportVersions.V2);
    }

    @Test
    void selectsV3AtAndAfterTheConfiguredReleaseBoundary() {
        assertThat(versions.forAssessmentCreatedAt(CUTOVER))
                .isEqualTo(ALevelReportVersions.V3);
        assertThat(versions.forAssessmentCreatedAt(CUTOVER.plusNanos(1)))
                .isEqualTo(ALevelReportVersions.V3);
    }

    @Test
    void rejectsAMalformedCutoverDuringConstruction() {
        assertThatThrownBy(() -> new ALevelReportPresentationVersions("13 November sometime"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must be a UTC ISO-8601 instant");
    }
}
