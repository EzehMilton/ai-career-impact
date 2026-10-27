package com.chikere.futureai.narrative.professional.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.Optional;

@Configuration(proxyBeanMethods = false)
@Profile({"prod", "dev-no-jev", "live-smoke"})
public class OpenAiProfessionalNarrativeConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "openai")
    OpenAiProfessionalNarrativeGenerator openAiProfessionalNarrativeGenerator(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.chat.model:}") String configuredModelIdentifier,
            @Value("${app.professional.narrative.openai.reasoning-effort:}") String configuredReasoningEffort) {
        // Empty reasoning effort: chosen from the model (none for GPT-6, otherwise the low custom temperature).
        return new OpenAiProfessionalNarrativeGenerator(
                chatClientBuilder.build(), objectMapper, configuredModelIdentifier,
                Optional.ofNullable(configuredReasoningEffort));
    }
}
