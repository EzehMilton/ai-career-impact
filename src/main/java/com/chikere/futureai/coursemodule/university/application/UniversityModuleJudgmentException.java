package com.chikere.futureai.coursemodule.university.application;

/**
 * The module-analysis call failed, or its answers could not be accepted. The message names what was wrong (a
 * missing or unexpected answer, an invalid value) and never repeats credentials.
 */
public final class UniversityModuleJudgmentException extends RuntimeException {

    public UniversityModuleJudgmentException(String message) {
        super(message);
    }

    public UniversityModuleJudgmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
