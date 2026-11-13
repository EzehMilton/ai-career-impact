package com.chikere.futureai.judgment.openai.professional;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.judgment.openai.InvalidOpenAiDecisionResponseException;
import com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema;
import com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentStateMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.DecisionResponseMetadata;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProfessionalOpenAiJudgmentServiceTest {

    @Test
    void preservesTheSchemaAndMapsAllNineAnswersToTheExistingDomainContract() {
        DecisionModel model = mock(DecisionModel.class);
        when(model.call(any(DecisionPrompt.class))).thenAnswer(call -> response(call.getArgument(0)));
        var service = service(model);

        var result = service.judge(state());

        assertThat(service.source()).isEqualTo(AssessmentSource.OPENAI);
        assertThat(service.modelIdentifier()).contains("gpt-6-luna");
        assertThat(result.journey()).isEqualTo(JourneyType.PROFESSIONAL);
        assertThat(result.schemaVersion()).isEqualTo("professional-v1");
        assertThat(result.judgments()).hasSize(9);
        var information = result.judgments().getFirst();
        assertThat(information.dimensionId()).isEqualTo("information_sufficient");
        assertThat(information.numericValue()).hasValue(0.91);
        assertThat(information.confidenceValue()).isEmpty();
        var score = result.judgments().get(1);
        assertThat(score.numericValue()).hasValue(2.0);
        assertThat(score.categoricalValue()).contains("2");
        assertThat(score.confidenceValue()).hasValue(0.82);
        assertThat(score.probabilityDistribution()).containsExactly(
                java.util.Map.entry("0", 0.1), java.util.Map.entry("1", 0.2),
                java.util.Map.entry("2", 0.4), java.util.Map.entry("3", 0.2),
                java.util.Map.entry("4", 0.1));

        ArgumentCaptor<DecisionPrompt> prompt = ArgumentCaptor.forClass(DecisionPrompt.class);
        verify(model).call(prompt.capture());
        assertThat(prompt.getValue().getQuestions().keySet())
                .containsExactlyElementsOf(new ProfessionalJudgmentSchema().questions().keySet());
        assertThat(prompt.getValue().getQuestions().get("information_sufficient"))
                .isInstanceOf(YesNoQuestion.class);
        assertThat(((ScoreQuestion) prompt.getValue().getQuestions().get("task_repeatability")).getLevels())
                .extracting(level -> level.getLabel()).containsExactly("0", "1", "2", "3", "4");
    }

    @Test
    void refusesMalformedOrRefusedResultsWithoutDefaulting() {
        DecisionModel model = mock(DecisionModel.class);
        when(model.call(any(DecisionPrompt.class))).thenAnswer(call -> {
            DecisionPrompt prompt = call.getArgument(0);
            var builder = DecisionResponse.builder(prompt)
                    .answer("information_sufficient", RefusalAnswer.builder().reason("insufficient").build());
            new ProfessionalJudgmentSchema().questions().keySet().stream()
                    .filter(id -> !id.equals("information_sufficient"))
                    .forEach(id -> builder.answer(id, score()));
            return builder.metadata(metadata()).build();
        });

        assertThatThrownBy(() -> service(model).judge(state()))
                .isInstanceOf(InvalidOpenAiDecisionResponseException.class)
                .hasMessageContaining("information_sufficient was refused");
    }

    private static ProfessionalOpenAiJudgmentService service(DecisionModel model) {
        return new ProfessionalOpenAiJudgmentService(model, new ProfessionalJudgmentSchema(),
                new ProfessionalJudgmentStateMapper(), "gpt-6-luna");
    }

    private static DecisionResponse response(DecisionPrompt prompt) {
        var builder = DecisionResponse.builder(prompt)
                .answer("information_sufficient", YesNoAnswer.builder().probability(0.91).build());
        new ProfessionalJudgmentSchema().questions().keySet().stream()
                .filter(id -> !id.equals("information_sufficient"))
                .forEach(id -> builder.answer(id, score()));
        return builder.metadata(metadata()).build();
    }

    private static ScoreAnswer score() {
        return ScoreAnswer.builder().value(2.0).confidence(0.82)
                .probabilities(List.of(0.1, 0.2, 0.4, 0.2, 0.1)).build();
    }

    private static DecisionResponseMetadata metadata() {
        return DecisionResponseMetadata.builder().id("req-test").model("gpt-6-luna").build();
    }

    private static ProfessionalAssessmentState state() {
        return new ProfessionalAssessmentState("Product manager", "Researches needs and prioritises work",
                List.of("Interview users", "Prioritise roadmap"), List.of("Research"), OptionalInt.of(5));
    }
}
