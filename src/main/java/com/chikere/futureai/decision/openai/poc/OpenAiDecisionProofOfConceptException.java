package com.chikere.futureai.decision.openai.poc;

/** A provider response could not satisfy the strict Phase 1 proof-of-concept contract. */
public final class OpenAiDecisionProofOfConceptException extends RuntimeException {

    public OpenAiDecisionProofOfConceptException(String message) {
        super(message);
    }
}
