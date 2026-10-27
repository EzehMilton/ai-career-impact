package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One immutable assembled {@value ProfessionalDetailedReportVersions#REPORT} for an assessment, with references to
 * every artefact it was assembled from. Reproducible from its own content; never reassembled under later code.
 */
public record ProfessionalDetailedReportSnapshot(
        UUID reportId,
        UUID assessmentId,
        UUID entitlementId,
        UUID analysisId,
        UUID guidanceId,
        UUID paidNarrativeId,
        PaidNarrativeStatus paidNarrativeStatus,
        String reportVersion,
        String pdfVersion,
        Instant generatedAt,
        ProfessionalDetailedReport report) {

    public ProfessionalDetailedReportSnapshot {
        Objects.requireNonNull(reportId);
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(entitlementId);
        Objects.requireNonNull(analysisId);
        Objects.requireNonNull(guidanceId);
        Objects.requireNonNull(paidNarrativeId);
        Objects.requireNonNull(paidNarrativeStatus);
        Objects.requireNonNull(reportVersion);
        Objects.requireNonNull(pdfVersion);
        Objects.requireNonNull(generatedAt);
        Objects.requireNonNull(report);
        if (paidNarrativeStatus == PaidNarrativeStatus.RETRYABLE_FALLBACK) {
            throw new IllegalArgumentException("a report whose narrative is still retryable is provisional, never stored");
        }
        if (!reportVersion.equals(report.reportVersion()) || paidNarrativeStatus != report.narrativeStatus()) {
            throw new IllegalArgumentException("the snapshot's versions and narrative status must match its report");
        }
    }
}
