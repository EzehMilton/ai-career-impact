package com.chikere.futureai.judgment.openai;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.judgment.openai.alevel.ALevelV2OpenAiJudgmentService;
import com.chikere.futureai.judgment.openai.university.UniversityOpenAiJudgmentService;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJudgmentSchemaV2;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelV2JevStateMapper;
import com.chikere.futureai.judgment.typesafe.university.UniversityJevStateMapper;
import com.chikere.futureai.judgment.typesafe.university.UniversityJudgmentSchema;
import com.chikere.futureai.trace.AssessmentTrace;
import org.junit.jupiter.api.Test;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.DecisionResponseMetadata;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.ScoreQuestion;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiPrimaryAssessmentAdaptersTest {

    @Test
    void universityPreservesTenQuestionContractAndMapsOpenAiAnswers() {
        DecisionModel model = respondingModel();
        var service = new UniversityOpenAiJudgmentService(model, new UniversityJudgmentSchema(),
                new UniversityJevStateMapper(), "gpt-6-luna", ProviderCostTracker.disabled(),
                AssessmentTrace.disabled());
        var state = new UniversityAssessmentState("Computer Science", "Algorithms and software engineering",
                List.of("Algorithms"), List.of("Programming"), List.of("Technology"), Optional.empty(),
                Optional.of("UK"));

        var result = service.judge(state);

        assertThat(service.source()).isEqualTo(com.chikere.futureai.assessment.university.application.AssessmentSource.OPENAI);
        assertThat(result.schemaVersion()).isEqualTo("university-v1");
        assertThat(result.judgments()).hasSize(10);
        assertThat(result.judgments().getFirst().numericValue()).hasValue(0.91);
        assertThat(result.judgments().get(1).probabilityDistribution()).containsEntry("2", 0.4);
    }

    @Test
    void aLevelV2PreservesSubjectOnlyTenQuestionContractAndMapsOpenAiAnswers() {
        var service = new ALevelV2OpenAiJudgmentService(respondingModel(), new ALevelJudgmentSchemaV2(),
                new ALevelV2JevStateMapper(), "gpt-6-luna", ProviderCostTracker.disabled(), AssessmentTrace.disabled());

        var result = service.judge(new ALevelJudgmentInput(List.of("Mathematics", "History", "Art")));

        assertThat(service.source()).isEqualTo(AssessmentSource.OPENAI);
        assertThat(result.schemaVersion()).isEqualTo("a-level-v2");
        assertThat(result.judgments()).hasSize(10);
        assertThat(result.judgments()).extracting(judgment -> judgment.dimensionId())
                .containsExactlyElementsOf(new ALevelJudgmentSchemaV2().questions().keySet());
    }

    private static DecisionModel respondingModel() {
        DecisionModel model = mock(DecisionModel.class);
        when(model.call(any(DecisionPrompt.class))).thenAnswer(invocation -> response(invocation.getArgument(0)));
        return model;
    }

    private static DecisionResponse response(DecisionPrompt prompt) {
        var builder = DecisionResponse.builder(prompt);
        prompt.getQuestions().forEach((id, question) -> builder.answer(id, question instanceof ScoreQuestion
                ? ScoreAnswer.builder().value(2.0).confidence(0.82)
                        .probabilities(List.of(0.1, 0.2, 0.4, 0.2, 0.1)).build()
                : YesNoAnswer.builder().probability(0.91).build()));
        return builder.metadata(DecisionResponseMetadata.builder().id("req-offline")
                .model("gpt-6-luna").build()).build();
    }
}
