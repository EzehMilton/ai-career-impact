package com.chikere.futureai.responsibility.professional.application;

/**
 * The responsibility-analysis call failed, or its answers could not be accepted. The message names what was wrong
 * (a missing or unexpected answer, an invalid value) and never repeats credentials.
 */
public final class ResponsibilityJudgmentException extends RuntimeException {

    public ResponsibilityJudgmentException(String message) {
        super(message);
    }

    public ResponsibilityJudgmentException(String message, Throwable cause) {
        super(message, cause);
    }
}
