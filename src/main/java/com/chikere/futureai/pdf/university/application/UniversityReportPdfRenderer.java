package com.chikere.futureai.pdf.university.application;

/** Vendor-neutral rendering port. Implementations must make no network call. */
public interface UniversityReportPdfRenderer {
    RenderedUniversityReportPdf render(UniversityReportPdfModel model);
}
