package com.chikere.futureai.coursemodule.university.analysis;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One failed generation attempt, kept for diagnosis. It never stands in for a result. */
public record UniversityModuleAnalysisFailure(
        UUID failureId,
        UUID assessmentId,
        Instant failedAt,
        Category category,
        boolean retryable,
        String errorType,
        String errorMessage,
        String schemaVersion,
        String policyVersion) {

    public enum Category {
        /** The provider could not be reached or did not answer. A later request may succeed. */
        PROVIDER_TRANSIENT(true),
        /** The provider answered with something the module contract cannot accept. A later request may succeed. */
        PROVIDER_INVALID_RESPONSE(true),
        /** The frozen policy could not classify accepted judgments: a defect, not worth retrying unchanged. */
        CLASSIFICATION(false),
        /** The snapshot could not be stored and no canonical snapshot exists. A later request may succeed. */
        PERSISTENCE(true),
        INTERNAL(false);

        private final boolean retryable;

        Category(boolean retryable) {
            this.retryable = retryable;
        }

        public boolean retryable() {
            return retryable;
        }
    }

    public UniversityModuleAnalysisFailure {
        Objects.requireNonNull(failureId, "failureId must not be null");
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Objects.requireNonNull(failedAt, "failedAt must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(errorType, "errorType must not be null");
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
    }
}
