package com.chikere.futureai.responsibility.professional.analysis;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One failed generation attempt, kept for diagnosis. It never stands in for a result. */
public record ResponsibilityAnalysisFailure(
        UUID failureId,
        UUID assessmentId,
        Instant failedAt,
        Stage stage,
        String errorType,
        String errorMessage,
        String schemaVersion,
        String policyVersion) {

    public enum Stage {
        PROVIDER,
        CLASSIFICATION,
        PERSISTENCE
    }

    public ResponsibilityAnalysisFailure {
        Objects.requireNonNull(failureId);
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(failedAt);
        Objects.requireNonNull(stage);
        Objects.requireNonNull(errorType);
        Objects.requireNonNull(schemaVersion);
        Objects.requireNonNull(policyVersion);
    }
}
