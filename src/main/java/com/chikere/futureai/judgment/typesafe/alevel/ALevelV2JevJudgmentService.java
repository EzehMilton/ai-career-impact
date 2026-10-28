package com.chikere.futureai.judgment.typesafe.alevel;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.cost.ProviderUsage;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.typesafe.JevResponseValidation;
import com.chikere.futureai.judgment.typesafe.JevTraceRendering;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceStage;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** The A-level adapter for new assessments: one Jev System One request for the ten a-level-v2 judgments. */
public final class ALevelV2JevJudgmentService implements ALevelJudgmentProvider {

    private final TypeSafeClient typeSafeClient;
    private final ALevelJudgmentSchemaV2 schema;
    private final ALevelV2JevStateMapper stateMapper;
    private final String configuredModelIdentifier;
    private final ProviderCostTracker costs;
    private final AssessmentTrace trace;

    public ALevelV2JevJudgmentService(TypeSafeClient typeSafeClient, ALevelJudgmentSchemaV2 schema,
            ALevelV2JevStateMapper stateMapper, String configuredModelIdentifier, ProviderCostTracker costs,
            AssessmentTrace trace) {
        this.typeSafeClient = Objects.requireNonNull(typeSafeClient, "typeSafeClient must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.stateMapper = Objects.requireNonNull(stateMapper, "stateMapper must not be null");
        this.configuredModelIdentifier = configuredModelIdentifier;
        this.costs = Objects.requireNonNull(costs, "costs must not be null");
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    @Override
    public AssessmentJudgmentSet judge(ALevelJudgmentInput input) {
        Map<String, Object> state = stateMapper.map(input);
        trace.event(JourneyType.A_LEVEL, TraceStage.DECISION_MODEL_PROVIDER_REQUEST,
                () -> JevTraceRendering.request(state, schema.questions(), schema.version(),
                        configuredModelIdentifier, typeSafeClient));
        SystemOneResponse response = trace.timed(JourneyType.A_LEVEL, TraceStage.DECISION_MODEL_PROVIDER_RESPONSE,
                null, () -> typeSafeClient.systemOne(state, schema.questions()), JevTraceRendering::response);
        recordUsage(response);
        JevResponseValidation.requireExactAnswers(response, schema.questions());

        List<DimensionJudgment> judgments = schema.questions().keySet().stream()
                .map(questionId -> toDomainJudgment(questionId, response))
                .toList();
        return new AssessmentJudgmentSet(JourneyType.A_LEVEL, schema.version(), judgments);
    }

    @Override
    public AssessmentSource source() {
        return AssessmentSource.JEV;
    }

    @Override
    public Optional<String> modelIdentifier() {
        return Optional.ofNullable(configuredModelIdentifier).filter(value -> !value.isBlank());
    }

    private void recordUsage(SystemOneResponse response) {
        var usage = response == null ? null : response.usage();
        String model = response == null || response.model() == null || response.model().isBlank()
                ? configuredModelIdentifier : response.model();
        costs.record(ProviderUsage.of(ProviderUsage.JEV, model,
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens()));
    }

    private DimensionJudgment toDomainJudgment(String questionId, SystemOneResponse response) {
        if (ALevelJudgmentSchemaV2.INFORMATION_SUFFICIENT.equals(questionId)) {
            NoulAnswer answer = response.noul(questionId);
            return new DimensionJudgment(questionId, ALevelJudgmentSchemaV2.nameOf(questionId),
                    OptionalDouble.of(answer.value()), Optional.empty(), Optional.empty(), OptionalDouble.empty(),
                    Map.of());
        }
        ScoreAnswer answer = response.score(questionId);
        Optional<String> nearestLabel = answer.nearestLabel().isBlank()
                ? Optional.empty() : Optional.of(answer.nearestLabel());
        LinkedHashMap<String, Double> probabilities = new LinkedHashMap<>();
        answer.probabilities().forEach((level, probability) -> probabilities.put(Integer.toString(level), probability));
        return new DimensionJudgment(questionId, ALevelJudgmentSchemaV2.nameOf(questionId),
                OptionalDouble.of(answer.value()), nearestLabel, Optional.empty(),
                OptionalDouble.of(answer.confidence()), probabilities);
    }
}
