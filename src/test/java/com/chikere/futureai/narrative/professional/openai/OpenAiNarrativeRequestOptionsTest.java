package com.chikere.futureai.narrative.professional.openai;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.NarrativeRepairFeedback;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeContext;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeContextFactory;
import com.chikere.futureai.narrative.professional.paid.openai.OpenAiProfessionalPaidNarrativeGenerator;
import com.chikere.futureai.report.professional.detailed.DetailedReportFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The Professional narratives' sampling settings per model (role-level v7 and paid v1), checked on the request body
 * Spring AI actually sends. The "provider" is a local HTTP stub that records the body and refuses the call: no
 * OpenAI request is ever made.
 */
class OpenAiNarrativeRequestOptionsTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final NarrativeRepairFeedback FEEDBACK = new NarrativeRepairFeedback("RULE", "reason", "text");

    private HttpServer stub;
    private final List<JsonNode> bodies = new ArrayList<>();

    @BeforeEach
    void startStub() throws Exception {
        stub = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stub.createContext("/", exchange -> {
            synchronized (bodies) {
                bodies.add(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            }
            byte[] refusal = "{\"error\":{\"message\":\"stub\",\"type\":\"invalid_request_error\"}}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(400, refusal.length);
            exchange.getResponseBody().write(refusal);
            exchange.close();
        });
        stub.start();
    }

    @AfterEach
    void stopStub() {
        stub.stop(0);
    }

    // ---- Resolution ----

    @Test
    void gpt6ModelsGetReasoningEffortNoneAndNoTemperature() {
        var options = OpenAiNarrativeRequestOptions.forModel(Optional.of("gpt-6-luna"), Optional.empty(), 0.2);
        assertThat(options.reasoningEffort()).contains("none");
        assertThat(options.temperature()).isEmpty();
        assertThat(options.toChatOptions().build().getTemperature()).isNull();
        assertThat(options.toChatOptions().build().getReasoningEffort()).isEqualTo("none");
    }

    @Test
    void theModelIsCarriedAndSentOnEveryRequestAndAGeneratorWithoutATemperatureSendsNone() {
        var options = OpenAiNarrativeRequestOptions.forModel(Optional.of(" gpt-6-luna "), Optional.empty(), 0.2);
        assertThat(options.model()).contains("gpt-6-luna");
        assertThat(options.toChatOptions().build().getModel()).isEqualTo("gpt-6-luna");
        // The free University and A-level narratives have no custom temperature: a GPT-6 model gets the shared
        // reasoning effort, and any other model is sent no sampling setting at all, as before.
        var gpt6 = OpenAiNarrativeRequestOptions.forModel(Optional.of("gpt-6-luna"), Optional.empty());
        assertThat(gpt6.reasoningEffort()).contains("none");
        assertThat(gpt6.temperature()).isEmpty();
        var legacy = OpenAiNarrativeRequestOptions.forModel(Optional.of("gpt-4o-mini"), Optional.empty());
        assertThat(legacy.temperature()).isEmpty();
        assertThat(legacy.reasoningEffort()).isEmpty();
        assertThat(OpenAiNarrativeRequestOptions.forModel(Optional.empty(), Optional.empty()).model()).isEmpty();
    }

    @Test
    void otherModelsKeepTheCustomTemperatureAndAnExplicitEffortAlwaysOmitsIt() {
        var legacy = OpenAiNarrativeRequestOptions.forModel(Optional.of("gpt-4o-mini"), Optional.empty(), 0.2);
        assertThat(legacy.temperature()).contains(0.2);
        assertThat(legacy.reasoningEffort()).isEmpty();
        assertThat(OpenAiNarrativeRequestOptions.forModel(Optional.empty(), Optional.empty(), 0.2).temperature())
                .contains(0.2);
        var explicit = OpenAiNarrativeRequestOptions.forModel(Optional.of("gpt-4o-mini"), Optional.of(" low "), 0.2);
        assertThat(explicit.reasoningEffort()).contains("low");
        assertThat(explicit.temperature()).isEmpty();
        assertThat(OpenAiNarrativeRequestOptions.forModel(Optional.of("gpt-6-luna"), Optional.of("  "), 0.2)
                .reasoningEffort()).as("a blank setting falls back to the model default").contains("none");
        assertThatThrownBy(() -> new OpenAiNarrativeRequestOptions(Optional.of(0.2), Optional.of("none")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bothProfessionalGeneratorsShareTheSameSettingsForTheSameConfiguration() {
        for (String model : List.of("gpt-6-luna", "gpt-4o-mini")) {
            for (Optional<String> effort : List.of(Optional.<String>empty(), Optional.of("low"))) {
                assertThat(new OpenAiProfessionalNarrativeGenerator(null, JSON, model, effort).requestOptions())
                        .isEqualTo(new OpenAiProfessionalPaidNarrativeGenerator(null, JSON, model, effort).requestOptions());
            }
        }
    }

    // ---- professional-narrative-v7 on the wire ----

    @Test
    void v7WithGpt6SendsReasoningEffortNoneAndNoTemperatureOnGenerateAndRepair() {
        List<JsonNode> sent = v7Bodies("gpt-6-luna", Optional.empty());
        assertThat(sent).hasSizeGreaterThanOrEqualTo(2).allSatisfy(body -> {
            assertThat(body.path("model").asText()).isEqualTo("gpt-6-luna");
            assertThat(body.path("reasoning_effort").asText()).isEqualTo("none");
            assertThat(body.has("temperature")).as("no temperature is sent, not even the default").isFalse();
        });
    }

    @Test
    void v7WithALegacyModelKeepsTemperature02AndNoReasoningEffortOnGenerateAndRepair() {
        assertThat(v7Bodies("gpt-4o-mini", Optional.empty())).hasSizeGreaterThanOrEqualTo(2).allSatisfy(body -> {
            assertThat(body.path("temperature").asDouble()).isEqualTo(0.2);
            assertThat(body.has("reasoning_effort")).isFalse();
        });
    }

    @Test
    void v7WithAnExplicitReasoningEffortOmitsTemperature() {
        assertThat(v7Bodies("gpt-4o-mini", Optional.of("minimal"))).isNotEmpty().allSatisfy(body -> {
            assertThat(body.path("reasoning_effort").asText()).isEqualTo("minimal");
            assertThat(body.has("temperature")).isFalse();
        });
    }

    @Test
    void v7RepairSendsExactlyTheSameRequestSettingsAsTheFirstCall() {
        var context = v7Context();
        withChatClient("gpt-6-luna", client -> {
            var generator = new OpenAiProfessionalNarrativeGenerator(client, JSON, "gpt-6-luna", Optional.empty());
            assertThatThrownBy(() -> generator.generate(context)).isInstanceOf(RuntimeException.class);
        });
        List<JsonNode> first = List.copyOf(bodies);
        bodies.clear();
        withChatClient("gpt-6-luna", client -> {
            var generator = new OpenAiProfessionalNarrativeGenerator(client, JSON, "gpt-6-luna", Optional.empty());
            assertThatThrownBy(() -> generator.repair(context, FEEDBACK)).isInstanceOf(RuntimeException.class);
        });
        assertThat(settings(bodies.getFirst())).isEqualTo(settings(first.getFirst()));
    }

    // ---- professional-paid-narrative-v1 on the wire ----

    @Test
    void paidNarrativeWithGpt6SendsReasoningEffortNoneAndNoTemperatureOnGenerateAndRepair() {
        List<JsonNode> sent = paidBodies("gpt-6-luna", Optional.empty());
        assertThat(sent).hasSizeGreaterThanOrEqualTo(2).allSatisfy(body -> {
            assertThat(body.path("reasoning_effort").asText()).isEqualTo("none");
            assertThat(body.has("temperature")).isFalse();
        });
    }

    @Test
    void paidNarrativeWithALegacyModelKeepsTemperature02AndAnExplicitEffortOmitsIt() {
        assertThat(paidBodies("gpt-4o-mini", Optional.empty())).isNotEmpty().allSatisfy(body -> {
            assertThat(body.path("temperature").asDouble()).isEqualTo(0.2);
            assertThat(body.has("reasoning_effort")).isFalse();
        });
        bodies.clear();
        assertThat(paidBodies("some-reasoning-model", Optional.of("minimal"))).isNotEmpty().allSatisfy(body -> {
            assertThat(body.path("reasoning_effort").asText()).isEqualTo("minimal");
            assertThat(body.has("temperature")).isFalse();
        });
    }

    // ---- Helpers ----

    /** A generate call then a repair call; every body the client sent (it may retry) is returned. */
    private List<JsonNode> v7Bodies(String model, Optional<String> effort) {
        var context = v7Context();
        return bodies(model, client -> new OpenAiProfessionalNarrativeGenerator(client, JSON, model, effort),
                generator -> {
                    assertThatThrownBy(() -> generator.generate(context)).isInstanceOf(RuntimeException.class);
                    assertThatThrownBy(() -> generator.repair(context, FEEDBACK)).isInstanceOf(RuntimeException.class);
                });
    }

    private List<JsonNode> paidBodies(String model, Optional<String> effort) {
        var context = DetailedReportFixtures.chain("solicitor-8").context();
        return bodies(model, client -> new OpenAiProfessionalPaidNarrativeGenerator(client, JSON, model, effort),
                generator -> {
                    assertThatThrownBy(() -> generator.generate(context)).isInstanceOf(RuntimeException.class);
                    assertThatThrownBy(() -> generator.repair(context, FEEDBACK)).isInstanceOf(RuntimeException.class);
                });
    }

    private <G> List<JsonNode> bodies(String model, Function<ChatClient, G> create, Consumer<G> use) {
        withChatClient(model, client -> use.accept(create.apply(client)));
        assertThat(bodies).allSatisfy(body -> assertThat(body.path("model").asText()).isEqualTo(model));
        return List.copyOf(bodies);
    }

    private void withChatClient(String model, Consumer<ChatClient> use) {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class,
                        org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class,
                        org.springframework.ai.model.chat.client.autoconfigure.ChatClientAutoConfiguration.class))
                .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.api-key=stub-not-a-key",
                        "spring.ai.openai.base-url=http://127.0.0.1:" + stub.getAddress().getPort() + "/v1",
                        "spring.ai.openai.chat.model=" + model)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    use.accept(context.getBean(ChatClient.Builder.class).build());
                });
    }

    /** The request settings of one body: everything except the messages, which differ by the repair text. */
    private static JsonNode settings(JsonNode body) {
        var copy = body.deepCopy();
        ((com.fasterxml.jackson.databind.node.ObjectNode) copy).remove("messages");
        return copy;
    }

    private static ProfessionalNarrativeContext v7Context() {
        var state = new ProfessionalAssessmentState("Registered Nurse", "I care for patients on a busy ward.",
                List.of("Monitor patients", "Respond to deterioration"), List.of("Clinical observation"),
                OptionalInt.of(9));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("registered_nurse").judge(state);
        var decision = new ProfessionalDecisionPolicyV2().decide(judgments);
        return new ProfessionalNarrativeContextFactory().create(new StoredProfessionalAssessment(UUID.randomUUID(),
                Instant.parse("2026-10-01T00:00:00Z"), state, judgments, new ProfessionalAssessmentResult(decision,
                        AssessmentSource.JEV, judgments.schemaVersion(), decision.policyVersion()), Optional.empty()));
    }
}
