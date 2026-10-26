package com.chikere.futureai.pdf.professional.application;

public final class ProfessionalReportPdfVersions {
    /**
     * The PDF presentation template used for newly rendered PDFs. v2: the Stage 0 presentation (no confidence or
     * caution, explanations once, plain guidance and career-context wording, no technical methodology block). A stored
     * PDF keeps the template version it was rendered with and is never regenerated, so v1 PDFs stay as they were.
     */
    public static final String PDF_TEMPLATE = "professional-report-pdf-v2";

    private ProfessionalReportPdfVersions() { }
}
