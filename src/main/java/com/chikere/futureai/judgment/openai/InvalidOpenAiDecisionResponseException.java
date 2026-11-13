package com.chikere.futureai.judgment.openai;

public final class InvalidOpenAiDecisionResponseException extends RuntimeException {

    public InvalidOpenAiDecisionResponseException(String message) {
        super(message);
    }
}
