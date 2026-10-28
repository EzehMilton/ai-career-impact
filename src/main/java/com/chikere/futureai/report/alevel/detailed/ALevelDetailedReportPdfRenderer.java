package com.chikere.futureai.report.alevel.detailed;

/** Renders one stored {@code a-level-report-v2} as PDF bytes. Deterministic: the same snapshot, the same content. */
public interface ALevelDetailedReportPdfRenderer {

    /** The PDF layout version this renderer produces. */
    String pdfVersion();

    byte[] render(ALevelDetailedReport report);
}
