package com.chikere.futureai.report.professional.application;

import java.util.Set;

/**
 * The content contract of a Professional report, frozen into each purchased snapshot.
 *
 * <p>The identifier says which composition rules produced a snapshot's content. A purchased snapshot keeps the
 * version it was frozen with and is never rewritten, so every version ever written stays readable.</p>
 */
public final class ProfessionalReportVersions {
    /** The original composition. Still readable for reports purchased before v2. */
    public static final String V1 = "professional-report-v1";
    /**
     * Stage 0 presentation corrections: plain measure descriptions, revised factor sentences, and the paid report no
     * longer presenting confidence, caution or the visitor's own responsibilities as insight. Same JSON shape as v1.
     */
    public static final String V2 = "professional-report-v2";
    /** The version every newly assembled report carries. */
    public static final String CURRENT = V2;
    /** Every version that may be read back from a stored snapshot. */
    public static final Set<String> READABLE = Set.of(V1, V2);

    private ProfessionalReportVersions() { }
}
