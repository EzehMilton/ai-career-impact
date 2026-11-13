package com.chikere.futureai.responsibility.professional.openai;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.cost.ProviderUsage;
import com.chikere.futureai.judgment.openai.OpenAiDecisionQuestionAdapter;
import com.chikere.futureai.judgment.openai.OpenAiDecisionResponseValidation;
import com.chikere.futureai.responsibility.professional.application.*;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilitySchema;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilityStateMapper;
import org.springaicommunity.typesafe.question.Question;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.DecisionQuestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** OpenAI Decisions implementation of atomic professional-responsibility judgments. */
public final class ProfessionalResponsibilityOpenAiProvider implements ProfessionalResponsibilityJudgmentProvider {
    private final DecisionModel model;
    private final ProfessionalResponsibilitySchema schema;
    private final ProfessionalResponsibilityStateMapper stateMapper;
    private final String configuredModel;
    private final ProviderCostTracker costs;

    public ProfessionalResponsibilityOpenAiProvider(DecisionModel model, ProfessionalResponsibilitySchema schema,
            ProfessionalResponsibilityStateMapper stateMapper, String configuredModel, ProviderCostTracker costs) {
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.stateMapper = Objects.requireNonNull(stateMapper, "stateMapper must not be null");
        this.configuredModel = Objects.requireNonNull(configuredModel, "configuredModel must not be null");
        this.costs = Objects.requireNonNull(costs, "costs must not be null");
    }

    @Override
    public ProfessionalResponsibilityJudgmentResult judge(ProfessionalResponsibilityAnalysisRequest request) {
        Map<String, Question> schemaQuestions = schema.questions(request);
        LinkedHashMap<String, DecisionQuestion> questions = new LinkedHashMap<>();
        schemaQuestions.forEach((id, question) -> questions.put(id, OpenAiDecisionQuestionAdapter.adapt(question)));
        DecisionResponse response;
        try {
            response = model.call(DecisionPrompt.builder().fields(stateMapper.map(request)).questions(questions).build());
            OpenAiDecisionResponseValidation.requireResponse(response, questions.keySet());
            OpenAiDecisionResponseValidation.requireModel(response);
        }
        catch (RuntimeException failure) {
            throw new ResponsibilityJudgmentException("the OpenAI responsibility provider failed: "
                    + failure.getMessage(), failure);
        }
        recordUsage(response);
        List<ResponsibilityJudgmentSet> sets = new ArrayList<>();
        int index = 1;
        for (Map.Entry<String, String> responsibility : request.keyedResponsibilities().entrySet()) {
            String key = responsibility.getKey();
            sets.add(new ResponsibilityJudgmentSet(index++, key, responsibility.getValue(),
                    predicate(response, ProfessionalResponsibilitySchema.answerName(key,
                            ProfessionalResponsibilitySchema.DESCRIBED_CLEARLY)),
                    scored(response, ProfessionalResponsibilitySchema.answerName(key,
                            ProfessionalResponsibilitySchema.ASSISTANCE_SCOPE)),
                    scored(response, ProfessionalResponsibilitySchema.answerName(key,
                            ProfessionalResponsibilitySchema.CONTEXTUAL_JUDGEMENT)),
                    scored(response, ProfessionalResponsibilitySchema.answerName(key,
                            ProfessionalResponsibilitySchema.HUMAN_INTERACTION)),
                    scored(response, ProfessionalResponsibilitySchema.answerName(key,
                            ProfessionalResponsibilitySchema.PHYSICAL_PRESENCE)),
                    scored(response, ProfessionalResponsibilitySchema.answerName(key,
                            ProfessionalResponsibilitySchema.OUTCOME_ACCOUNTABILITY))));
        }
        Usage usage = response.getMetadata().getUsage();
        var result = new ProfessionalResponsibilityJudgmentResult(schema.version(),
                ResponsibilityJudgmentSource.OPENAI, Optional.of(response.getMetadata().getModel()),
                Optional.ofNullable(response.getMetadata().getId()), token(usage, true), token(usage, false),
                scored(response, ProfessionalResponsibilitySchema.REGULATED_CONTEXT), sets);
        if (!result.answers(request)) throw new ResponsibilityJudgmentException("mapped result did not answer request");
        return result;
    }

    @Override public ResponsibilityJudgmentSource source() { return ResponsibilityJudgmentSource.OPENAI; }

    private static double predicate(DecisionResponse response, String id) {
        YesNoAnswer answer = OpenAiDecisionResponseValidation.requirePredicate(response, id);
        return answer.getProbability();
    }

    private static ResponsibilityScoredJudgment scored(DecisionResponse response, String id) {
        ScoreAnswer answer = OpenAiDecisionResponseValidation.requireScore(response, id, 5);
        LinkedHashMap<Integer, Double> distribution = new LinkedHashMap<>();
        for (int level = 0; level < 5; level++) distribution.put(level, answer.getProbabilities().get(level));
        return new ResponsibilityScoredJudgment(answer.getValue(), distribution,
                OptionalDouble.of(answer.getConfidence()), OptionalInt.of(answer.getNearestLevel()),
                Optional.of(Integer.toString(answer.getNearestLevel())));
    }

    private void recordUsage(DecisionResponse response) {
        Usage usage = response.getMetadata().getUsage();
        costs.record(ProviderUsage.of(ProviderUsage.OPENAI, response.getMetadata().getModel(),
                usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens()));
    }

    private static OptionalLong token(Usage usage, boolean input) {
        if (usage == null) return OptionalLong.empty();
        Integer value = input ? usage.getPromptTokens() : usage.getCompletionTokens();
        return value == null ? OptionalLong.empty() : OptionalLong.of(value);
    }
}
