package com.chikere.futureai.coursemodule.university.openai;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.cost.ProviderUsage;
import com.chikere.futureai.coursemodule.university.application.*;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleStateMapper;
import com.chikere.futureai.judgment.openai.OpenAiDecisionQuestionAdapter;
import com.chikere.futureai.judgment.openai.OpenAiDecisionResponseValidation;
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

/** OpenAI Decisions implementation of atomic university-module judgments. */
public final class UniversityModuleOpenAiProvider implements UniversityModuleJudgmentProvider {
    private final DecisionModel model;
    private final UniversityModuleSchema schema;
    private final UniversityModuleStateMapper stateMapper;
    private final ProviderCostTracker costs;

    public UniversityModuleOpenAiProvider(DecisionModel model, UniversityModuleSchema schema,
            UniversityModuleStateMapper stateMapper, ProviderCostTracker costs) {
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.stateMapper = Objects.requireNonNull(stateMapper, "stateMapper must not be null");
        this.costs = Objects.requireNonNull(costs, "costs must not be null");
    }

    @Override
    public UniversityModuleJudgmentResult judge(UniversityModuleAnalysisRequest request) {
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
            throw new UniversityModuleJudgmentException(UniversityModuleJudgmentException.Kind.PROVIDER_CALL,
                    "the OpenAI module provider failed: " + failure.getMessage(), failure);
        }
        recordUsage(response);
        List<UniversityModuleJudgmentSet> sets = new ArrayList<>();
        int index = 1;
        for (Map.Entry<String, String> module : request.keyedModules().entrySet()) {
            String key = module.getKey();
            sets.add(new UniversityModuleJudgmentSet(index++, key, module.getValue(),
                    predicate(response, UniversityModuleSchema.answerName(key, UniversityModuleSchema.DESCRIBED_CLEARLY)),
                    predicate(response, UniversityModuleSchema.answerName(key, UniversityModuleSchema.PROFESSIONAL_STANDARDS)),
                    scored(response, UniversityModuleSchema.answerName(key, UniversityModuleSchema.AI_LEARNING_ASSISTANCE)),
                    scored(response, UniversityModuleSchema.answerName(key, UniversityModuleSchema.INDEPENDENT_DEMONSTRATION)),
                    scored(response, UniversityModuleSchema.answerName(key, UniversityModuleSchema.HUMAN_JUDGEMENT)),
                    scored(response, UniversityModuleSchema.answerName(key, UniversityModuleSchema.INTERPERSONAL_WORK)),
                    scored(response, UniversityModuleSchema.answerName(key, UniversityModuleSchema.PRACTICAL_APPLICATION)),
                    scored(response, UniversityModuleSchema.answerName(key, UniversityModuleSchema.TECHNICAL_QUANTITATIVE))));
        }
        Usage usage = response.getMetadata().getUsage();
        var result = new UniversityModuleJudgmentResult(schema.version(), UniversityModuleJudgmentSource.OPENAI,
                Optional.of(response.getMetadata().getModel()), Optional.ofNullable(response.getMetadata().getId()),
                token(usage, true), token(usage, false), sets);
        if (!result.answers(request)) throw new UniversityModuleJudgmentException("mapped result did not answer request");
        return result;
    }

    @Override public UniversityModuleJudgmentSource source() { return UniversityModuleJudgmentSource.OPENAI; }

    private static double predicate(DecisionResponse response, String id) {
        YesNoAnswer answer = OpenAiDecisionResponseValidation.requirePredicate(response, id);
        return answer.getProbability();
    }

    private static ModuleScoredJudgment scored(DecisionResponse response, String id) {
        ScoreAnswer answer = OpenAiDecisionResponseValidation.requireScore(response, id, 5);
        LinkedHashMap<Integer, Double> distribution = new LinkedHashMap<>();
        for (int level = 0; level < 5; level++) distribution.put(level, answer.getProbabilities().get(level));
        return new ModuleScoredJudgment(answer.getValue(), distribution,
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
