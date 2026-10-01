package com.chikere.futureai.payment.university.application;

import com.chikere.futureai.report.university.application.UniversityReport;
import com.chikere.futureai.report.university.application.UniversityReportTier;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The exact paid report frozen at payment confirmation.
 *
 * <p>A later {@code university-report-v2} must never change what a {@code university-report-v1}
 * customer bought.</p>
 */
public record PurchasedUniversityReportSnapshot(
        UUID entitlementId,
        UUID assessmentId,
        String reportVersion,
        Instant frozenAt,
        UniversityReport report) {

    public PurchasedUniversityReportSnapshot {
        Objects.requireNonNull(entitlementId);
        Objects.requireNonNull(assessmentId);
        if (reportVersion == null || reportVersion.isBlank()) {
            throw new IllegalArgumentException("reportVersion must not be blank");
        }
        reportVersion = reportVersion.strip();
        Objects.requireNonNull(frozenAt);
        Objects.requireNonNull(report);
        if (report.tier() != UniversityReportTier.PAID) {
            throw new IllegalArgumentException("purchased snapshot must be PAID");
        }
        if (!reportVersion.equals(report.reportVersion())) {
            throw new IllegalArgumentException("report versions must match");
        }
    }
}
