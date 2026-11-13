package com.chikere.futureai.report.alevel.application;

public final class ALevelReportVersions {
    /** The historical paid report of an a-level-decision-v1 assessment, frozen at purchase. */
    public static final String V1 = "a-level-report-v1";
    /** The paid detailed report of an a-level-decision-v2 subject profile, stored once when prepared. */
    public static final String V2 = "a-level-report-v2";
    /** The PDF layout of a stored a-level-report-v2, rendered on demand from that snapshot. */
    public static final String PDF_V2 = "a-level-report-pdf-v2";
    /** Plain-language presentation for newly generated detailed reports; assessment semantics remain v2. */
    public static final String V3 = "a-level-report-v3";
    /** The unchanged layout applied to a stored v3 report's new customer wording. */
    public static final String PDF_V3 = "a-level-report-pdf-v3";

    private ALevelReportVersions() { }
}
