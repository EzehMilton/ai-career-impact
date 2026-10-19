package com.chikere.futureai.cost;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Drives a real {@link ChatClient} over a mocked model, so no request leaves the process. */
@ExtendWith(OutputCaptureExtension.class)
class OpenAiUsageAdvisorTest {

    private final ProviderCostTracker tracker = new ProviderCostTracker(new ProviderCostProperties("USD",
            new ProviderRates(new BigDecimal("0.15"), new BigDecimal("0.60")), null));

    @Test
    void theConfiguredCustomizerRecordsUsageForEveryClientBuiltFromTheBuilder(CapturedOutput output) {
        ChatModel model = model(response(new DefaultUsage(3_000, 800)));
        ChatClient.Builder builder = ChatClient.builder(model);
        new ProviderCostConfiguration().providerCostChatClientCustomizer(tracker).customize(builder);
        ChatClient client = builder.build();

        tracker.trackAssessment("professional", () -> {
            client.prompt().user("explain").call().content();
            client.prompt().user("explain again").call().content();
            return UUID.randomUUID();
        });

        assertThat(output).contains("openaiCalls=2 openaiInputTokens=6000 openaiOutputTokens=1600",
                "openaiCost=0.001860");
    }

    @Test
    void aResponseWithoutUsageIsCountedAsACallWithoutUsage(CapturedOutput output) {
        ChatModel model = model(response(null));
        ChatClient client = ChatClient.builder(model).defaultAdvisors(new OpenAiUsageAdvisor(tracker)).build();

        tracker.trackAssessment("university", () -> {
            client.prompt().user("explain").call().content();
            return UUID.randomUUID();
        });

        assertThat(output).contains("openaiCalls=1 openaiInputTokens=0", "openaiCallsWithoutUsage=1");
    }

    private static ChatModel model(ChatResponse response) {
        ChatModel model = mock(ChatModel.class);
        when(model.getOptions()).thenReturn(ChatOptions.builder().build());
        when(model.call(any(Prompt.class))).thenReturn(response);
        return model;
    }

    private static ChatResponse response(DefaultUsage usage) {
        var metadata = ChatResponseMetadata.builder().model("gpt-4o-mini");
        if (usage != null) {
            metadata.usage(usage);
        }
        return new ChatResponse(List.of(new Generation(new AssistantMessage("ok"))), metadata.build());
    }
}
