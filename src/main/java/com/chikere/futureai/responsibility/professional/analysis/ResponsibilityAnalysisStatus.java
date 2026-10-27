package com.chikere.futureai.responsibility.professional.analysis;

/**
 * The lifecycle of a paid responsibility analysis. Generation is synchronous in v1: a snapshot is only ever stored
 * once it is COMPLETED, and a failed attempt is recorded separately as FAILED. PENDING is never persisted.
 */
public enum ResponsibilityAnalysisStatus {
    PENDING,
    COMPLETED,
    FAILED
}
