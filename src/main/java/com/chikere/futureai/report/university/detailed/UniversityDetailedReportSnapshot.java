package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One immutable {@code university-report-v2}: every version and source artefact it was assembled from, and the
 * composed report. Stored once, with an AVAILABLE or final FALLBACK narrative only, and never regenerated: a later
 * model, prompt or policy change never alters it.
 */
public record UniversityDetailedReportSnapshot(
        UUID reportId,
        UUID assessmentId,
        UUID entitlementId,
        String decisionPolicyVersion,
        UUID moduleAnalysisId,
        String moduleSchemaVersion,
        String modulePolicyVersion,
        String guidancePolicyVersion,
        String paidNarrativeVersion,
        UniversityPaidNarrativeStatus paidNarrativeStatus,
        String reportVersion,
        String pdfVersion,
        Instant generatedAt,
        UniversityDetailedReport report) {

    public UniversityDetailedReportSnapshot {
        Objects.requireNonNull(reportId);
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(entitlementId);
        Objects.requireNonNull(decisionPolicyVersion);
        Objects.requireNonNull(moduleAnalysisId);
        Objects.requireNonNull(moduleSchemaVersion);
        Objects.requireNonNull(modulePolicyVersion);
        Objects.requireNonNull(guidancePolicyVersion);
        Objects.requireNonNull(paidNarrativeVersion);
        Objects.requireNonNull(paidNarrativeStatus);
        if (paidNarrativeStatus == UniversityPaidNarrativeStatus.RETRYABLE_FALLBACK) {
            throw new IllegalArgumentException("a retryable narrative is never frozen into a report");
        }
        Objects.requireNonNull(reportVersion);
        Objects.requireNonNull(pdfVersion);
        Objects.requireNonNull(generatedAt);
        Objects.requireNonNull(report);
        if (!reportVersion.equals(report.reportVersion())) {
            throw new IllegalArgumentException("report versions must match");
        }
    }
}
