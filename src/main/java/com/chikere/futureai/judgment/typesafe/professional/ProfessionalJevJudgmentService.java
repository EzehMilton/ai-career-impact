package com.chikere.futureai.judgment.typesafe.professional;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.cost.ProviderUsage;
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

public final class ProfessionalJevJudgmentService implements ProfessionalJudgmentProvider {

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

    private final TypeSafeClient typeSafeClient;
    private final ProfessionalJudgmentSchema schema;
    private final ProfessionalJevStateMapper stateMapper;
    private final String configuredModelIdentifier;
    private final ProviderCostTracker costs;

    public ProfessionalJevJudgmentService(
            TypeSafeClient typeSafeClient,
            ProfessionalJudgmentSchema schema,
            ProfessionalJevStateMapper stateMapper) {
        this(typeSafeClient, schema, stateMapper, null);
    }

    public ProfessionalJevJudgmentService(
            TypeSafeClient typeSafeClient,
            ProfessionalJudgmentSchema schema,
            ProfessionalJevStateMapper stateMapper,
            String configuredModelIdentifier) {
        this(typeSafeClient, schema, stateMapper, configuredModelIdentifier, ProviderCostTracker.disabled());
    }

    public ProfessionalJevJudgmentService(TypeSafeClient typeSafeClient, ProfessionalJudgmentSchema schema,
            ProfessionalJevStateMapper stateMapper, String configuredModelIdentifier,
            ProviderCostTracker costs) {
        this.typeSafeClient = Objects.requireNonNull(typeSafeClient, "typeSafeClient must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.stateMapper = Objects.requireNonNull(stateMapper, "stateMapper must not be null");
        this.configuredModelIdentifier = configuredModelIdentifier;
        this.costs = Objects.requireNonNull(costs, "costs must not be null");
    }

    @Override
    public AssessmentJudgmentSet judge(ProfessionalAssessmentState assessmentState) {
        Map<String, Object> state = stateMapper.map(assessmentState);
        SystemOneResponse response = typeSafeClient.systemOne(state, schema.questions());
        recordUsage(response);

        List<DimensionJudgment> judgments = schema.questions().keySet().stream()
                .map(questionId -> toDomainJudgment(questionId, response))
                .toList();
        return new AssessmentJudgmentSet(JourneyType.PROFESSIONAL, schema.version(), judgments);
    }

    private void recordUsage(SystemOneResponse response) {
        var usage = response == null ? null : response.usage();
        String model = response == null || response.model() == null || response.model().isBlank()
                ? configuredModelIdentifier : response.model();
        costs.record(ProviderUsage.of(ProviderUsage.JEV, model,
                usage == null ? null : usage.inputTokens(),
                usage == null ? null : usage.outputTokens()));
    }

    @Override
    public AssessmentSource source() {
        return AssessmentSource.JEV;
    }

    @Override
    public Optional<String> modelIdentifier() {
        return Optional.ofNullable(configuredModelIdentifier).filter(value -> !value.isBlank());
    }

    private DimensionJudgment toDomainJudgment(String questionId, SystemOneResponse response) {
        if (ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT.equals(questionId)) {
            return toNoulJudgment(questionId, response.noul(questionId));
        }
        return toScoreJudgment(questionId, response.score(questionId));
    }

    private DimensionJudgment toNoulJudgment(String questionId, NoulAnswer answer) {
        return new DimensionJudgment(
                questionId,
                dimensionName(questionId),
                OptionalDouble.of(answer.value()),
                Optional.empty(),
                Optional.empty(),
                OptionalDouble.empty(),
                Map.of());
    }

    private DimensionJudgment toScoreJudgment(String questionId, ScoreAnswer answer) {
        Optional<String> nearestLabel = answer.nearestLabel().isBlank()
                ? Optional.empty()
                : Optional.of(answer.nearestLabel());
        LinkedHashMap<String, Double> probabilities = new LinkedHashMap<>();
        answer.probabilities().forEach((level, probability) ->
                probabilities.put(Integer.toString(level), probability));

        return new DimensionJudgment(
                questionId,
                dimensionName(questionId),
                OptionalDouble.of(answer.value()),
                nearestLabel,
                Optional.empty(),
                OptionalDouble.of(answer.confidence()),
                probabilities);
    }

    private String dimensionName(String questionId) {
        String name = DIMENSION_NAMES.get(questionId);
        if (name == null) {
            throw new IllegalArgumentException("Unknown professional-v1 question: " + questionId);
        }
        return name;
    }
}
