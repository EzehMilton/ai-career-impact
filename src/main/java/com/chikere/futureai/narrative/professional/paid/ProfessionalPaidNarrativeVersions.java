package com.chikere.futureai.narrative.professional.paid;

/**
 * The prompt, schema and validation contract of the paid Professional narrative. A stored paid narrative keeps the
 * version it was generated with; a later version is never applied to an existing report.
 *
 * <p>{@value #V1} explained the deterministic findings only. {@value #V2} keeps those findings final and adds bounded,
 * illustrative practical interpretation: concrete uses, human ownership, a first experiment, role-specific skill
 * manifestations and an adaptation strategy, all keyed to the deterministic ids.</p>
 */
public final class ProfessionalPaidNarrativeVersions {

    public static final String V1 = "professional-paid-narrative-v1";
    public static final String V2 = "professional-paid-narrative-v2";
    public static final String CURRENT = V2;

    private ProfessionalPaidNarrativeVersions() {
    }
}
