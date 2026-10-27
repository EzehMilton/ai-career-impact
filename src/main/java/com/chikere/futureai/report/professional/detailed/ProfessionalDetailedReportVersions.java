package com.chikere.futureai.report.professional.detailed;

/**
 * The detailed Professional report composed from the role-level paid snapshot (professional-report-v2), the
 * responsibility analysis, the guidance and the paid narrative. Every stored v3 snapshot keeps the versions it was
 * assembled with and is never reassembled; v2 snapshots and their PDFs stay readable unchanged.
 */
public final class ProfessionalDetailedReportVersions {

    public static final String REPORT = "professional-report-v3";
    public static final String PDF = "professional-report-pdf-v5";

    private ProfessionalDetailedReportVersions() {
    }
}
