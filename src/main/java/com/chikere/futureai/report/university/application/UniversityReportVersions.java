package com.chikere.futureai.report.university.application;

public final class UniversityReportVersions {
    public static final String V1 = "university-report-v1";
    /**
     * The course-profile product of a university-decision-v2 assessment, and the purchase version that buys it. It is
     * also the version of the first stored detailed report document, kept for reports stored before {@link #V3}.
     */
    public static final String V2 = "university-report-v2";
    public static final String PDF_V2 = "university-report-pdf-v2";
    /**
     * The detailed report document assembled for a v2 purchase since university-paid-narrative-v2: module-specific
     * learning, independent capability, skill interpretation, plan notes and an AI-study strategy. A v2 purchase
     * stays a v2 purchase; only the stored document is v3.
     */
    public static final String V3 = "university-report-v3";
    public static final String PDF_V3 = "university-report-pdf-v3";

    private UniversityReportVersions() { }

    /** The page template of a stored detailed report: a v2 document keeps its own page. */
    public static String detailedTemplate(String reportVersion) {
        return V2.equals(reportVersion) ? "university-assessment/paid-report-v2" : "university-assessment/paid-report-v3";
    }
}
