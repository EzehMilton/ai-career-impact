package com.chikere.futureai.coursemodule.university.analysis;

import java.util.Objects;

/** Module analysis cannot be generated or returned for this request. No provider was called and nothing was stored. */
public final class UniversityModuleAnalysisUnavailableException extends RuntimeException {

    public enum Reason {
        ASSESSMENT_NOT_FOUND,
        /** No ACTIVE entitlement: refused for generation and for reuse alike. */
        NOT_ENTITLED,
        NO_MODULES,
        /** More than eight modules and no explicit selection: the customer must choose up to eight. */
        MODULE_SELECTION_REQUIRED,
        /** An explicit selection was empty, too large, repeated or named an unknown module. */
        INVALID_SELECTION,
        DUPLICATE_MODULES,
        PROVIDER_NOT_CONFIGURED
    }

    private final Reason reason;

    public UniversityModuleAnalysisUnavailableException(Reason reason, String message) {
        super(message);
        this.reason = Objects.requireNonNull(reason, "reason must not be null");
    }

    public Reason reason() {
        return reason;
    }
}
