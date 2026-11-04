package com.chikere.futureai.report.university.detailed;

import java.util.Set;

/** Renders one stored detailed report as PDF bytes. Deterministic: the same snapshot, the same content. */
public interface UniversityDetailedReportPdfRenderer {

    /** The PDF layout version this renderer produces for new reports. */
    String pdfVersion();

    /** Every PDF layout version this renderer can still produce exactly, so stored reports keep their own layout. */
    default Set<String> pdfVersions() {
        return Set.of(pdfVersion());
    }

    byte[] render(UniversityDetailedReport report);
}
