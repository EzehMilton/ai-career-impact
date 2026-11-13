package com.chikere.futureai.decision.openai.poc;

import com.chikere.futureai.decision.openai.poc.OpenAiDecisionProofOfConceptResult.PredicateObservation;
import com.chikere.futureai.decision.openai.poc.OpenAiDecisionProofOfConceptResult.ScoreObservation;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilitySchema;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilityStateMapper;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.DecisionQuestion;
import org.springframework.ai.decision.question.ScoreQuestion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * An isolated three-question feasibility probe. It is not an assessment provider and no production capability calls
 * it. Results stay provider-native so Phase 1 makes no claim that OpenAI and Jev values are interchangeable.
 */
public final class OpenAiDecisionProofOfConcept {

    public static final String INFORMATION_SUFFICIENT = ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT;
    public static final String TASK_REPEATABILITY = ProfessionalJudgmentSchema.TASK_REPEATABILITY;
    public static final String OUTCOME_ACCOUNTABILITY = ProfessionalResponsibilitySchema.OUTCOME_ACCOUNTABILITY;
    public static final String OUTCOME_ACCOUNTABILITY_QUESTION =
            ProfessionalResponsibilitySchema.answerName("r1", OUTCOME_ACCOUNTABILITY);

    private static final double PROBABILITY_TOLERANCE = 1.0e-6;
    private static final UUID POC_ASSESSMENT_ID = new UUID(0L, 1L);

    private final DecisionModel decisionModel;

    public OpenAiDecisionProofOfConcept(DecisionModel decisionModel) {
        this.decisionModel = Objects.requireNonNull(decisionModel, "decisionModel must not be null");
    }

    public OpenAiDecisionProofOfConceptResult evaluate(OpenAiDecisionProofOfConceptInput input) {
        Objects.requireNonNull(input, "input must not be null");
        ProfessionalResponsibilityAnalysisRequest responsibilityRequest = new ProfessionalResponsibilityAnalysisRequest(
                POC_ASSESSMENT_ID, input.profession(), input.workDescription(), List.of(input.responsibility()),
                input.skills());

        Map<String, DecisionQuestion> questions = questions(responsibilityRequest);
        DecisionPrompt prompt = DecisionPrompt.builder()
                .fields(new ProfessionalResponsibilityStateMapper().map(responsibilityRequest))
                .questions(questions)
                .build();
        DecisionResponse response = Objects.requireNonNull(decisionModel.call(prompt),
                "decisionModel returned no response");
        validateAnswerNames(response.getAnswers(), questions.keySet());

        PredicateObservation informationSufficient = predicate(response, INFORMATION_SUFFICIENT);
        ScoreObservation taskRepeatability = score(response, TASK_REPEATABILITY,
                (ScoreQuestion) questions.get(TASK_REPEATABILITY));
        ScoreObservation outcomeAccountability = score(response, OUTCOME_ACCOUNTABILITY_QUESTION,
                (ScoreQuestion) questions.get(OUTCOME_ACCOUNTABILITY_QUESTION));

        String model = response.getMetadata() == null ? null : response.getMetadata().getModel();
        if (model == null || model.isBlank()) {
            throw invalid("response metadata did not identify the model");
        }
        String requestId = response.getMetadata().getId();
        return new OpenAiDecisionProofOfConceptResult(informationSufficient, taskRepeatability,
                outcomeAccountability, model, Optional.ofNullable(requestId).filter(value -> !value.isBlank()));
    }

    private static Map<String, DecisionQuestion> questions(ProfessionalResponsibilityAnalysisRequest request) {
        var professional = new ProfessionalJudgmentSchema().questions();
        var responsibility = new ProfessionalResponsibilitySchema().questions(request);
        LinkedHashMap<String, DecisionQuestion> questions = new LinkedHashMap<>();
        questions.put(INFORMATION_SUFFICIENT,
                JevQuestionDecisionAdapter.adapt(professional.get(INFORMATION_SUFFICIENT)));
        questions.put(TASK_REPEATABILITY,
                JevQuestionDecisionAdapter.adapt(professional.get(TASK_REPEATABILITY)));
        questions.put(OUTCOME_ACCOUNTABILITY_QUESTION,
                JevQuestionDecisionAdapter.adapt(responsibility.get(OUTCOME_ACCOUNTABILITY_QUESTION)));
        return Collections.unmodifiableMap(questions);
    }

    static void validateAnswerNames(Map<String, DecisionAnswer> answers, Set<String> expected) {
        Set<String> received = answers.keySet();
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(received);
        Set<String> unexpected = new TreeSet<>(received);
        unexpected.removeAll(expected);
        if (!missing.isEmpty() || !unexpected.isEmpty()) {
            throw invalid("answers did not match questions: missing " + missing + ", unexpected " + unexpected);
        }
        answers.forEach((name, answer) -> {
            if (answer instanceof RefusalAnswer) {
                throw invalid("question " + name + " was refused");
            }
        });
    }

    private static PredicateObservation predicate(DecisionResponse response, String name) {
        DecisionAnswer raw = response.getAnswer(name);
        if (!(raw instanceof YesNoAnswer answer)) {
            throw invalid("answer " + name + " must be yes/no, was " + raw.getClass().getSimpleName());
        }
        requireProbability(answer.getProbability(), name + " probability");
        return new PredicateObservation(answer.getProbability(), answer.isYes());
    }

    private static ScoreObservation score(DecisionResponse response, String name, ScoreQuestion question) {
        DecisionAnswer raw = response.getAnswer(name);
        if (!(raw instanceof ScoreAnswer answer)) {
            throw invalid("answer " + name + " must be a score, was " + raw.getClass().getSimpleName());
        }
        int levelCount = question.getLevels().size();
        double value = answer.getValue();
        if (!Double.isFinite(value) || value < 0 || value > levelCount - 1) {
            throw invalid("answer " + name + " expected level must be between 0 and " + (levelCount - 1));
        }
        List<Double> probabilities = answer.getProbabilities();
        if (probabilities.size() != levelCount) {
            throw invalid("answer " + name + " must contain " + levelCount + " probabilities");
        }
        probabilities.forEach(probability -> requireProbability(probability, name + " probability"));
        double total = probabilities.stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(total - 1.0) > PROBABILITY_TOLERANCE) {
            throw invalid("answer " + name + " probabilities must sum to 1");
        }
        Double confidence = answer.getConfidence();
        if (confidence == null) {
            throw invalid("answer " + name + " did not include confidence");
        }
        requireProbability(confidence, name + " confidence");
        int nearest = answer.getNearestLevel();
        if (nearest < 0 || nearest >= levelCount) {
            throw invalid("answer " + name + " nearest level is outside the question scale");
        }
        return new ScoreObservation(value, nearest, confidence, probabilities);
    }

    private static void requireProbability(Double value, String name) {
        if (value == null || !Double.isFinite(value) || value < 0 || value > 1) {
            throw invalid(name + " must be finite and between 0 and 1");
        }
    }

    private static OpenAiDecisionProofOfConceptException invalid(String message) {
        return new OpenAiDecisionProofOfConceptException(message);
    }
}
