package com.chikere.futureai.judgment.openai;

import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import com.chikere.futureai.coursemodule.university.openai.UniversityModuleOpenAiProvider;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleSchema;
import com.chikere.futureai.coursemodule.university.typesafe.UniversityModuleStateMapper;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSource;
import com.chikere.futureai.responsibility.professional.openai.ProfessionalResponsibilityOpenAiProvider;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilitySchema;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilityStateMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.DecisionResponseMetadata;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.question.ScoreQuestion;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpenAiDetailedAnalysisAdaptersTest {

    @Test
    void responsibilityAdapterMapsAllDynamicQuestionsWithoutClassifying() {
        var provider = new ProfessionalResponsibilityOpenAiProvider(respondingModel(),
                new ProfessionalResponsibilitySchema(), new ProfessionalResponsibilityStateMapper(),
                "gpt-6-luna", ProviderCostTracker.disabled());
        var request = new ProfessionalResponsibilityAnalysisRequest(UUID.randomUUID(), "Nurse",
                "Provides clinical care", List.of("Assess patients", "Administer medication"),
                List.of("Clinical judgement"));

        var result = provider.judge(request);

        assertThat(result.source()).isEqualTo(ResponsibilityJudgmentSource.OPENAI);
        assertThat(result.schemaVersion()).isEqualTo("professional-responsibility-v1");
        assertThat(result.responsibilities()).hasSize(2);
        assertThat(result.responsibilities().getFirst().describedClearly()).isEqualTo(0.91);
        assertThat(result.regulatedContext().value()).isEqualTo(2.0);
    }

    @Test
    void moduleAdapterMapsAllDynamicQuestionsWithoutClassifying() {
        var provider = new UniversityModuleOpenAiProvider(respondingModel(), new UniversityModuleSchema(),
                new UniversityModuleStateMapper(), ProviderCostTracker.disabled());
        var request = new UniversityModuleAnalysisRequest(UUID.randomUUID(), "Engineering",
                "Applied engineering degree", List.of("Thermodynamics", "Engineering Design"),
                List.of("Analysis"));

        var result = provider.judge(request);

        assertThat(result.source()).isEqualTo(UniversityModuleJudgmentSource.OPENAI);
        assertThat(result.schemaVersion()).isEqualTo("university-module-v1");
        assertThat(result.modules()).hasSize(2);
        assertThat(result.modules().getFirst().professionalStandards()).isEqualTo(0.91);
        assertThat(result.modules().getFirst().technicalQuantitative().value()).isEqualTo(2.0);
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
