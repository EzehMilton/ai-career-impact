package com.chikere.futureai.trace;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.decision.professional.DecisionFactorEffect;
import com.chikere.futureai.decision.professional.ProfessionalFactorDirection;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevJudgmentService;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevStateMapper;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrative;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeContext;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeValidator;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeGenerator;
import com.chikere.futureai.cost.ProviderCostTracker;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.JsonContent;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springaicommunity.typesafe.response.Answer;
import org.springaicommunity.typesafe.response.NoulAnswer;
import org.springaicommunity.typesafe.response.ScoreAnswer;
import org.springaicommunity.typesafe.response.SystemOneResponse;
import org.springaicommunity.typesafe.response.Usage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Wire-level traces of the live providers, driven over mocks so nothing leaves the process. */
class ProviderTraceTest {

    private final TraceLogCapture log = TraceLogCapture.start();
    private final AssessmentTrace trace = AssessmentTrace.enabled();

    @AfterEach
    void detach() {
        log.close();
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void theJevRequestIsLoggedInFullAndItsResponseIsTimedBeforeMapping() {
        TypeSafeClient client = mock(TypeSafeClient.class);
        ProfessionalJudgmentSchema schema = new ProfessionalJudgmentSchema();
        when(client.systemOne(anyMap(), anyMap())).thenReturn(jevResponse(schema));
        var service = new ProfessionalJevJudgmentService(client, schema, new ProfessionalJevStateMapper(),
                "jev-latest", ProviderCostTracker.disabled(), trace);

        trace.assessment(JourneyType.PROFESSIONAL, () -> {
            service.judge(new ProfessionalAssessmentState("Software Architect", "Designs systems",
                    List.of("Design platforms"), List.of("Architecture"), OptionalInt.of(14)));
            return UUID.randomUUID();
        });

        verify(client, times(1)).systemOne(anyMap(), anyMap());
        JsonNode request = log.only(TraceStage.DECISION_MODEL_PROVIDER_REQUEST).get("payload");
        assertThat(request.get("provider").asText()).isEqualTo("JEV");
        assertThat(request.get("configuredModel").asText()).isEqualTo("jev-latest");
        assertThat(request.at("/state/profession").asText()).isEqualTo("Software Architect");
        assertThat(request.at("/state/years_experience").asInt()).isEqualTo(14);
        assertThat(request.at("/questions/task_repeatability/instructions").asText())
                .startsWith("How repetitive, standardised and predictable");
        assertThat(request.at("/questions/task_repeatability/levels").size()).isEqualTo(5);
        assertThat(request.get("questions").size()).isEqualTo(9);
        JsonNode response = log.only(TraceStage.DECISION_MODEL_PROVIDER_RESPONSE).get("payload");
        assertThat(response.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(response.get("durationMs").isNumber()).isTrue();
        assertThat(response.at("/result/requestId").asText()).isEqualTo("test-request-id");
        assertThat(response.at("/result/answers/information_sufficient/value").asDouble()).isEqualTo(0.91);
        assertThat(response.at("/result/answers/task_repeatability/value").asDouble()).isEqualTo(2.0);
        assertThat(response.at("/result/answers/task_repeatability/confidence").asDouble()).isEqualTo(0.82);
        assertThat(response.at("/result/answers/task_repeatability/probabilities/2").asDouble()).isEqualTo(0.4);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void aFailedJevCallIsTracedAsFailedAndTheSameExceptionReachesTheCaller() {
        TypeSafeClient client = mock(TypeSafeClient.class);
        IllegalStateException failure = new IllegalStateException("Jev returned 503");
        when(client.systemOne(anyMap(), anyMap())).thenThrow(failure);
        var service = new ProfessionalJevJudgmentService(client, new ProfessionalJudgmentSchema(),
                new ProfessionalJevStateMapper(), "jev-latest", ProviderCostTracker.disabled(), trace);

        assertThatThrownBy(() -> service.judge(new ProfessionalAssessmentState("Nurse", "Cares for patients",
                List.of(), List.of(), OptionalInt.empty()))).isSameAs(failure);

        JsonNode response = log.only(TraceStage.DECISION_MODEL_PROVIDER_RESPONSE).get("payload");
        assertThat(response.get("status").asText()).isEqualTo("FAILED");
        assertThat(response.get("errorMessage").asText()).isEqualTo("Jev returned 503");
    }

    @Test
    void theExactOpenAiMessagesAndRawResponseAreLoggedAndTheCallIsTimed() throws Exception {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(OpenAiChatOptions.builder().model("gpt-4o-mini").build());
        String json = new ObjectMapper().writeValueAsString(narrative());
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(
                List.of(new Generation(new AssistantMessage(json))),
                ChatResponseMetadata.builder().model("gpt-4o-mini-2024-07-18").usage(new DefaultUsage(1_200,
                        300)).build()));
        ChatClient.Builder builder = ChatClient.builder(model);
        new AssessmentTraceConfiguration().assessmentTraceChatClientCustomizer(trace).customize(builder);
        var generator = new OpenAiProfessionalNarrativeGenerator(builder.build(), new ObjectMapper()
                .findAndRegisterModules(), "gpt-4o-mini");

        trace.assessment(JourneyType.PROFESSIONAL, () -> {
            assertThat(generator.generate(context())).isEqualTo(narrative());
            return UUID.randomUUID();
        });

        verify(model, times(1)).call(any(Prompt.class));
        JsonNode request = log.only(TraceStage.NARRATIVE_PROVIDER_REQUEST);
        assertThat(request.get("journey").asText()).isEqualTo("PROFESSIONAL");
        JsonNode messages = request.at("/payload/messages");
        assertThat(messages.get(0).get("role").asText()).isEqualTo("SYSTEM");
        assertThat(messages.get(0).get("text").asText())
                .isEqualTo(OpenAiProfessionalNarrativeGenerator.SYSTEM_INSTRUCTIONS);
        assertThat(messages.get(1).get("role").asText()).isEqualTo("USER");
        assertThat(messages.get(1).get("text").asText())
                .startsWith(OpenAiProfessionalNarrativeGenerator.USER_PROMPT_PREFIX)
                .contains("Software Architect", "AI_EXPOSURE", "digital_executability");
        assertThat(request.at("/payload/model").asText()).isEqualTo("gpt-4o-mini");
        // The Professional narrative's low temperature reaches the model; the configured model is kept.
        assertThat(request.at("/payload/temperature").asDouble()).isEqualTo(0.2);
        assertThat(request.at("/payload/outputSchema").asText()).contains("\"practicalActions\"", "\"headline\"");
        JsonNode response = log.only(TraceStage.NARRATIVE_PROVIDER_RESPONSE).get("payload");
        assertThat(response.get("status").asText()).isEqualTo("SUCCEEDED");
        assertThat(response.get("durationMs").isNumber()).isTrue();
        assertThat(response.at("/result/model").asText()).isEqualTo("gpt-4o-mini-2024-07-18");
        assertThat(response.at("/result/promptTokens").asInt()).isEqualTo(1_200);
        assertThat(response.at("/result/outputs/0/rawText").asText()).isEqualTo(json);
    }

    @Test
    void aRejectedNarrativeNamesTheRuleAndTheTextThatTriggeredIt() {
        ProfessionalNarrativeValidator validator = new ProfessionalNarrativeValidator(trace);
        ProfessionalNarrative scored = new ProfessionalNarrative("Headline", "You scored 7/10 overall.", "Meaning",
                "Exposure", "Human advantage", "Complementarity", "Adaptability",
                List.of("Action one", "Action two", "Action three"), "Limitations");

        assertThatThrownBy(() -> validator.validate(scored))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("narrative must not repeat numeric scores or percentage claims");

        JsonNode validation = log.only(TraceStage.NARRATIVE_VALIDATION).get("payload");
        assertThat(validation.get("outcome").asText()).isEqualTo("REJECTED");
        assertThat(validation.get("rejectedBy").asText()).isEqualTo("NO_NUMERIC_SCORES_OR_PERCENTAGES");
        JsonNode rules = validation.get("rules");
        assertThat(rules.get(2).get("triggeringText").asText()).isEqualTo("7/10");
        assertThat(rules.get(3).get("reason").asText()).startsWith("NOT_EVALUATED");

        log.clear();
        validator.validate(narrative());
        assertThat(log.only(TraceStage.NARRATIVE_VALIDATION).at("/payload/outcome").asText()).isEqualTo("ACCEPTED");
    }

    private static SystemOneResponse jevResponse(ProfessionalJudgmentSchema schema) {
        LinkedHashMap<String, Answer> answers = new LinkedHashMap<>();
        answers.put(ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT, new NoulAnswer(0.91));
        LinkedHashMap<Integer, JsonContent> legend = new LinkedHashMap<>();
        LinkedHashMap<Integer, Double> probabilities = new LinkedHashMap<>();
        double[] distribution = {0.10, 0.20, 0.40, 0.20, 0.10};
        for (int level = 0; level <= 4; level++) {
            legend.put(level, JsonContent.of("Level " + level));
            probabilities.put(level, distribution[level]);
        }
        schema.questions().keySet().stream()
                .filter(id -> !id.equals(ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT))
                .forEach(id -> answers.put(id, new ScoreAnswer(2.0, legend, probabilities, 0.82)));
        return new SystemOneResponse("jev-test", answers, Usage.EMPTY, "test-request-id");
    }

    private static ProfessionalNarrativeContext context() {
        return new ProfessionalNarrativeContext("Software Architect", "I design complex systems.",
                List.of("Design platforms"), List.of("Architecture"), OptionalInt.of(14),
                AssessmentStatus.READY,
                java.util.Arrays.stream(ProfessionalMeasure.values())
                        .map(measure -> new ProfessionalNarrativeContext.MeasureContext(measure, measure.name(), 6.5,
                                "Moderate"))
                        .toList(),
                List.of(new ProfessionalNarrativeContext.FactorContext("digital_executability", "Digital executability",
                        "Much of the work can be performed digitally", DecisionFactorEffect.INCREASES_AI_EXPOSURE,
                        ProfessionalFactorDirection.HIGH_DIGITAL_EXECUTABILITY, 1, 3.5, 0.1)),
                List.of(), AssessmentSource.JEV, "professional-v1", "professional-decision-v1");
    }

    private static ProfessionalNarrative narrative() {
        return new ProfessionalNarrative("Headline", "Summary", "Meaning", "Exposure", "Human advantage",
                "Complementarity", "Adaptability", List.of("Action one", "Action two", "Action three"),
                "Limitations");
    }
}
