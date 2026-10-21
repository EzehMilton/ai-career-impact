package com.chikere.futureai.cost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.core.Ordered;

import java.util.Objects;

/**
 * Records the token usage OpenAI reports on every chat response, for every {@code ChatClient} built
 * from the application's builder — the three narrative generators.
 *
 * <p>Ordered immediately before Spring AI's terminal model advisor, so it sees each actual model
 * response rather than only the last one an outer advisor might return. A call that throws is not
 * recorded: there is no usage to read, and providers do not normally bill failed requests.</p>
 */
public final class OpenAiUsageAdvisor implements CallAdvisor {

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAiUsageAdvisor.class);

    private final ProviderCostTracker tracker;

    public OpenAiUsageAdvisor(ProviderCostTracker tracker) {
        this.tracker = Objects.requireNonNull(tracker, "tracker must not be null");
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        try {
            tracker.record(usageOf(response));
        }
        catch (RuntimeException trackingFailure) {
            LOGGER.debug("Could not read OpenAI usage", trackingFailure);
        }
        return response;
    }

    private ProviderUsage usageOf(ChatClientResponse response) {
        ChatResponse chatResponse = response == null ? null : response.chatResponse();
        ChatResponseMetadata metadata = chatResponse == null ? null : chatResponse.getMetadata();
        Usage usage = metadata == null ? null : metadata.getUsage();
        return ProviderUsage.of(ProviderUsage.OPENAI, metadata == null ? null : metadata.getModel(),
                usage == null ? null : usage.getPromptTokens(),
                usage == null ? null : usage.getCompletionTokens());
    }

    @Override
    public String getName() {
        return "provider-cost-usage";
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
