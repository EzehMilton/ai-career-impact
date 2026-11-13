package com.chikere.futureai.judgment.openai.professional;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2;
import com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentSchema;
import com.chikere.futureai.judgment.professional.schema.ProfessionalJudgmentStateMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.OpenAiDecisionModel;
import org.springframework.ai.openai.OpenAiDecisionOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalOpenAiJudgmentServiceContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final AtomicReference<JsonNode> request = new AtomicReference<>();
    private final AtomicReference<String> path = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            request.set(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] response = response().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("x-request-id", "req-professional-contract");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsAllNineCanonicalQuestionsAndFeedsTheUnchangedDecisionPolicy() {
        OpenAiDecisionOptions.Builder options = OpenAiDecisionOptions.builder();
        options.baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        options.apiKey("test-only-key");
        options.model("gpt-6-luna");
        options.timeout(Duration.ofSeconds(5));
        options.maxRetries(0);
        var provider = new ProfessionalOpenAiJudgmentService(
                OpenAiDecisionModel.builder().options(options.build()).build(),
                new ProfessionalJudgmentSchema(), new ProfessionalJudgmentStateMapper(), "gpt-6-luna");

        var judgments = provider.judge(new ProfessionalAssessmentState("Product manager",
                "Researches needs, prioritises work and owns product decisions",
                List.of("Interview users", "Prioritise roadmap"),
                List.of("Research", "Decision-making"), OptionalInt.of(6)));
        var decision = new ProfessionalDecisionPolicyV2().decide(judgments);

        assertThat(path.get()).isEqualTo("/v1/decisions");
        assertThat(request.get().get("model").asText()).isEqualTo("gpt-6-luna");
        assertThat(request.get().get("questions").size()).isEqualTo(9);
        assertThat(request.get().get("questions").get(0).get("name").asText())
                .isEqualTo("information_sufficient");
        assertThat(request.get().get("questions").get(0).get("type").asText()).isEqualTo("predicate");
        assertThat(request.get().get("questions").get(1).get("levels").size()).isEqualTo(5);
        assertThat(judgments.schemaVersion()).isEqualTo("professional-v1");
        assertThat(decision.policyVersion()).isEqualTo("professional-decision-v2");
        assertThat(decision.measures()).hasSize(4);
    }

    private static String response() {
        String[] ids = {
                "task_repeatability", "digital_executability", "human_trust_dependency",
                "physical_world_dependency", "contextual_complexity", "original_judgment",
                "accountability_responsibility", "ai_complementarity"
        };
        StringBuilder answers = new StringBuilder(
                "{\"type\":\"predicate\",\"name\":\"information_sufficient\",\"probability\":0.91}");
        for (String id : ids) {
            answers.append(",{")
                    .append("\"type\":\"score\",\"name\":\"").append(id)
                    .append("\",\"score\":2.0,\"confidence\":0.82,\"probabilities\":[")
                    .append("{\"value\":0,\"label\":\"0\",\"probability\":0.1},")
                    .append("{\"value\":1,\"label\":\"1\",\"probability\":0.2},")
                    .append("{\"value\":2,\"label\":\"2\",\"probability\":0.4},")
                    .append("{\"value\":3,\"label\":\"3\",\"probability\":0.2},")
                    .append("{\"value\":4,\"label\":\"4\",\"probability\":0.1}]} ");
        }
        return "{\"model\":\"gpt-6-luna\",\"answers\":[" + answers
                + "],\"usage\":{\"input_tokens\":800,"
                + "\"input_tokens_details\":{\"cached_tokens\":0,\"cache_write_tokens\":0},"
                + "\"output_tokens\":0,\"output_tokens_details\":{\"reasoning_tokens\":0},"
                + "\"total_tokens\":800}}";
    }
}
