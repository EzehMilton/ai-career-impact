package com.chikere.futureai.report.alevel.detailed;

import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One immutable versioned A-level report: every version it was assembled from, and the composed report. Stored once,
 * with an AVAILABLE or final FALLBACK narrative only, and never regenerated: a later model, prompt, guidance or
 * decision-policy change never alters it.
 *
 * @param evidenceVersion the version of the evidence bundle shown in the report, if any
 */
public record ALevelDetailedReportSnapshot(
        UUID reportId,
        UUID assessmentId,
        UUID entitlementId,
        String judgmentSchemaVersion,
        String decisionPolicyVersion,
        String guidancePolicyVersion,
        String paidNarrativeVersion,
        ALevelPaidNarrativeStatus paidNarrativeStatus,
        Optional<String> evidenceVersion,
        String reportVersion,
        String pdfVersion,
        Instant generatedAt,
        ALevelDetailedReport report) {

    public ALevelDetailedReportSnapshot {
        Objects.requireNonNull(reportId);
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(entitlementId);
        Objects.requireNonNull(judgmentSchemaVersion);
        Objects.requireNonNull(decisionPolicyVersion);
        Objects.requireNonNull(guidancePolicyVersion);
        Objects.requireNonNull(paidNarrativeVersion);
        Objects.requireNonNull(paidNarrativeStatus);
        if (paidNarrativeStatus == ALevelPaidNarrativeStatus.RETRYABLE_FALLBACK) {
            throw new IllegalArgumentException("a retryable narrative is never frozen into a report");
        }
        Objects.requireNonNull(evidenceVersion);
        Objects.requireNonNull(reportVersion);
        Objects.requireNonNull(pdfVersion);
        Objects.requireNonNull(generatedAt);
        Objects.requireNonNull(report);
        if (!reportVersion.equals(report.reportVersion())) {
            throw new IllegalArgumentException("report versions must match");
        }
    }
}
