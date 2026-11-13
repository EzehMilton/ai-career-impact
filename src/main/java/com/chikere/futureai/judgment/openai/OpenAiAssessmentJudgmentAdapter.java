package com.chikere.futureai.judgment.openai;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.cost.ProviderUsage;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.DecisionQuestion;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;

/** Shared strict OpenAI Decisions boundary for fixed assessment schemas. Java policies remain downstream. */
public final class OpenAiAssessmentJudgmentAdapter<I> {

    private final DecisionModel model;
    private final JourneyType journey;
    private final String schemaVersion;
    private final Map<String, Question> schema;
    private final Function<I, Map<String, Object>> stateMapper;
    private final Function<String, String> dimensionName;
    private final String configuredModel;
    private final ProviderCostTracker costs;
    private final AssessmentTrace trace;

    public OpenAiAssessmentJudgmentAdapter(DecisionModel model, JourneyType journey, String schemaVersion,
            Map<String, Question> schema, Function<I, Map<String, Object>> stateMapper,
            Function<String, String> dimensionName, String configuredModel, ProviderCostTracker costs,
            AssessmentTrace trace) {
        this.model = Objects.requireNonNull(model, "model must not be null");
        this.journey = Objects.requireNonNull(journey, "journey must not be null");
        this.schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        this.schema = Collections.unmodifiableMap(new LinkedHashMap<>(schema));
        this.stateMapper = Objects.requireNonNull(stateMapper, "stateMapper must not be null");
        this.dimensionName = Objects.requireNonNull(dimensionName, "dimensionName must not be null");
        this.configuredModel = Objects.requireNonNull(configuredModel, "configuredModel must not be null");
        this.costs = Objects.requireNonNull(costs, "costs must not be null");
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    public AssessmentJudgmentSet judge(I input) {
        Map<String, Object> state = stateMapper.apply(input);
        Map<String, DecisionQuestion> questions = questions();
        DecisionPrompt prompt = DecisionPrompt.builder().fields(state).questions(questions).build();
        trace.event(journey, TraceStage.DECISION_MODEL_PROVIDER_REQUEST,
                () -> Map.of("provider", "OPENAI", "model", configuredModel,
                        "schemaVersion", schemaVersion, "state", state));
        DecisionResponse response = trace.timed(journey, TraceStage.DECISION_MODEL_PROVIDER_RESPONSE, null,
                () -> model.call(prompt), value -> Map.of("answers", value == null ? Map.of() : value.getAnswers()));
        OpenAiDecisionResponseValidation.requireResponse(response, questions.keySet());
        OpenAiDecisionResponseValidation.requireModel(response);
        recordUsage(response);
        List<DimensionJudgment> judgments = schema.entrySet().stream()
                .map(entry -> map(entry.getKey(), entry.getValue(), response)).toList();
        return new AssessmentJudgmentSet(journey, schemaVersion, judgments);
    }

    private Map<String, DecisionQuestion> questions() {
        LinkedHashMap<String, DecisionQuestion> result = new LinkedHashMap<>();
        schema.forEach((id, question) -> result.put(id, OpenAiDecisionQuestionAdapter.adapt(question)));
        return Collections.unmodifiableMap(result);
    }

    private DimensionJudgment map(String id, Question question, DecisionResponse response) {
        if (question instanceof Noul) {
            YesNoAnswer answer = OpenAiDecisionResponseValidation.requirePredicate(response, id);
            return new DimensionJudgment(id, dimensionName.apply(id), OptionalDouble.of(answer.getProbability()),
                    Optional.empty(), Optional.empty(), OptionalDouble.empty(), Map.of());
        }
        Score score = (Score) question;
        ScoreAnswer answer = OpenAiDecisionResponseValidation.requireScore(response, id, score.criteria().size());
        LinkedHashMap<String, Double> probabilities = new LinkedHashMap<>();
        for (int index = 0; index < answer.getProbabilities().size(); index++) {
            probabilities.put(Integer.toString(index), answer.getProbabilities().get(index));
        }
        return new DimensionJudgment(id, dimensionName.apply(id), OptionalDouble.of(answer.getValue()),
                Optional.of(Integer.toString(answer.getNearestLevel())), Optional.empty(),
                OptionalDouble.of(answer.getConfidence()), probabilities);
    }

    private void recordUsage(DecisionResponse response) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        costs.record(ProviderUsage.of(ProviderUsage.OPENAI,
                response.getMetadata() == null ? configuredModel : response.getMetadata().getModel(),
                usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens()));
    }
}
