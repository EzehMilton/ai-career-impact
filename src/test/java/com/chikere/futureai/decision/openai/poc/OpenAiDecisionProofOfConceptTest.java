package com.chikere.futureai.decision.openai.poc;

import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.typesafe.ProfessionalResponsibilitySchema;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.decision.DecisionModel;
import org.springframework.ai.decision.DecisionPrompt;
import org.springframework.ai.decision.DecisionResponse;
import org.springframework.ai.decision.DecisionResponseMetadata;
import org.springframework.ai.decision.answer.DecisionAnswer;
import org.springframework.ai.decision.answer.RefusalAnswer;
import org.springframework.ai.decision.answer.ScoreAnswer;
import org.springframework.ai.decision.answer.YesNoAnswer;
import org.springframework.ai.decision.input.FieldsInput;
import org.springframework.ai.decision.question.ScoreQuestion;
import org.springframework.ai.decision.question.YesNoQuestion;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiDecisionProofOfConceptTest {

    private static final OpenAiDecisionProofOfConceptInput INPUT = new OpenAiDecisionProofOfConceptInput(
            "Senior software architect",
            "Designs regulated financial systems and reviews changes with engineering teams.",
            "Approve production architecture changes that can affect customer financial data",
            List.of("Architecture", "Risk analysis"));

    @Test
    void asksThreeExistingQuestionsWithoutChangingTheirSemanticsAndKeepsProviderValuesSeparate() {
        DecisionModel model = mock(DecisionModel.class);
        when(model.call(any(DecisionPrompt.class))).thenAnswer(invocation -> valid(invocation.getArgument(0)));

        var result = new OpenAiDecisionProofOfConcept(model).evaluate(INPUT);

        assertThat(result.informationSufficient().probabilityOfYes()).isEqualTo(0.91);
        assertThat(result.informationSufficient().answerAtDefaultThreshold()).isTrue();
        assertThat(result.taskRepeatability().expectedLevel()).isEqualTo(1.4);
        assertThat(result.taskRepeatability().nearestLevel()).isEqualTo(1);
        assertThat(result.taskRepeatability().confidence()).isEqualTo(0.73);
        assertThat(result.outcomeAccountability().expectedLevel()).isEqualTo(3.2);
        assertThat(result.model()).isEqualTo("gpt-6-luna");
        assertThat(result.providerRequestId()).contains("req-poc-test");

        ArgumentCaptor<DecisionPrompt> captor = ArgumentCaptor.forClass(DecisionPrompt.class);
        verify(model).call(captor.capture());
        DecisionPrompt prompt = captor.getValue();
        assertThat(prompt.getQuestions().keySet()).containsExactly(
                OpenAiDecisionProofOfConcept.INFORMATION_SUFFICIENT,
                OpenAiDecisionProofOfConcept.TASK_REPEATABILITY,
                OpenAiDecisionProofOfConcept.OUTCOME_ACCOUNTABILITY_QUESTION);
        assertExactProfessionalQuestions(prompt);
        assertExactComplexQuestion(prompt);

        assertThat(prompt.getInstructions()).isInstanceOf(FieldsInput.class);
        assertThat(((FieldsInput) prompt.getInstructions()).getFields())
                .containsEntry("journey", "PROFESSIONAL_RESPONSIBILITIES")
                .containsEntry("profession", INPUT.profession())
                .containsEntry("work_description", INPUT.workDescription())
                .containsEntry("responsibilities", Map.of("r1", INPUT.responsibility()));
    }

    @Test
    void rejectsARefusedQuestionInsteadOfSubstitutingAValue() {
        DecisionModel model = mock(DecisionModel.class);
        when(model.call(any(DecisionPrompt.class))).thenAnswer(invocation -> {
            DecisionPrompt prompt = invocation.getArgument(0);
            return DecisionResponse.builder(prompt)
                    .answer(OpenAiDecisionProofOfConcept.INFORMATION_SUFFICIENT,
                            RefusalAnswer.builder().reason("cannot decide").build())
                    .answer(OpenAiDecisionProofOfConcept.TASK_REPEATABILITY, score(1.4, 0.73))
                    .answer(OpenAiDecisionProofOfConcept.OUTCOME_ACCOUNTABILITY_QUESTION, score(3.2, 0.84))
                    .metadata(metadata())
                    .build();
        });

        assertThatThrownBy(() -> new OpenAiDecisionProofOfConcept(model).evaluate(INPUT))
                .isInstanceOf(OpenAiDecisionProofOfConceptException.class)
                .hasMessageContaining("information_sufficient was refused");
    }

    @Test
    void rejectsMissingAndUnexpectedAnswers() {
        Map<String, DecisionAnswer> missing = Map.of(
                OpenAiDecisionProofOfConcept.INFORMATION_SUFFICIENT,
                YesNoAnswer.builder().probability(0.91).build(),
                OpenAiDecisionProofOfConcept.TASK_REPEATABILITY, score(1.4, 0.73));
        assertThatThrownBy(() -> OpenAiDecisionProofOfConcept.validateAnswerNames(missing, Set.of(
                OpenAiDecisionProofOfConcept.INFORMATION_SUFFICIENT,
                OpenAiDecisionProofOfConcept.TASK_REPEATABILITY,
                OpenAiDecisionProofOfConcept.OUTCOME_ACCOUNTABILITY_QUESTION)))
                .hasMessageContaining("missing [r1__outcome_accountability]");

        Map<String, DecisionAnswer> unexpectedAnswers = Map.of(
                OpenAiDecisionProofOfConcept.INFORMATION_SUFFICIENT,
                YesNoAnswer.builder().probability(0.91).build(),
                OpenAiDecisionProofOfConcept.TASK_REPEATABILITY, score(1.4, 0.73),
                OpenAiDecisionProofOfConcept.OUTCOME_ACCOUNTABILITY_QUESTION, score(3.2, 0.84),
                "unrequested", YesNoAnswer.builder().probability(0.5).build());
        assertThatThrownBy(() -> OpenAiDecisionProofOfConcept.validateAnswerNames(unexpectedAnswers, Set.of(
                OpenAiDecisionProofOfConcept.INFORMATION_SUFFICIENT,
                OpenAiDecisionProofOfConcept.TASK_REPEATABILITY,
                OpenAiDecisionProofOfConcept.OUTCOME_ACCOUNTABILITY_QUESTION)))
                .hasMessageContaining("unexpected [unrequested]");
    }

    @Test
    void rejectsMalformedScoreDistributions() {
        DecisionModel model = mock(DecisionModel.class);
        when(model.call(any(DecisionPrompt.class))).thenAnswer(invocation -> {
            DecisionPrompt prompt = invocation.getArgument(0);
            ScoreAnswer malformed = ScoreAnswer.builder()
                    .value(1.4)
                    .probabilities(List.of(0.2, 0.2, 0.2, 0.2, 0.1))
                    .confidence(0.73)
                    .build();
            return DecisionResponse.builder(prompt)
                    .answer(OpenAiDecisionProofOfConcept.INFORMATION_SUFFICIENT,
                            YesNoAnswer.builder().probability(0.91).build())
                    .answer(OpenAiDecisionProofOfConcept.TASK_REPEATABILITY, malformed)
                    .answer(OpenAiDecisionProofOfConcept.OUTCOME_ACCOUNTABILITY_QUESTION, score(3.2, 0.84))
                    .metadata(metadata())
                    .build();
        });

        assertThatThrownBy(() -> new OpenAiDecisionProofOfConcept(model).evaluate(INPUT))
                .isInstanceOf(OpenAiDecisionProofOfConceptException.class)
                .hasMessageContaining("probabilities must sum to 1");
    }

    private static void assertExactProfessionalQuestions(DecisionPrompt prompt) {
        var existing = new ProfessionalJudgmentSchema().questions();
        var existingNoul = (org.springaicommunity.typesafe.question.Noul) existing.get(
                ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT);
        var actualNoul = (YesNoQuestion) prompt.getQuestions().get(
                OpenAiDecisionProofOfConcept.INFORMATION_SUFFICIENT);
        assertThat(actualNoul.getText()).isEqualTo(existingNoul.instructions().asText());
        assertThat(actualNoul.getWhenYes()).isEqualTo(existingNoul.criteria().whenTrue().asText());
        assertThat(actualNoul.getWhenNo()).isEqualTo(existingNoul.criteria().whenFalse().asText());

        var existingScore = (org.springaicommunity.typesafe.question.Score) existing.get(
                ProfessionalJudgmentSchema.TASK_REPEATABILITY);
        assertExactScore((ScoreQuestion) prompt.getQuestions().get(OpenAiDecisionProofOfConcept.TASK_REPEATABILITY),
                existingScore);
    }

    private static void assertExactComplexQuestion(DecisionPrompt prompt) {
        var request = new ProfessionalResponsibilityAnalysisRequest(UUID.randomUUID(), INPUT.profession(),
                INPUT.workDescription(), List.of(INPUT.responsibility()), INPUT.skills());
        var existing = (org.springaicommunity.typesafe.question.Score) new ProfessionalResponsibilitySchema()
                .questions(request).get(OpenAiDecisionProofOfConcept.OUTCOME_ACCOUNTABILITY_QUESTION);
        assertExactScore((ScoreQuestion) prompt.getQuestions().get(
                OpenAiDecisionProofOfConcept.OUTCOME_ACCOUNTABILITY_QUESTION), existing);
    }

    private static void assertExactScore(ScoreQuestion actual, org.springaicommunity.typesafe.question.Score existing) {
        assertThat(actual.getText()).isEqualTo(existing.instructions().asText());
        assertThat(actual.getLevels()).hasSize(existing.maxLevel() + 1);
        for (int level = 0; level <= existing.maxLevel(); level++) {
            assertThat(actual.getLevels().get(level).getLabel()).isEqualTo(Integer.toString(level));
            assertThat(actual.getLevels().get(level).getDescription()).isEqualTo(existing.levelAt(level).asText());
        }
    }

    private static DecisionResponse valid(DecisionPrompt prompt) {
        return DecisionResponse.builder(prompt)
                .answer(OpenAiDecisionProofOfConcept.INFORMATION_SUFFICIENT,
                        YesNoAnswer.builder().probability(0.91).build())
                .answer(OpenAiDecisionProofOfConcept.TASK_REPEATABILITY, score(1.4, 0.73))
                .answer(OpenAiDecisionProofOfConcept.OUTCOME_ACCOUNTABILITY_QUESTION, score(3.2, 0.84))
                .metadata(metadata())
                .build();
    }

    private static ScoreAnswer score(double value, double confidence) {
        List<Double> probabilities = value < 2
                ? List.of(0.05, 0.60, 0.25, 0.05, 0.05)
                : List.of(0.02, 0.03, 0.15, 0.55, 0.25);
        return ScoreAnswer.builder().value(value).probabilities(probabilities).confidence(confidence).build();
    }

    private static DecisionResponseMetadata metadata() {
        return DecisionResponseMetadata.builder().id("req-poc-test").model("gpt-6-luna").build();
    }
}
