package com.chikere.futureai.judgment.openai;

import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/** Strict reusable validation at the OpenAI Decisions boundary. No defaults or fixture fallback are supplied. */
public final class OpenAiDecisionResponseValidation {

    private static final double DISTRIBUTION_TOLERANCE = 1.0e-6;

    private OpenAiDecisionResponseValidation() {}

    public static DecisionResponse requireResponse(DecisionResponse response, Set<String> expectedQuestionIds) {
        if (response == null) throw invalid("decision model returned no response");
        Objects.requireNonNull(expectedQuestionIds, "expectedQuestionIds must not be null");
        Map<String, DecisionAnswer> answers = response.getAnswers();
        if (answers == null) throw invalid("decision model returned no answer map");

        Set<String> missing = new TreeSet<>(expectedQuestionIds);
        missing.removeAll(answers.keySet());
        Set<String> unexpected = new TreeSet<>(answers.keySet());
        unexpected.removeAll(expectedQuestionIds);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw invalid("answers did not match questions: missing " + missing + ", unexpected " + unexpected);
        }
        answers.forEach((id, answer) -> {
            if (answer == null) throw invalid("answer " + id + " was null");
            if (answer instanceof RefusalAnswer) throw invalid("answer " + id + " was refused");
        });
        return response;
    }

    public static YesNoAnswer requirePredicate(DecisionResponse response, String questionId) {
        DecisionAnswer raw = requireAnswer(response, questionId);
        if (!(raw instanceof YesNoAnswer answer)) {
            throw invalid("answer " + questionId + " must be yes/no, was " + raw.getClass().getSimpleName());
        }
        requireProbability(answer.getProbability(), questionId + " probability");
        return answer;
    }

    public static ScoreAnswer requireScore(DecisionResponse response, String questionId, int levelCount) {
        if (levelCount < 2) throw new IllegalArgumentException("levelCount must be at least two");
        DecisionAnswer raw = requireAnswer(response, questionId);
        if (!(raw instanceof ScoreAnswer answer)) {
            throw invalid("answer " + questionId + " must be a score, was " + raw.getClass().getSimpleName());
        }
        double value = answer.getValue();
        if (!Double.isFinite(value) || value < 0.0 || value > levelCount - 1) {
            throw invalid("answer " + questionId + " expected score must be between 0 and " + (levelCount - 1));
        }
        if (answer.getProbabilities() == null || answer.getProbabilities().size() != levelCount) {
            throw invalid("answer " + questionId + " must contain " + levelCount + " probabilities");
        }
        answer.getProbabilities().forEach(valuePart -> requireProbability(valuePart, questionId + " probability"));
        double total = answer.getProbabilities().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total - 1.0) > DISTRIBUTION_TOLERANCE) {
            throw invalid("answer " + questionId + " probabilities must sum to 1");
        }
        requireProbability(answer.getConfidence(), questionId + " confidence");
        if (answer.getNearestLevel() < 0 || answer.getNearestLevel() >= levelCount) {
            throw invalid("answer " + questionId + " nearest level is outside the question scale");
        }
        return answer;
    }

    public static String requireModel(DecisionResponse response) {
        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        if (model == null || model.isBlank()) throw invalid("response metadata did not identify the model");
        return model.strip();
    }

    private static DecisionAnswer requireAnswer(DecisionResponse response, String questionId) {
        DecisionAnswer answer = response.getAnswer(questionId);
        if (answer == null) throw invalid("answer " + questionId + " was missing");
        if (answer instanceof RefusalAnswer) throw invalid("answer " + questionId + " was refused");
        return answer;
    }

    private static void requireProbability(Double value, String field) {
        if (value == null || !Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw invalid(field + " must be finite and between 0 and 1");
        }
    }

    private static InvalidOpenAiDecisionResponseException invalid(String message) {
        return new InvalidOpenAiDecisionResponseException(message);
    }
}
