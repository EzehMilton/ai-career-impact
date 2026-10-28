package com.chikere.futureai.report.university.detailed;

/** Renders one stored {@code university-report-v2} as PDF bytes. Deterministic: the same snapshot, the same content. */
public interface UniversityDetailedReportPdfRenderer {

    /** The PDF layout version this renderer produces. */
    String pdfVersion();

    byte[] render(UniversityDetailedReport report);
}
