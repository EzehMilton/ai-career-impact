package com.chikere.futureai.pdf.alevel.application;

/** Vendor-neutral rendering port. Implementations must make no network call. */
public interface ALevelReportPdfRenderer {
    RenderedALevelReportPdf render(ALevelReportPdfModel model);
}
