package com.chikere.futureai.pdf.alevel.application;

import com.chikere.futureai.report.alevel.application.ALevelReport;

import java.time.Instant;
import java.util.Objects;

/** Renderer input derived only from an immutable purchased report snapshot. */
public record ALevelReportPdfModel(
        String pdfTemplateVersion,
        String purchasedReportVersion,
        Instant purchasedAt,
        ALevelReport report) {

    public ALevelReportPdfModel {
        pdfTemplateVersion = required(pdfTemplateVersion, "pdfTemplateVersion");
        purchasedReportVersion = required(purchasedReportVersion, "purchasedReportVersion");
        Objects.requireNonNull(purchasedAt);
        Objects.requireNonNull(report);
        if (!purchasedReportVersion.equals(report.reportVersion())) {
            throw new IllegalArgumentException("purchased report versions must match");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value.strip();
    }
}
