package com.chikere.futureai.narrative.alevel.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"prod", "dev-no-jev"})
public class OpenAiALevelNarrativeConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "openai")
    OpenAiALevelNarrativeGenerator openAiALevelNarrativeGenerator(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.chat.model:}") String configuredModelIdentifier) {
        return new OpenAiALevelNarrativeGenerator(
                chatClientBuilder.build(), objectMapper, configuredModelIdentifier);
    }
}
