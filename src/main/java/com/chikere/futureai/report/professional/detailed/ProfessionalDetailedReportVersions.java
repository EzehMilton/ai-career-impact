package com.chikere.futureai.report.professional.detailed;

/**
 * The detailed Professional report composed from the role-level paid snapshot (professional-report-v2), the
 * responsibility analysis, the guidance and the paid narrative. Every stored snapshot keeps the versions it was
 * assembled with and is never reassembled: a stored {@value #REPORT_V3} is always shown with its own template and
 * rendered as {@value #PDF_V5}; only newly prepared reports are {@value #REPORT_V4} with {@value #PDF_V6}.
 *
 * <p>{@value #REPORT_V4} adds the practical layer of professional-paid-narrative-v2 (concrete uses, human ownership,
 * first experiments, role-specific skill manifestations and the adaptation strategy) and shows universal safeguards
 * once, in Responsible AI use, instead of on every opportunity.</p>
 */
public final class ProfessionalDetailedReportVersions {

    public static final String REPORT_V3 = "professional-report-v3";
    public static final String PDF_V5 = "professional-report-pdf-v5";
    public static final String REPORT_V4 = "professional-report-v4";
    public static final String PDF_V6 = "professional-report-pdf-v6";

    public static final String REPORT = REPORT_V4;
    public static final String PDF = PDF_V6;

    private ProfessionalDetailedReportVersions() {
    }

    /** The page template a stored report version is shown with; a v3 report keeps its original page. */
    public static String template(String reportVersion) {
        return REPORT_V3.equals(reportVersion) ? "professional-assessment/paid-report-v3"
                : "professional-assessment/paid-report-v4";
    }
}
