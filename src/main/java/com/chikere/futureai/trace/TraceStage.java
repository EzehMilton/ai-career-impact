package com.chikere.futureai.trace;

/** The named points of an assessment trace, roughly in the order a submission passes through them. */
public enum TraceStage {
    START,
    FORM_INPUT,
    DOMAIN_INPUT,
    FORM_MAPPING_FAILED,
    DECISION_MODEL_REQUEST,
    DECISION_MODEL_PROVIDER_REQUEST,
    DECISION_MODEL_PROVIDER_RESPONSE,
    DECISION_MODEL_RESPONSE,
    INFORMATION_SUFFICIENCY,
    ASSESSMENT_CONFIDENCE,
    SCORE_CALCULATION,
    FACTOR_SELECTION,
    DETERMINISTIC_ASSESSMENT,
    PERSISTENCE,
    NARRATIVE_REQUEST,
    NARRATIVE_PROVIDER_REQUEST,
    NARRATIVE_PROVIDER_RESPONSE,
    NARRATIVE_RESPONSE,
    NARRATIVE_VALIDATION,
    NARRATIVE_REPAIR_REQUEST,
    NARRATIVE_OUTCOME,
    OCCUPATION_CLASSIFICATION,
    EVIDENCE_REQUEST,
    EVIDENCE_RESPONSE,
    SUMMARY,
    FREE_ASSESSMENT_COMPLETE,
    DETAILED_REPORT_COMPLETE,
    REPORT_SUMMARY,
    /** Responsibility analysis for the paid Professional report: the selected responsibilities and schema. */
    RESPONSIBILITY_ANALYSIS_REQUEST,
    RESPONSIBILITY_MODEL_PROVIDER_REQUEST,
    RESPONSIBILITY_MODEL_PROVIDER_RESPONSE,
    /** The validated, vendor-neutral responsibility judgments, or why they were rejected. */
    RESPONSIBILITY_ANALYSIS_RESULT,
    /** The deterministic category, oversight marker and levels derived for each analysed responsibility. */
    RESPONSIBILITY_CLASSIFICATION,
    /** A completed responsibility-analysis snapshot already existed and was returned; no provider was called. */
    RESPONSIBILITY_ANALYSIS_REUSED,
    RESPONSIBILITY_ANALYSIS_PERSISTED,
    /** Generation failed at a named stage; no completed snapshot was stored and a retry remains possible. */
    RESPONSIBILITY_ANALYSIS_FAILED,
    /** Deterministic paid guidance generated from a stored analysis, or an existing guidance snapshot reused. */
    RESPONSIBILITY_GUIDANCE,
    /** The paid-narrative request: versions, identifiers and the compact provider view of the frozen findings. */
    PAID_NARRATIVE_REQUEST,
    /** One paid-narrative provider response (attempt 1, or 2 for the repair). */
    PAID_NARRATIVE_RESPONSE,
    /** Each paid-narrative validation rule's outcome. */
    PAID_NARRATIVE_VALIDATION,
    /** The single repair request after a draft failed validation. */
    PAID_NARRATIVE_REPAIR,
    /** No narrative is shown: why, and that the report uses deterministic copy. */
    PAID_NARRATIVE_FALLBACK,
    /** A stored paid narrative was returned; no provider was called. */
    PAID_NARRATIVE_REUSED,
    /** A new immutable professional-report-v3 snapshot was assembled from stored artefacts and persisted. */
    PROFESSIONAL_REPORT_V3_GENERATED,
    /** University module analysis requested: entitlement accepted and the selected module ids. */
    UNIVERSITY_MODULE_ANALYSIS_REQUEST,
    /** University module analysis refused before any provider call: why. */
    UNIVERSITY_MODULE_ANALYSIS_DENIED,
    /** A completed University module-analysis snapshot already existed and was returned; no provider was called. */
    UNIVERSITY_MODULE_ANALYSIS_REUSED,
    /** A new University module-analysis snapshot was classified and persisted, or a concurrent one returned. */
    UNIVERSITY_MODULE_ANALYSIS_PERSISTED,
    /** University module-analysis generation failed; no completed snapshot was stored and a retry remains possible. */
    UNIVERSITY_MODULE_ANALYSIS_FAILED,
    /** A university-report-v2 preparation was requested with an ACTIVE v2 entitlement: whether a selection came with it. */
    UNIVERSITY_REPORT_V2_REQUEST,
    /** A new immutable university-report-v2 snapshot was assembled from stored artefacts and persisted. */
    UNIVERSITY_REPORT_V2_GENERATED,
    /** A stored university-report-v2 snapshot was returned unchanged; nothing was generated. */
    UNIVERSITY_REPORT_V2_REUSED,
    /** The University paid narrative was retryable, so the deterministic report was returned and nothing stored. */
    UNIVERSITY_REPORT_V2_PROVISIONAL,
    /** The PDF of a stored university-report-v2 was rendered on demand: identifiers, version and size, never bytes. */
    UNIVERSITY_REPORT_V2_PDF_RENDERED,
    /** An a-level-report-v2 preparation was requested with an ACTIVE v2 entitlement; never a Jev call. */
    A_LEVEL_REPORT_V2_REQUEST,
    /** A new immutable a-level-report-v2 snapshot was assembled from stored artefacts and persisted. */
    A_LEVEL_REPORT_V2_GENERATED,
    /** A stored a-level-report-v2 snapshot was returned unchanged; nothing was generated. */
    A_LEVEL_REPORT_V2_REUSED,
    /** The A-level paid narrative was retryable, so the deterministic report was returned and nothing stored. */
    A_LEVEL_REPORT_V2_PROVISIONAL,
    /** The PDF of a stored a-level-report-v2 was rendered on demand: identifiers, version and size, never bytes. */
    A_LEVEL_REPORT_V2_PDF_RENDERED,
    /** A stored professional-report-v3 snapshot was returned unchanged; nothing was generated. */
    PROFESSIONAL_REPORT_V3_REUSED,
    /** The paid narrative was retryable, so the deterministic report was returned provisionally and nothing stored. */
    PROFESSIONAL_REPORT_V3_PROVISIONAL
}
