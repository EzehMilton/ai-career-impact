package com.chikere.futureai.evidence.alevel.application;

/**
 * Categories of factual A-level context.
 *
 * <p>{@code DEGREE_PREREQUISITES} exists because subject requirements for specific courses are
 * external facts with a publisher and a date. They are never a model judgment and never alter an
 * assessment measure.</p>
 */
public enum ALevelEvidenceCategory {
    DEGREE_PREREQUISITES,
    COURSE_ENTRY_CONTEXT,
    SUBJECT_PATHWAY_CONTEXT,
    LABOUR_MARKET_CONTEXT,
    SKILL_DEMAND_CONTEXT
}
