package com.chikere.futureai.payment.alevel.application;

import com.chikere.futureai.report.alevel.application.ALevelReport;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The exact paid report frozen at payment confirmation.
 *
 * <p>A later {@code a-level-report-v2} must never change what an {@code a-level-report-v1}
 * customer bought.</p>
 */
public record PurchasedALevelReportSnapshot(
        UUID entitlementId,
        UUID assessmentId,
        String reportVersion,
        Instant frozenAt,
        ALevelReport report) {

    public PurchasedALevelReportSnapshot {
        Objects.requireNonNull(entitlementId);
        Objects.requireNonNull(assessmentId);
        if (reportVersion == null || reportVersion.isBlank()) {
            throw new IllegalArgumentException("reportVersion must not be blank");
        }
        reportVersion = reportVersion.strip();
        Objects.requireNonNull(frozenAt);
        Objects.requireNonNull(report);
        if (report.tier() != ALevelReportTier.PAID) {
            throw new IllegalArgumentException("purchased snapshot must be PAID");
        }
        if (!reportVersion.equals(report.reportVersion())) {
            throw new IllegalArgumentException("report versions must match");
        }
    }
}
