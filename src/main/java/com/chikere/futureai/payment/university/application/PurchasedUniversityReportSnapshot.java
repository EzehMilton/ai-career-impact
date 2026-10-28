package com.chikere.futureai.payment.university.application;

import com.chikere.futureai.report.university.application.UniversityReport;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import com.chikere.futureai.report.university.application.UniversityReportVersions;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * What a University purchase froze at payment confirmation.
 *
 * <p>A {@code university-report-v1} purchase (a historical v1 assessment) freezes the exact paid report document.
 * A {@code university-report-v2} purchase (a course-profile assessment) freezes no document here: its course profile
 * is already immutable in the assessment, and its detailed report is generated once, later, on the customer's
 * explicit request, and stored as its own immutable snapshot. A later report version must never change what a
 * {@code university-report-v1} customer bought.</p>
 */
public record PurchasedUniversityReportSnapshot(
        UUID entitlementId,
        UUID assessmentId,
        String reportVersion,
        Instant frozenAt,
        Optional<UniversityReport> purchasedReport) {

    public PurchasedUniversityReportSnapshot {
        Objects.requireNonNull(entitlementId);
        Objects.requireNonNull(assessmentId);
        if (reportVersion == null || reportVersion.isBlank()) {
            throw new IllegalArgumentException("reportVersion must not be blank");
        }
        reportVersion = reportVersion.strip();
        Objects.requireNonNull(frozenAt);
        Objects.requireNonNull(purchasedReport);
        if (UniversityReportVersions.V2.equals(reportVersion)) {
            if (purchasedReport.isPresent()) {
                throw new IllegalArgumentException("a university-report-v2 purchase freezes no v1 document");
            }
        }
        else {
            if (purchasedReport.isEmpty()) {
                throw new IllegalArgumentException("a " + reportVersion + " purchase must freeze its report");
            }
            UniversityReport report = purchasedReport.get();
            if (report.tier() != UniversityReportTier.PAID) {
                throw new IllegalArgumentException("purchased snapshot must be PAID");
            }
            if (!reportVersion.equals(report.reportVersion())) {
                throw new IllegalArgumentException("report versions must match");
            }
        }
    }

    /** A historical {@code university-report-v1} purchase with its frozen document. */
    public PurchasedUniversityReportSnapshot(UUID entitlementId, UUID assessmentId, String reportVersion,
            Instant frozenAt, UniversityReport report) {
        this(entitlementId, assessmentId, reportVersion, frozenAt, Optional.of(Objects.requireNonNull(report)));
    }

    /** A {@code university-report-v2} purchase of a course-profile assessment. */
    public static PurchasedUniversityReportSnapshot courseProfile(UUID entitlementId, UUID assessmentId,
            Instant frozenAt) {
        return new PurchasedUniversityReportSnapshot(entitlementId, assessmentId, UniversityReportVersions.V2,
                frozenAt, Optional.empty());
    }

    /** The frozen v1 document. Only a {@code university-report-v1} purchase has one. */
    public UniversityReport report() {
        return purchasedReport.orElseThrow(() -> new IllegalStateException(
                "a " + reportVersion + " purchase has no frozen v1 report document"));
    }

    public boolean isCourseProfilePurchase() {
        return UniversityReportVersions.V2.equals(reportVersion);
    }
}
