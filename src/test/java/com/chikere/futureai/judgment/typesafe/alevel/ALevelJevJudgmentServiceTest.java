package com.chikere.futureai.judgment.typesafe.alevel;

import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.Usage;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ALevelJevJudgmentServiceTest {

    private final ALevelJudgmentSchema schema = new ALevelJudgmentSchema();

    @Test
    void makesExactlyOneSystemOneRequestForAllElevenJudgments() {
        TypeSafeClient client = mock(TypeSafeClient.class);
        SystemOneResponse response = stubbedResponse();
        when(client.systemOne(anyMap(), any())).thenReturn(response);
        var state = state();

        AssessmentJudgmentSet judgments = new ALevelJevJudgmentService(client, schema,
                new ALevelJevStateMapper(), "jev-latest").judge(state);

        verify(client, times(1)).systemOne(new ALevelJevStateMapper().map(state), schema.questions());
        verifyNoMoreInteractions(client);
        assertThat(judgments.journey()).isEqualTo(JourneyType.A_LEVEL);
        assertThat(judgments.schemaVersion()).isEqualTo("a-level-v1");
        assertThat(judgments.judgments()).hasSize(11)
                .extracting(DimensionJudgment::dimensionId)
                .containsExactlyElementsOf(schema.questions().keySet());
    }

    @Test
    void returnsVendorNeutralJudgmentsWithProvenance() {
        TypeSafeClient client = mock(TypeSafeClient.class);
        SystemOneResponse response = stubbedResponse();
        when(client.systemOne(anyMap(), any())).thenReturn(response);
        var service = new ALevelJevJudgmentService(client, schema, new ALevelJevStateMapper(), "jev-latest");

        AssessmentJudgmentSet judgments = service.judge(state());
        DimensionJudgment information = judgments.judgments().getFirst();
        DimensionJudgment quantitative = judgments.judgments().stream()
                .filter(value -> value.dimensionId().equals(ALevelJudgmentSchema.QUANTITATIVE_REASONING))
                .findFirst().orElseThrow();

        assertThat(service.source()).isEqualTo(AssessmentSource.JEV);
        assertThat(service.modelIdentifier()).contains("jev-latest");
        assertThat(information.name()).isEqualTo("Information sufficiency");
        assertThat(information.numericValue()).hasValue(0.9);
        assertThat(information.confidenceValue()).isEmpty();
        assertThat(quantitative.name()).isEqualTo("Quantitative reasoning");
        assertThat(quantitative.numericValue()).hasValue(3.1);
        assertThat(quantitative.confidenceValue()).hasValue(0.82);
        assertThat(quantitative.categoricalValue()).contains("substantial");
        assertThat(quantitative.probabilityDistribution()).containsEntry("3", 1.0);
        assertThat(new ALevelJevJudgmentService(client, schema, new ALevelJevStateMapper())
                .modelIdentifier()).isEmpty();
    }

    private SystemOneResponse stubbedResponse() {
        LinkedHashMap<String, Answer> answers = new LinkedHashMap<>();
        answers.put(ALevelJudgmentSchema.INFORMATION_SUFFICIENT, new NoulAnswer(0.9));
        schema.questions().keySet().stream()
                .filter(id -> !id.equals(ALevelJudgmentSchema.INFORMATION_SUFFICIENT))
                .forEach(id -> answers.put(id, scoreAnswer()));
        return new SystemOneResponse("jev-test", answers, Usage.EMPTY, "test-request-id");
    }

    private ScoreAnswer scoreAnswer() {
        return new ScoreAnswer(3.1, Map.of(3, JsonContent.of("substantial")),
                Map.of(3, 1.0), 0.82);
    }

    private ALevelAssessmentState state() {
        return new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"), Map.of(),
                List.of("Problem solving"), List.of("Economics"), List.of(),
                Optional.of("United Kingdom"), Optional.empty());
    }
}
