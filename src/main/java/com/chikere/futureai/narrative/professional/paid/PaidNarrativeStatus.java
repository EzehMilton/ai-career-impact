package com.chikere.futureai.narrative.professional.paid;

/** Whether the paid report shows the validated narrative or its deterministic copy. */
public enum PaidNarrativeStatus {
    /** A validated narrative is stored and shown alongside the deterministic content. Immutable. */
    AVAILABLE,
    /**
     * Final fallback: the provider returned content, it failed validation and its one repair also failed. Stored and
     * immutable; the report renders completely from deterministic copy and no later request retries it.
     */
    FALLBACK,
    /**
     * Retryable fallback: no usable provider response (not configured, timeout, connection failure, rate limit,
     * provider error or empty response). NEVER stored: the deterministic report is shown provisionally and a later
     * explicit preparation may try the narrative again.
     */
    RETRYABLE_FALLBACK
}
