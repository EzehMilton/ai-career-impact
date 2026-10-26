package com.chikere.futureai.trace;

import java.util.UUID;

/**
 * An optional second destination for assessment trace events. It receives exactly the events written to the log,
 * serialised and redacted the same way, as one compact JSON line each. A sink must never throw into traced work;
 * any failure it raises is swallowed like every other trace failure.
 */
@FunctionalInterface
public interface AssessmentTraceSink {

    /**
     * @param assessmentTraceId the trace the event belongs to, or null outside a trace
     * @param assessmentId      the persisted assessment, or null while it is not yet known
     */
    void accept(String assessmentTraceId, UUID assessmentId, String jsonLine);
}
