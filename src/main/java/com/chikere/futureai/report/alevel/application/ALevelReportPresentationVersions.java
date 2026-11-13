package com.chikere.futureai.report.alevel.application;

import java.time.Instant;
import java.util.Objects;

/**
 * Selects only the dynamically composed free-report wording. The immutable cutover uses the assessment's stored
 * creation time, so revisiting an assessment cannot change its presentation after a deployment or configuration
 * change. It is independent of judgment provider, schema and decision-policy versions.
 */
public final class ALevelReportPresentationVersions {

    public static final Instant V3_CUTOVER = Instant.parse("2026-11-13T00:00:00Z");

    private ALevelReportPresentationVersions() { }

    public static String forAssessmentCreatedAt(Instant createdAt) {
        return Objects.requireNonNull(createdAt, "createdAt must not be null").isBefore(V3_CUTOVER)
                ? ALevelReportVersions.V2 : ALevelReportVersions.V3;
    }
}
