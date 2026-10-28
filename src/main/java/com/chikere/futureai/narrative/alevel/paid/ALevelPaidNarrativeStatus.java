package com.chikere.futureai.narrative.alevel.paid;

/** Whether the A-level paid report shows the validated narrative or its deterministic copy. */
public enum ALevelPaidNarrativeStatus {
    /** A validated narrative is frozen into the report alongside the deterministic content. Immutable. */
    AVAILABLE,
    /**
     * Final fallback: the provider returned content that failed validation, and its one repair also failed. The
     * report is frozen with deterministic copy and is never retried.
     */
    FALLBACK,
    /**
     * Retryable fallback: no usable provider response (not configured, provider error or empty response). Nothing is
     * frozen; the deterministic report is shown provisionally and a later explicit preparation may try again.
     */
    RETRYABLE_FALLBACK
}
