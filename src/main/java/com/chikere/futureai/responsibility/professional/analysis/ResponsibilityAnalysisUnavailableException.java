package com.chikere.futureai.responsibility.professional.analysis;

/** The analysis cannot be generated for this assessment. No provider was called and nothing was stored. */
public final class ResponsibilityAnalysisUnavailableException extends RuntimeException {

    public enum Reason {
        ASSESSMENT_NOT_FOUND,
        NOT_ENTITLED,
        NO_RESPONSIBILITIES,
        /** More than eight responsibilities and no explicit selection: the customer must choose up to eight. */
        RESPONSIBILITY_SELECTION_REQUIRED,
        /** An explicit selection was invalid (empty, repeated or out of range). */
        INVALID_SELECTION,
        DUPLICATE_RESPONSIBILITIES,
        INVALID_RESPONSIBILITY,
        PROVIDER_NOT_CONFIGURED
    }

    private final Reason reason;

    public ResponsibilityAnalysisUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
