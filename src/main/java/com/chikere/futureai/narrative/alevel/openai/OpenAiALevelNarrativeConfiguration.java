package com.chikere.futureai.narrative.alevel.openai;

import com.chikere.futureai.narrative.openai.OpenAiNarrativeModelPolicy;
import com.chikere.futureai.narrative.openai.OpenAiNarrativeModelPolicy.Journey;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"prod", "dev-no-jev", "live-smoke"})
public class OpenAiALevelNarrativeConfiguration {

    /** The journey's narrative model; absent, its default. Never Spring AI's global chat model. */
    private static final String MODEL = "${" + OpenAiNarrativeModelPolicy.A_LEVEL_PROPERTY + ":"
            + OpenAiNarrativeModelPolicy.A_LEVEL_DEFAULT + "}";

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "openai")
    OpenAiALevelNarrativeGenerator openAiALevelNarrativeGenerator(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            @Value(MODEL) String configuredModelIdentifier) {
        String model = OpenAiNarrativeModelPolicy.require(Journey.A_LEVEL, configuredModelIdentifier);
        return new OpenAiALevelNarrativeGenerator(chatClientBuilder.build(), objectMapper, model);
    }
}
