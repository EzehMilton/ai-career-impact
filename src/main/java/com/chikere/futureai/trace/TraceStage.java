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
    RESPONSIBILITY_GUIDANCE
}
