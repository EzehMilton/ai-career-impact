package com.chikere.futureai.report.alevel.application;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Objects;

/**
 * Selects only the dynamically composed free-report wording. The immutable cutover uses the assessment's stored
 * creation time and a deliberately fixed release boundary, so revisiting an assessment remains stable across the
 * deployment. It is independent of judgment provider, schema and decision-policy versions.
 */
public final class ALevelReportPresentationVersions {

    private final Instant v3Cutover;

    public ALevelReportPresentationVersions(String configuredV3Cutover) {
        Objects.requireNonNull(configuredV3Cutover, "configuredV3Cutover must not be null");
        try {
            this.v3Cutover = Instant.parse(configuredV3Cutover);
        }
        catch (DateTimeParseException invalidCutover) {
            throw new IllegalArgumentException("app.a-level.presentation-v3-cutover must be a UTC ISO-8601 instant",
                    invalidCutover);
        }
    }

    public String forAssessmentCreatedAt(Instant createdAt) {
        return Objects.requireNonNull(createdAt, "createdAt must not be null").isBefore(v3Cutover)
                ? ALevelReportVersions.V2 : ALevelReportVersions.V3;
    }

    public Instant v3Cutover() {
        return v3Cutover;
    }
}
