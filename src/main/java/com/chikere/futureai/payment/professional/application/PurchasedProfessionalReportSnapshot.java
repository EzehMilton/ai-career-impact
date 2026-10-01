package com.chikere.futureai.payment.professional.application;

import com.chikere.futureai.report.professional.application.ProfessionalReport;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PurchasedProfessionalReportSnapshot(
        UUID entitlementId,
        UUID assessmentId,
        String reportVersion,
        Instant frozenAt,
        ProfessionalReport report) {

    public PurchasedProfessionalReportSnapshot {
        Objects.requireNonNull(entitlementId); Objects.requireNonNull(assessmentId);
        if (reportVersion == null || reportVersion.isBlank()) throw new IllegalArgumentException("reportVersion must not be blank");
        reportVersion = reportVersion.strip();
        Objects.requireNonNull(frozenAt); Objects.requireNonNull(report);
        if (report.tier() != ProfessionalReportTier.PAID) throw new IllegalArgumentException("purchased snapshot must be PAID");
        if (!reportVersion.equals(report.reportVersion())) throw new IllegalArgumentException("report versions must match");
    }
}
