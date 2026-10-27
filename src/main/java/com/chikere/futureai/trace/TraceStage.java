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
    RESPONSIBILITY_CLASSIFICATION
}
