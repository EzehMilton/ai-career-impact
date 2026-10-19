package com.chikere.futureai.judgment.typesafe.professional;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import com.chikere.futureai.cost.ProviderCostProperties;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.cost.ProviderRates;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.ArgumentCaptor;
import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.exception.TypeSafeException;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.Usage;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class ProfessionalJevJudgmentServiceTest {

    private TypeSafeClient typeSafeClient;
    private ProfessionalJudgmentSchema schema;
    private ProfessionalJevJudgmentService service;

    @BeforeEach
    void setUp() {
        typeSafeClient = mock(TypeSafeClient.class);
        schema = new ProfessionalJudgmentSchema();
        service = new ProfessionalJevJudgmentService(
                typeSafeClient, schema, new ProfessionalJevStateMapper());
    }

    @Test
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void sendsAllQuestionsOnceAndMapsEveryAnswer() {
        when(typeSafeClient.systemOne(anyMap(), anyMap())).thenReturn(response());

        AssessmentJudgmentSet result = service.judge(assessmentState());

        ArgumentCaptor<Map<String, Object>> stateCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map<String, Question>> questionsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(typeSafeClient, times(1)).systemOne(stateCaptor.capture(), questionsCaptor.capture());
        verifyNoMoreInteractions(typeSafeClient);

        assertThat(stateCaptor.getValue()).containsEntry("profession", "Product manager");
        assertThat(questionsCaptor.getValue()).containsExactlyEntriesOf(schema.questions());
        assertThat(result.journey()).isEqualTo(JourneyType.PROFESSIONAL);
        assertThat(result.schemaVersion()).isEqualTo("professional-v1");
        assertThat(result.judgments()).hasSize(9);

        DimensionJudgment score = judgment(result, ProfessionalJudgmentSchema.TASK_REPEATABILITY);
        assertThat(score.numericValue()).hasValue(2.0);
        assertThat(score.categoricalValue()).contains("Level 2");
        assertThat(score.confidence()).isEmpty();
        assertThat(score.confidenceValue()).hasValue(0.82);
        assertThat(score.probabilityDistribution()).containsExactly(
                Map.entry("0", 0.10),
                Map.entry("1", 0.20),
                Map.entry("2", 0.40),
                Map.entry("3", 0.20),
                Map.entry("4", 0.10));

        DimensionJudgment noul = judgment(result, ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT);
        assertThat(noul.numericValue()).hasValue(0.91);
        assertThat(noul.categoricalValue()).isEmpty();
        assertThat(noul.confidence()).isEmpty();
        assertThat(noul.confidenceValue()).isEmpty();
        assertThat(noul.probabilityDistribution()).isEmpty();
    }

    @Test
    void propagatesTypeSafeFailuresInsteadOfCreatingJudgments() {
        TypeSafeException failure = new TypeSafeException("Jev unavailable");
        when(typeSafeClient.systemOne(anyMap(), anyMap())).thenThrow(failure);

        assertThatThrownBy(() -> service.judge(assessmentState())).isSameAs(failure);
    }

    @Test
    void recordsTheTokenUsageJevReportsForCostEstimation(CapturedOutput output) {
        var tracker = new ProviderCostTracker(new ProviderCostProperties("USD", null,
                new ProviderRates(new BigDecimal("0.042"), BigDecimal.ZERO)));
        var tracked = new ProfessionalJevJudgmentService(typeSafeClient, schema,
                new ProfessionalJevStateMapper(), "jev-latest", tracker);
        SystemOneResponse withUsage = response();
        when(typeSafeClient.systemOne(anyMap(), anyMap())).thenReturn(new SystemOneResponse(
                withUsage.model(), withUsage.answers(), new Usage(2_000, 0), withUsage.requestId()));

        tracker.trackAssessment("professional", () -> {
            tracked.judge(assessmentState());
            return UUID.randomUUID();
        });

        assertThat(output).contains("jevCalls=1 jevInputTokens=2000 jevOutputTokens=0 jevCost=0.000084");
    }

    private SystemOneResponse response() {
        LinkedHashMap<String, Answer> answers = new LinkedHashMap<>();
        answers.put(ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT, new NoulAnswer(0.91));
        schema.questions().keySet().stream()
                .filter(id -> !id.equals(ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT))
                .forEach(id -> answers.put(id, scoreAnswer()));
        return new SystemOneResponse("jev-test", answers, Usage.EMPTY, "test-request-id");
    }

    private ScoreAnswer scoreAnswer() {
        LinkedHashMap<Integer, JsonContent> legend = new LinkedHashMap<>();
        LinkedHashMap<Integer, Double> probabilities = new LinkedHashMap<>();
        for (int level = 0; level <= 4; level++) {
            legend.put(level, JsonContent.of("Level " + level));
        }
        probabilities.put(0, 0.10);
        probabilities.put(1, 0.20);
        probabilities.put(2, 0.40);
        probabilities.put(3, 0.20);
        probabilities.put(4, 0.10);
        return new ScoreAnswer(2.0, legend, probabilities, 0.82);
    }

    private ProfessionalAssessmentState assessmentState() {
        return new ProfessionalAssessmentState(
                "Product manager",
                "Researches user needs and decides product priorities",
                List.of("Interview users", "Prioritise roadmap"),
                List.of("Research", "Decision-making"),
                OptionalInt.of(6));
    }

    private DimensionJudgment judgment(AssessmentJudgmentSet set, String dimensionId) {
        return set.judgments().stream()
                .filter(judgment -> judgment.dimensionId().equals(dimensionId))
                .findFirst()
                .orElseThrow();
    }
}
