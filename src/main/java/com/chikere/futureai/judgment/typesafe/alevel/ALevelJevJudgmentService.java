package com.chikere.futureai.judgment.typesafe.alevel;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
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

/** The normal A-level adapter: one Jev System One request for all eleven a-level-v1 judgments. */
public final class ALevelJevJudgmentService implements ALevelJudgmentProvider {

    private final TypeSafeClient typeSafeClient;
    private final ALevelJudgmentSchema schema;
    private final ALevelJevStateMapper stateMapper;
    private final String configuredModelIdentifier;

    public ALevelJevJudgmentService(TypeSafeClient typeSafeClient, ALevelJudgmentSchema schema,
            ALevelJevStateMapper stateMapper) {
        this(typeSafeClient, schema, stateMapper, null);
    }

    public ALevelJevJudgmentService(TypeSafeClient typeSafeClient, ALevelJudgmentSchema schema,
            ALevelJevStateMapper stateMapper, String configuredModelIdentifier) {
        this.typeSafeClient = Objects.requireNonNull(typeSafeClient, "typeSafeClient must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.stateMapper = Objects.requireNonNull(stateMapper, "stateMapper must not be null");
        this.configuredModelIdentifier = configuredModelIdentifier;
    }

    @Override
    public AssessmentJudgmentSet judge(ALevelAssessmentState assessmentState) {
        Map<String, Object> state = stateMapper.map(assessmentState);
        SystemOneResponse response = typeSafeClient.systemOne(state, schema.questions());

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

    private DimensionJudgment toDomainJudgment(String questionId, SystemOneResponse response) {
        if (ALevelJudgmentSchema.INFORMATION_SUFFICIENT.equals(questionId)) {
            return toNoulJudgment(questionId, response.noul(questionId));
        }
        return toScoreJudgment(questionId, response.score(questionId));
    }

    private DimensionJudgment toNoulJudgment(String questionId, NoulAnswer answer) {
        return new DimensionJudgment(questionId, ALevelDimensionNames.of(questionId),
                OptionalDouble.of(answer.value()), Optional.empty(), Optional.empty(),
                OptionalDouble.empty(), Map.of());
    }

    private DimensionJudgment toScoreJudgment(String questionId, ScoreAnswer answer) {
        Optional<String> nearestLabel = answer.nearestLabel().isBlank()
                ? Optional.empty()
                : Optional.of(answer.nearestLabel());
        LinkedHashMap<String, Double> probabilities = new LinkedHashMap<>();
        answer.probabilities().forEach((level, probability) ->
                probabilities.put(Integer.toString(level), probability));

        return new DimensionJudgment(questionId, ALevelDimensionNames.of(questionId),
                OptionalDouble.of(answer.value()), nearestLabel, Optional.empty(),
                OptionalDouble.of(answer.confidence()), probabilities);
    }
}
