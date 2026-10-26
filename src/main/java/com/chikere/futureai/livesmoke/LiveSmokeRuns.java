package com.chikere.futureai.livesmoke;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Which smoke case produced which assessment in this run of the application, so a result page can offer
 * "Run this smoke case again". In memory only and bounded: it is a convenience, never provenance of record.
 */
public final class LiveSmokeRuns {

    private static final int MAXIMUM_REMEMBERED = 500;

    private final LiveSmokeTraceFiles traceFiles;

    private final Map<UUID, LiveSmokeCase> runs = new LinkedHashMap<>(16, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, LiveSmokeCase> eldest) {
            return size() > MAXIMUM_REMEMBERED;
        }
    };

    public LiveSmokeRuns(LiveSmokeTraceFiles traceFiles) {
        this.traceFiles = traceFiles;
    }

    /**
     * Records a completed submission: the saved case whose unedited inputs produced it, if any, and the same
     * provenance in the assessment's trace file ({@link LiveSmokeTraceFiles#CUSTOM} for any other input).
     */
    public synchronized void completed(LiveSmokeJourney journey, UUID assessmentId, Optional<LiveSmokeCase> smokeCase) {
        smokeCase.ifPresent(value -> runs.put(assessmentId, value));
        try {
            traceFiles.annotate(assessmentId, journey, smokeCase.map(LiveSmokeCase::id).orElse(LiveSmokeTraceFiles.CUSTOM));
        }
        catch (RuntimeException ignored) {
            // Diagnostics only: a trace file problem never affects the assessment.
        }
    }

    /** The case whose unedited inputs produced this assessment, if it was one. */
    public synchronized Optional<LiveSmokeCase> caseFor(UUID assessmentId) {
        return Optional.ofNullable(assessmentId).map(runs::get);
    }
}
