package com.chikere.futureai.pdf.professional.application;

public final class ProfessionalReportPdfVersions {
    /**
     * The PDF presentation template used for newly rendered PDFs. v2: the Stage 0 presentation (no confidence or
     * caution, explanations once, plain guidance and career-context wording, no technical methodology block). v3: an
     * empty factor list (possible once only material factors are selected) renders an explanatory sentence instead of
     * an empty heading and list. v4: scores use ProfessionalScoreDisplayFormatter, so a score just below a band
     * boundary never displays as the boundary itself (a Moderate 6.99625 reads 6.99, not 7.00). A stored PDF keeps the template version it was rendered with and is never
     * regenerated, so v1, v2 and v3 PDFs stay as they were.
     */
    public static final String PDF_TEMPLATE = "professional-report-pdf-v4";

    private ProfessionalReportPdfVersions() { }
}
