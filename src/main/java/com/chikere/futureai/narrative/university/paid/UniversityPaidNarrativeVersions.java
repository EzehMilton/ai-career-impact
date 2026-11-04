package com.chikere.futureai.narrative.university.paid;

/** The prompt, schema and validator contract of the University paid narrative. */
public final class UniversityPaidNarrativeVersions {

    /** Five short perspectives. Lives on only inside stored university-report-v2 documents. */
    public static final String V1 = "university-paid-narrative-v1";
    /** Module-specific learning, independent capability, skill interpretation, plan notes and an AI-study strategy. */
    public static final String V2 = "university-paid-narrative-v2";
    public static final String CURRENT = V2;

    private UniversityPaidNarrativeVersions() {
    }
}
