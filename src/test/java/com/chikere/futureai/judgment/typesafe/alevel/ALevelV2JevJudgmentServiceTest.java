package com.chikere.futureai.judgment.typesafe.alevel;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.trace.AssessmentTrace;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.Usage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** The a-level-v2 Jev adapter and its wire state, against a mocked client. No real Jev call. */
class ALevelV2JevJudgmentServiceTest {

    private final ALevelJudgmentSchemaV2 schema = new ALevelJudgmentSchemaV2();

    @Test
    void theSchemaIsTenSubjectCombinationQuestionsWithTheRenamedAiDimension() {
        assertThat(schema.version()).isEqualTo("a-level-v2");
        assertThat(schema.questions().keySet()).containsExactly("information_sufficient", "quantitative_reasoning",
                "analytical_reasoning", "written_verbal_communication", "original_judgment_creativity",
                "contextual_social_understanding", "technical_digital_foundation", "subject_skill_diversity",
                "discipline_family_coverage", "ai_learning_support");
        assertThat(schema.questions().keySet()).doesNotContain("ai_era_complementarity", "degree_pathway_breadth",
                "skill_transferability");
        assertThat(ALevelJudgmentSchemaV2.nameOf("ai_learning_support")).isEqualTo("AI learning support");
        // The policy's ids and the schema's are the same list.
        assertThat(schema.questions().keySet().stream().skip(1).toList())
                .containsExactlyElementsOf(ALevelDecisionPolicyV2.SCORE_DIMENSIONS);
        assertThatThrownBy(() -> ALevelJudgmentSchemaV2.nameOf("ai_era_complementarity"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theWireStateIsTheSubjectsOnly() {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of("Mathematics", "A*"), List.of("Debating"), List.of("Economics"), List.of("Finance"),
                Optional.of("United Kingdom"), Optional.of("I might take a gap year."));

        Map<String, Object> wire = new ALevelV2JevStateMapper().map(ALevelJudgmentInput.of(state));

        assertThat(wire).containsOnlyKeys("selected_subjects")
                .containsEntry("selected_subjects", List.of("Mathematics", "Economics", "Geography"));
    }

    @Test
    void twoStudentsWithTheSameSubjectsButDifferentContextSendTheSameRequest() {
        var first = new ALevelAssessmentState(List.of("History", "English Literature", "Politics"), Map.of(),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
        var second = new ALevelAssessmentState(List.of("history", "Eng Lit", "Government and Politics"),
                Map.of("History", "B"), List.of("Reading widely"), List.of("Law"), List.of("Journalism"),
                Optional.of("United Kingdom"), Optional.of("Undecided."));

        assertThat(ALevelJudgmentInput.of(second)).isEqualTo(ALevelJudgmentInput.of(first));
        assertThat(new ALevelV2JevStateMapper().map(ALevelJudgmentInput.of(second)))
                .isEqualTo(new ALevelV2JevStateMapper().map(ALevelJudgmentInput.of(first)));
    }

    @Test
    void makesExactlyOneSystemOneRequestForTheTenV2Judgments() {
        TypeSafeClient client = mock(TypeSafeClient.class);
        when(client.systemOne(anyMap(), any())).thenReturn(stubbedResponse());
        var input = new ALevelJudgmentInput(List.of("Mathematics", "Physics"));

        AssessmentJudgmentSet judgments = service(client).judge(input);

        verify(client, times(1)).systemOne(Map.of("selected_subjects", List.of("Mathematics", "Physics")),
                schema.questions());
        verifyNoMoreInteractions(client);
        assertThat(judgments.journey()).isEqualTo(JourneyType.A_LEVEL);
        assertThat(judgments.schemaVersion()).isEqualTo("a-level-v2");
        assertThat(judgments.judgments()).extracting(DimensionJudgment::dimensionId)
                .containsExactlyElementsOf(schema.questions().keySet());
        DimensionJudgment ai = judgments.judgments().getLast();
        assertThat(ai.name()).isEqualTo("AI learning support");
        assertThat(ai.confidenceValue()).hasValue(0.82);
        assertThat(service(client).source()).isEqualTo(AssessmentSource.JEV);
        assertThat(service(client).modelIdentifier()).contains("jev-latest");
        // The judgments decide under a-level-decision-v2 without any adaptation.
        assertThat(new ALevelDecisionPolicyV2().decide(judgments).measures()).hasSize(4);
    }

    private ALevelV2JevJudgmentService service(TypeSafeClient client) {
        return new ALevelV2JevJudgmentService(client, schema, new ALevelV2JevStateMapper(), "jev-latest",
                ProviderCostTracker.disabled(), AssessmentTrace.disabled());
    }

    private SystemOneResponse stubbedResponse() {
        LinkedHashMap<String, Answer> answers = new LinkedHashMap<>();
        answers.put(ALevelJudgmentSchemaV2.INFORMATION_SUFFICIENT, new NoulAnswer(0.9));
        schema.questions().keySet().stream()
                .filter(id -> !id.equals(ALevelJudgmentSchemaV2.INFORMATION_SUFFICIENT))
                .forEach(id -> answers.put(id, new ScoreAnswer(3.1, Map.of(3, JsonContent.of("substantial")),
                        Map.of(3, 1.0), 0.82)));
        return new SystemOneResponse("jev-test", answers, Usage.EMPTY, "test-request-id");
    }
}
