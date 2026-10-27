package com.chikere.futureai.responsibility.professional.analysis;

/** Generation was attempted and failed. No completed snapshot exists; the failure was recorded and may be retried. */
public final class ResponsibilityAnalysisFailedException extends RuntimeException {

    private final ResponsibilityAnalysisFailure.Stage stage;

    public ResponsibilityAnalysisFailedException(ResponsibilityAnalysisFailure.Stage stage, String message, Throwable cause) {
        super(message, cause);
        this.stage = stage;
    }

    public ResponsibilityAnalysisFailure.Stage stage() {
        return stage;
    }
}
