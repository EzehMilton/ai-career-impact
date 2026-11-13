package com.chikere.futureai.decision.openai.poc;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Raw provider observations from the Phase 1 proof of concept. They deliberately remain separate from existing
 * Jev judgments: this phase does not claim semantic interchangeability or feed any production decision policy.
 */
public record OpenAiDecisionProofOfConceptResult(
        PredicateObservation informationSufficient,
        ScoreObservation taskRepeatability,
        ScoreObservation outcomeAccountability,
        String model,
        Optional<String> providerRequestId) {

    public OpenAiDecisionProofOfConceptResult {
        Objects.requireNonNull(informationSufficient, "informationSufficient must not be null");
        Objects.requireNonNull(taskRepeatability, "taskRepeatability must not be null");
        Objects.requireNonNull(outcomeAccountability, "outcomeAccountability must not be null");
        model = required(model, "model");
        providerRequestId = Objects.requireNonNull(providerRequestId, "providerRequestId must not be null")
                .map(value -> required(value, "providerRequestId"));
    }

    public record PredicateObservation(double probabilityOfYes, boolean answerAtDefaultThreshold) {
    }

    public record ScoreObservation(
            double expectedLevel,
            int nearestLevel,
            double confidence,
            List<Double> probabilities) {

        public ScoreObservation {
            probabilities = List.copyOf(Objects.requireNonNull(probabilities, "probabilities must not be null"));
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
