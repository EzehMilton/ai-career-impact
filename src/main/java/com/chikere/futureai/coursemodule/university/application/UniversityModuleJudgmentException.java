package com.chikere.futureai.coursemodule.university.application;

/**
 * The module-analysis call failed, or its answers could not be accepted. The message names what was wrong (a
 * missing or unexpected answer, an invalid value) and never repeats credentials.
 */
public final class UniversityModuleJudgmentException extends RuntimeException {

    /** Whether the provider could not be reached or answered, or answered with something that cannot be accepted. */
    public enum Kind {
        PROVIDER_CALL,
        INVALID_RESPONSE
    }

    private final Kind kind;

    public UniversityModuleJudgmentException(String message) {
        this(Kind.INVALID_RESPONSE, message, null);
    }

    public UniversityModuleJudgmentException(String message, Throwable cause) {
        this(Kind.INVALID_RESPONSE, message, cause);
    }

    public UniversityModuleJudgmentException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = java.util.Objects.requireNonNull(kind, "kind must not be null");
    }

    public Kind kind() {
        return kind;
    }
}
