package com.chikere.futureai.narrative.university.paid.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

/** The real University paid-narrative provider, in the same profiles and under the same switch as Professional's. */
@Configuration(proxyBeanMethods = false)
@Profile({"prod", "dev-no-jev", "live-smoke"})
public class OpenAiUniversityPaidNarrativeConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "openai")
    OpenAiUniversityPaidNarrativeGenerator openAiUniversityPaidNarrativeGenerator(
            ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper,
            @Value("${spring.ai.openai.chat.model:}") String configuredModelIdentifier,
            @Value("${app.university.paid-narrative.openai.reasoning-effort:}") String configuredReasoningEffort) {
        return new OpenAiUniversityPaidNarrativeGenerator(chatClientBuilder.build(), objectMapper,
                configuredModelIdentifier, Optional.ofNullable(configuredReasoningEffort));
    }
}
