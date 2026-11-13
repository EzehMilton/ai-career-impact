package com.chikere.futureai.decision.openai.poc;

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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises Spring AI and the official OpenAI Java client against a local HTTP server; no provider is contacted. */
class OpenAiDecisionProofOfConceptContractTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final AtomicReference<JsonNode> requestBody = new AtomicReference<>();
    private final AtomicReference<String> requestPath = new AtomicReference<>();
    private HttpServer server;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requestPath.set(exchange.getRequestURI().getPath());
            requestBody.set(JSON.readTree(exchange.getRequestBody().readAllBytes()));
            byte[] body = response().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.getResponseHeaders().add("x-request-id", "req-local-contract");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void sendsTheThreeQuestionsToTheDecisionsEndpointAndMapsTheProviderResponse() {
        OpenAiDecisionOptions.Builder options = OpenAiDecisionOptions.builder();
        options.baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1");
        options.apiKey("test-only-openai-key");
        options.model("gpt-6-luna");
        options.timeout(Duration.ofSeconds(5));
        options.maxRetries(0);
        var model = OpenAiDecisionModel.builder().options(options.build()).build();
        var proof = new OpenAiDecisionProofOfConcept(model);

        var result = proof.evaluate(new OpenAiDecisionProofOfConceptInput(
                "Senior software architect",
                "Designs regulated financial systems and reviews changes with engineering teams.",
                "Approve production architecture changes that can affect customer financial data",
                List.of("Architecture", "Risk analysis")));

        assertThat(requestPath.get()).isEqualTo("/v1/decisions");
        JsonNode body = requestBody.get();
        assertThat(body.get("model").asText()).isEqualTo("gpt-6-luna");
        assertThat(body.get("questions").size()).isEqualTo(3);
        assertThat(body.get("questions").get(0).get("name").asText())
                .isEqualTo(OpenAiDecisionProofOfConcept.INFORMATION_SUFFICIENT);
        assertThat(body.get("questions").get(0).get("type").asText()).isEqualTo("predicate");
        assertThat(body.get("questions").get(0).get("instructions").asText())
                .contains("Answer \"yes\" if:").contains("Answer \"no\" if:");
        assertThat(body.get("questions").get(1).get("name").asText())
                .isEqualTo(OpenAiDecisionProofOfConcept.TASK_REPEATABILITY);
        assertThat(body.get("questions").get(1).get("levels").size()).isEqualTo(5);
        assertThat(body.get("questions").get(2).get("name").asText())
                .isEqualTo(OpenAiDecisionProofOfConcept.OUTCOME_ACCOUNTABILITY_QUESTION);
        assertThat(body.get("questions").get(2).get("levels").get(4).get("description").asText())
                .startsWith("Errors can cause serious harm");

        assertThat(result.model()).isEqualTo("gpt-6-luna");
        assertThat(result.providerRequestId()).contains("req-local-contract");
        assertThat(result.informationSufficient().probabilityOfYes()).isEqualTo(0.91);
        assertThat(result.taskRepeatability().probabilities()).containsExactly(0.05, 0.60, 0.25, 0.05, 0.05);
        assertThat(result.outcomeAccountability().nearestLevel()).isEqualTo(3);
    }

    private static String response() {
        return """
                {
                  "model": "gpt-6-luna",
                  "answers": [
                    {"type":"predicate","name":"information_sufficient","probability":0.91},
                    {"type":"score","name":"task_repeatability","score":1.4,"confidence":0.73,
                     "probabilities":[
                       {"value":0,"label":"0","probability":0.05},
                       {"value":1,"label":"1","probability":0.60},
                       {"value":2,"label":"2","probability":0.25},
                       {"value":3,"label":"3","probability":0.05},
                       {"value":4,"label":"4","probability":0.05}]},
                    {"type":"score","name":"r1__outcome_accountability","score":3.2,"confidence":0.84,
                     "probabilities":[
                       {"value":0,"label":"0","probability":0.02},
                       {"value":1,"label":"1","probability":0.03},
                       {"value":2,"label":"2","probability":0.15},
                       {"value":3,"label":"3","probability":0.55},
                       {"value":4,"label":"4","probability":0.25}]}
                  ],
                  "usage": {
                    "input_tokens": 500,
                    "input_tokens_details": {"cached_tokens":0,"cache_write_tokens":0},
                    "output_tokens": 0,
                    "output_tokens_details": {"reasoning_tokens":0},
                    "total_tokens": 500
                  }
                }
                """;
    }
}
