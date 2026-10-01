package com.chikere.futureai.judgment.typesafe.university;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class UniversityJevJudgmentServiceTest {

    private final UniversityJudgmentSchema schema = new UniversityJudgmentSchema();

    @Test
    void makesExactlyOneSystemOneRequestForAllTenJudgments() {
        TypeSafeClient client = mock(TypeSafeClient.class);
        SystemOneResponse response = stubbedResponse();
        when(client.systemOne(anyMap(), any())).thenReturn(response);
        var state = state();

        AssessmentJudgmentSet judgments = new UniversityJevJudgmentService(client, schema,
                new UniversityJevStateMapper(), "jev-latest").judge(state);

        verify(client, times(1)).systemOne(new UniversityJevStateMapper().map(state), schema.questions());
        verifyNoMoreInteractions(client);
        assertThat(judgments.journey()).isEqualTo(JourneyType.UNIVERSITY);
        assertThat(judgments.schemaVersion()).isEqualTo("university-v1");
        assertThat(judgments.judgments()).hasSize(10)
                .extracting(DimensionJudgment::dimensionId)
                .containsExactlyElementsOf(schema.questions().keySet());
    }

    @Test
    void returnsVendorNeutralJudgmentsWithProvenance() {
        TypeSafeClient client = mock(TypeSafeClient.class);
        SystemOneResponse response = stubbedResponse();
        when(client.systemOne(anyMap(), any())).thenReturn(response);
        var service = new UniversityJevJudgmentService(client, schema, new UniversityJevStateMapper(),
                "jev-latest");

        AssessmentJudgmentSet judgments = service.judge(state());
        DimensionJudgment information = judgments.judgments().getFirst();
        DimensionJudgment analytical = judgments.judgments().stream()
                .filter(value -> value.dimensionId().equals(UniversityJudgmentSchema.ANALYTICAL_REASONING))
                .findFirst().orElseThrow();

        assertThat(service.source()).isEqualTo(AssessmentSource.JEV);
        assertThat(service.modelIdentifier()).contains("jev-latest");
        assertThat(information.name()).isEqualTo("Information sufficiency");
        assertThat(information.numericValue()).hasValue(0.88);
        assertThat(information.confidenceValue()).isEmpty();
        assertThat(analytical.name()).isEqualTo("Analytical reasoning");
        assertThat(analytical.numericValue()).hasValue(3.2);
        assertThat(analytical.confidenceValue()).hasValue(0.81);
        assertThat(analytical.categoricalValue()).contains("substantial");
        assertThat(analytical.probabilityDistribution()).containsEntry("3", 1.0);
        assertThat(new UniversityJevJudgmentService(client, schema, new UniversityJevStateMapper())
                .modelIdentifier()).isEmpty();
    }

    private SystemOneResponse stubbedResponse() {
        SystemOneResponse response = mock(SystemOneResponse.class);
        NoulAnswer noul = mock(NoulAnswer.class);
        doReturn(0.88).when(noul).value();
        doReturn(noul).when(response).noul(UniversityJudgmentSchema.INFORMATION_SUFFICIENT);
        schema.questions().keySet().stream()
                .filter(id -> !id.equals(UniversityJudgmentSchema.INFORMATION_SUFFICIENT))
                .forEach(id -> {
                    ScoreAnswer score = mock(ScoreAnswer.class);
                    doReturn(3.2).when(score).value();
                    doReturn(0.81).when(score).confidence();
                    doReturn("substantial").when(score).nearestLabel();
                    doReturn(Map.of(3, 1.0)).when(score).probabilities();
                    doReturn(score).when(response).score(id);
                });
        return response;
    }

    private UniversityAssessmentState state() {
        return new UniversityAssessmentState("Economics",
                "Micro, macro, econometrics and a quantitative dissertation.",
                List.of("Econometrics"), List.of("Statistical analysis"), List.of("Policy analysis"),
                Optional.empty(), Optional.of("United Kingdom"));
    }
}
