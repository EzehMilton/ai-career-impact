package com.chikere.futureai.payment.alevel.application;

import com.chikere.futureai.report.alevel.application.ALevelReport;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import com.chikere.futureai.report.alevel.application.ALevelReportVersions;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What an A-level purchase froze at payment confirmation.
 *
 * <p>A {@code a-level-report-v1} purchase (a historical v1 assessment) freezes the exact paid report document.
 * A {@code a-level-report-v2} purchase (a subject-profile assessment) freezes no document here: its subject profile
 * is already immutable in the assessment, and its detailed report is generated once, later, on the customer's
 * explicit request, and stored as its own immutable snapshot. A later report version must never change what a
 * {@code a-level-report-v1} customer bought.</p>
 */
public record PurchasedALevelReportSnapshot(
        UUID entitlementId,
        UUID assessmentId,
        String reportVersion,
        Instant frozenAt,
        Optional<ALevelReport> purchasedReport) {

    public PurchasedALevelReportSnapshot {
        Objects.requireNonNull(entitlementId);
        Objects.requireNonNull(assessmentId);
        if (reportVersion == null || reportVersion.isBlank()) {
            throw new IllegalArgumentException("reportVersion must not be blank");
        }
        reportVersion = reportVersion.strip();
        Objects.requireNonNull(frozenAt);
        Objects.requireNonNull(purchasedReport);
        if (ALevelReportVersions.V2.equals(reportVersion)) {
            if (purchasedReport.isPresent()) {
                throw new IllegalArgumentException("a a-level-report-v2 purchase freezes no v1 document");
            }
        }
        else {
            if (purchasedReport.isEmpty()) {
                throw new IllegalArgumentException("a " + reportVersion + " purchase must freeze its report");
            }
            ALevelReport report = purchasedReport.get();
            if (report.tier() != ALevelReportTier.PAID) {
                throw new IllegalArgumentException("purchased snapshot must be PAID");
            }
            if (!reportVersion.equals(report.reportVersion())) {
                throw new IllegalArgumentException("report versions must match");
            }
        }
    }

    /** A historical {@code a-level-report-v1} purchase with its frozen document. */
    public PurchasedALevelReportSnapshot(UUID entitlementId, UUID assessmentId, String reportVersion,
            Instant frozenAt, ALevelReport report) {
        this(entitlementId, assessmentId, reportVersion, frozenAt, Optional.of(Objects.requireNonNull(report)));
    }

    /** A {@code a-level-report-v2} purchase of a subject-profile assessment. */
    public static PurchasedALevelReportSnapshot subjectProfile(UUID entitlementId, UUID assessmentId,
            Instant frozenAt) {
        return new PurchasedALevelReportSnapshot(entitlementId, assessmentId, ALevelReportVersions.V2,
                frozenAt, Optional.empty());
    }

    /** The frozen v1 document. Only a {@code a-level-report-v1} purchase has one. */
    public ALevelReport report() {
        return purchasedReport.orElseThrow(() -> new IllegalStateException(
                "a " + reportVersion + " purchase has no frozen v1 report document"));
    }

    public boolean isSubjectProfilePurchase() {
        return ALevelReportVersions.V2.equals(reportVersion);
    }
}
