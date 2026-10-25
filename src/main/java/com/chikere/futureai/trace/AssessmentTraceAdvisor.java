package com.chikere.futureai.trace;

import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.core.Ordered;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Logs the exact messages Spring AI sends to the narrative model and the raw text it returns, before
 * Spring AI maps that text onto the typed narrative.
 *
 * <p>Registered only while tracing is enabled. Ordered just outside the usage advisor, so it sees each
 * actual model call and measures the provider latency rather than the application's own work. Only
 * the model, sampling settings, output schema and messages are read from the request: never headers,
 * API keys or other client configuration.</p>
 */
final class AssessmentTraceAdvisor implements CallAdvisor {

    private final AssessmentTrace trace;

    AssessmentTraceAdvisor(AssessmentTrace trace) {
        this.trace = Objects.requireNonNull(trace, "trace must not be null");
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        var journey = trace.currentJourney();
        trace.event(journey, TraceStage.NARRATIVE_PROVIDER_REQUEST, () -> describeRequest(request));
        return trace.timed(journey, TraceStage.NARRATIVE_PROVIDER_RESPONSE, null,
                () -> chain.nextCall(request), AssessmentTraceAdvisor::describeResponse);
    }

    private static Map<String, Object> describeRequest(ChatClientRequest request) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("provider", "openai");
        ChatOptions options = request.prompt().getOptions();
        if (options != null) {
            payload.put("model", options.getModel());
            payload.put("temperature", options.getTemperature());
            payload.put("maxTokens", options.getMaxTokens());
            if (options instanceof StructuredOutputChatOptions structured && structured.getOutputSchema() != null) {
                payload.put("outputSchema", structured.getOutputSchema());
            }
        }
        // Spring AI carries the entity's JSON schema in the request context until the model applies it.
        Map<String, Object> context = request.context();
        payload.putIfAbsent("outputSchema", context.get(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey()));
        payload.put("providerStructuredOutput", context.get(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey()));
        payload.put("outputFormatInstructions", context.get(ChatClientAttributes.OUTPUT_FORMAT.getKey()));
        List<Map<String, Object>> messages = request.prompt().getInstructions().stream()
                .map(AssessmentTraceAdvisor::message)
                .toList();
        payload.put("messages", messages);
        return payload;
    }

    private static Map<String, Object> message(Message message) {
        Map<String, Object> rendered = new LinkedHashMap<>();
        rendered.put("role", message.getMessageType());
        rendered.put("text", message.getText());
        return rendered;
    }

    private static Map<String, Object> describeResponse(ChatClientResponse response) {
        Map<String, Object> payload = new LinkedHashMap<>();
        ChatResponse chatResponse = response == null ? null : response.chatResponse();
        if (chatResponse == null) {
            payload.put("chatResponse", null);
            return payload;
        }
        ChatResponseMetadata metadata = chatResponse.getMetadata();
        if (metadata != null) {
            payload.put("model", metadata.getModel());
            payload.put("responseId", metadata.getId());
            Usage usage = metadata.getUsage();
            if (usage != null) {
                payload.put("promptTokens", usage.getPromptTokens());
                payload.put("completionTokens", usage.getCompletionTokens());
                payload.put("totalTokens", usage.getTotalTokens());
            }
        }
        payload.put("outputs", chatResponse.getResults().stream().map(AssessmentTraceAdvisor::generation).toList());
        return payload;
    }

    private static Map<String, Object> generation(Generation generation) {
        Map<String, Object> rendered = new LinkedHashMap<>();
        rendered.put("finishReason",
                generation.getMetadata() == null ? null : generation.getMetadata().getFinishReason());
        rendered.put("rawText", generation.getOutput() == null ? null : generation.getOutput().getText());
        return rendered;
    }

    @Override
    public String getName() {
        return "assessment-trace";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 2;
    }
}
