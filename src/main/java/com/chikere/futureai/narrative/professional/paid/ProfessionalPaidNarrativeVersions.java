package com.chikere.futureai.narrative.professional.paid;

/**
 * The prompt, schema and validation contract of the paid Professional narrative. A stored paid narrative keeps the
 * version it was generated with; a later version is never applied to an existing report.
 */
public final class ProfessionalPaidNarrativeVersions {

    public static final String V1 = "professional-paid-narrative-v1";
    public static final String CURRENT = V1;

    private ProfessionalPaidNarrativeVersions() {
    }
}
