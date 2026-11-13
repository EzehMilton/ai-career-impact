package com.chikere.futureai.judgment.openai.professional;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.cost.ProviderUsage;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.openai.OpenAiDecisionResponseValidation;
import com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema;
import com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentStateMapper;
import com.chikere.futureai.judgment.professional.schema.ProfessionalQuestion;
import com.chikere.futureai.judgment.professional.schema.ProfessionalScoreQuestion;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
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

/** OpenAI Decisions adapter for the existing professional-v1 provider port. */
public final class ProfessionalOpenAiJudgmentService implements ProfessionalJudgmentProvider {

    private static final Map<String, String> DIMENSION_NAMES = Map.of(
            ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT, "Information sufficiency",
            ProfessionalJudgmentSchema.TASK_REPEATABILITY, "Task repeatability",
            ProfessionalJudgmentSchema.DIGITAL_EXECUTABILITY, "Digital executability",
            ProfessionalJudgmentSchema.HUMAN_TRUST_DEPENDENCY, "Human trust dependency",
            ProfessionalJudgmentSchema.PHYSICAL_WORLD_DEPENDENCY, "Physical-world dependency",
            ProfessionalJudgmentSchema.CONTEXTUAL_COMPLEXITY, "Contextual complexity",
            ProfessionalJudgmentSchema.ORIGINAL_JUDGMENT, "Original judgment",
            ProfessionalJudgmentSchema.ACCOUNTABILITY_RESPONSIBILITY, "Accountability and responsibility",
            ProfessionalJudgmentSchema.AI_COMPLEMENTARITY, "AI complementarity");

    private final DecisionModel decisionModel;
    private final ProfessionalJudgmentSchema schema;
    private final ProfessionalJudgmentStateMapper stateMapper;
    private final String configuredModel;
    private final ProviderCostTracker costs;
    private final AssessmentTrace trace;

    public ProfessionalOpenAiJudgmentService(DecisionModel decisionModel, ProfessionalJudgmentSchema schema,
            ProfessionalJudgmentStateMapper stateMapper, String configuredModel) {
        this(decisionModel, schema, stateMapper, configuredModel, ProviderCostTracker.disabled(),
                AssessmentTrace.disabled());
    }

    public ProfessionalOpenAiJudgmentService(DecisionModel decisionModel, ProfessionalJudgmentSchema schema,
            ProfessionalJudgmentStateMapper stateMapper, String configuredModel, ProviderCostTracker costs,
            AssessmentTrace trace) {
        this.decisionModel = Objects.requireNonNull(decisionModel, "decisionModel must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.stateMapper = Objects.requireNonNull(stateMapper, "stateMapper must not be null");
        this.configuredModel = required(configuredModel, "configuredModel");
        this.costs = Objects.requireNonNull(costs, "costs must not be null");
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    @Override
    public AssessmentJudgmentSet judge(ProfessionalAssessmentState assessmentState) {
        Map<String, Object> state = stateMapper.map(assessmentState);
        Map<String, DecisionQuestion> questions = questions();
        DecisionPrompt prompt = DecisionPrompt.builder().fields(state).questions(questions).build();
        trace.event(JourneyType.PROFESSIONAL, TraceStage.DECISION_MODEL_PROVIDER_REQUEST,
                () -> requestTrace(state));
        DecisionResponse response = trace.timed(JourneyType.PROFESSIONAL,
                TraceStage.DECISION_MODEL_PROVIDER_RESPONSE, null,
                () -> decisionModel.call(prompt), ProfessionalOpenAiJudgmentService::responseTrace);
        OpenAiDecisionResponseValidation.requireResponse(response, questions.keySet());
        OpenAiDecisionResponseValidation.requireModel(response);
        recordUsage(response);

        List<DimensionJudgment> judgments = schema.questions().values().stream()
                .map(question -> toDomain(question, response))
                .toList();
        return new AssessmentJudgmentSet(JourneyType.PROFESSIONAL, schema.version(), judgments);
    }

    @Override
    public AssessmentSource source() { return AssessmentSource.OPENAI; }

    @Override
    public Optional<String> modelIdentifier() { return Optional.of(configuredModel); }

    private Map<String, DecisionQuestion> questions() {
        LinkedHashMap<String, DecisionQuestion> result = new LinkedHashMap<>();
        schema.questions().forEach((id, question) -> result.put(id, OpenAiProfessionalQuestionAdapter.adapt(question)));
        return Collections.unmodifiableMap(result);
    }

    private DimensionJudgment toDomain(ProfessionalQuestion question, DecisionResponse response) {
        if (question.id().equals(ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT)) {
            YesNoAnswer answer = OpenAiDecisionResponseValidation.requirePredicate(response, question.id());
            return new DimensionJudgment(question.id(), name(question.id()),
                    OptionalDouble.of(answer.getProbability()), Optional.empty(), Optional.empty(),
                    OptionalDouble.empty(), Map.of());
        }
        ProfessionalScoreQuestion scoreQuestion = (ProfessionalScoreQuestion) question;
        ScoreAnswer answer = OpenAiDecisionResponseValidation.requireScore(
                response, question.id(), scoreQuestion.levels().size());
        LinkedHashMap<String, Double> distribution = new LinkedHashMap<>();
        for (int index = 0; index < answer.getProbabilities().size(); index++) {
            distribution.put(Integer.toString(scoreQuestion.levels().get(index).value()),
                    answer.getProbabilities().get(index));
        }
        return new DimensionJudgment(question.id(), name(question.id()), OptionalDouble.of(answer.getValue()),
                Optional.of(Integer.toString(scoreQuestion.levels().get(answer.getNearestLevel()).value())),
                Optional.empty(), OptionalDouble.of(answer.getConfidence()), distribution);
    }

    private void recordUsage(DecisionResponse response) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        costs.record(ProviderUsage.of(ProviderUsage.OPENAI,
                response.getMetadata() == null ? configuredModel : response.getMetadata().getModel(),
                usage == null ? null : usage.getPromptTokens(), usage == null ? null : usage.getCompletionTokens()));
    }

    private Map<String, Object> requestTrace(Map<String, Object> state) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", "OPENAI");
        payload.put("call", "DecisionModel.call(prompt)");
        payload.put("model", configuredModel);
        payload.put("schemaVersion", schema.version());
        payload.put("state", state);
        payload.put("questions", schema.questions());
        return payload;
    }

    private static Map<String, Object> responseTrace(DecisionResponse response) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        if (response == null) {
            payload.put("response", null);
            return payload;
        }
        payload.put("model", response.getMetadata() == null ? null : response.getMetadata().getModel());
        payload.put("requestId", response.getMetadata() == null ? null : response.getMetadata().getId());
        payload.put("answers", response.getAnswers());
        return payload;
    }

    private static String name(String id) {
        String name = DIMENSION_NAMES.get(id);
        if (name == null) throw new IllegalArgumentException("Unknown professional-v1 question: " + id);
        return name;
    }

    private static String required(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.strip();
    }
}
