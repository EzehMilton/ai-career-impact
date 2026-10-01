package com.chikere.futureai.judgment.typesafe.university;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
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

/** The normal university adapter: one Jev System One request for all ten university-v1 judgments. */
public final class UniversityJevJudgmentService implements UniversityJudgmentProvider {

    private final TypeSafeClient typeSafeClient;
    private final UniversityJudgmentSchema schema;
    private final UniversityJevStateMapper stateMapper;
    private final String configuredModelIdentifier;

    public UniversityJevJudgmentService(
            TypeSafeClient typeSafeClient,
            UniversityJudgmentSchema schema,
            UniversityJevStateMapper stateMapper) {
        this(typeSafeClient, schema, stateMapper, null);
    }

    public UniversityJevJudgmentService(
            TypeSafeClient typeSafeClient,
            UniversityJudgmentSchema schema,
            UniversityJevStateMapper stateMapper,
            String configuredModelIdentifier) {
        this.typeSafeClient = Objects.requireNonNull(typeSafeClient, "typeSafeClient must not be null");
        this.schema = Objects.requireNonNull(schema, "schema must not be null");
        this.stateMapper = Objects.requireNonNull(stateMapper, "stateMapper must not be null");
        this.configuredModelIdentifier = configuredModelIdentifier;
    }

    @Override
    public AssessmentJudgmentSet judge(UniversityAssessmentState assessmentState) {
        Map<String, Object> state = stateMapper.map(assessmentState);
        SystemOneResponse response = typeSafeClient.systemOne(state, schema.questions());

        List<DimensionJudgment> judgments = schema.questions().keySet().stream()
                .map(questionId -> toDomainJudgment(questionId, response))
                .toList();
        return new AssessmentJudgmentSet(JourneyType.UNIVERSITY, schema.version(), judgments);
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
        if (UniversityJudgmentSchema.INFORMATION_SUFFICIENT.equals(questionId)) {
            return toNoulJudgment(questionId, response.noul(questionId));
        }
        return toScoreJudgment(questionId, response.score(questionId));
    }

    private DimensionJudgment toNoulJudgment(String questionId, NoulAnswer answer) {
        return new DimensionJudgment(questionId, UniversityDimensionNames.of(questionId),
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

        return new DimensionJudgment(questionId, UniversityDimensionNames.of(questionId),
                OptionalDouble.of(answer.value()), nearestLabel, Optional.empty(),
                OptionalDouble.of(answer.confidence()), probabilities);
    }
}
