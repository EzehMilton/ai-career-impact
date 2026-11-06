package com.chikere.futureai.narrative.alevel.paid.openai;

import com.chikere.futureai.narrative.openai.OpenAiNarrativeModelPolicy;
import com.chikere.futureai.narrative.openai.OpenAiNarrativeModelPolicy.Journey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

/** The real A-level paid-narrative provider, in the same profiles and under the same switch as Professional's. */
@Configuration(proxyBeanMethods = false)
@Profile({"prod", "dev-no-jev", "live-smoke"})
public class OpenAiALevelPaidNarrativeConfiguration {

    /** The journey's narrative model; absent, its default. Never Spring AI's global chat model. */
    private static final String MODEL = "${" + OpenAiNarrativeModelPolicy.A_LEVEL_PROPERTY + ":"
            + OpenAiNarrativeModelPolicy.A_LEVEL_DEFAULT + "}";

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "openai")
    OpenAiALevelPaidNarrativeGenerator openAiALevelPaidNarrativeGenerator(
            ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper,
            @Value(MODEL) String configuredModelIdentifier,
            @Value("${app.a-level.paid-narrative.openai.reasoning-effort:}") String configuredReasoningEffort) {
        String model = OpenAiNarrativeModelPolicy.require(Journey.A_LEVEL, configuredModelIdentifier);
        return new OpenAiALevelPaidNarrativeGenerator(chatClientBuilder.build(), objectMapper, model,
                Optional.ofNullable(configuredReasoningEffort));
    }
}
