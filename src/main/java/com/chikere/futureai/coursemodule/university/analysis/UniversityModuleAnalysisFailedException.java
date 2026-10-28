package com.chikere.futureai.coursemodule.university.analysis;

import java.util.Objects;

/** Generation was attempted and failed. No completed snapshot exists; the failure was recorded and may be retried. */
public final class UniversityModuleAnalysisFailedException extends RuntimeException {

    private final UniversityModuleAnalysisFailure.Category category;

    public UniversityModuleAnalysisFailedException(UniversityModuleAnalysisFailure.Category category, String message,
            Throwable cause) {
        super(message, cause);
        this.category = Objects.requireNonNull(category, "category must not be null");
    }

    public UniversityModuleAnalysisFailure.Category category() {
        return category;
    }
}
