package com.chikere.futureai.narrative.professional.paid.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** The real paid-narrative provider, in the same profiles and under the same switch as the role-level narrative. */
@Configuration(proxyBeanMethods = false)
@Profile({"prod", "dev-no-jev", "live-smoke"})
public class OpenAiProfessionalPaidNarrativeConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "openai")
    OpenAiProfessionalPaidNarrativeGenerator openAiProfessionalPaidNarrativeGenerator(
            ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper,
            @Value("${spring.ai.openai.chat.model:}") String configuredModelIdentifier) {
        return new OpenAiProfessionalPaidNarrativeGenerator(chatClientBuilder.build(), objectMapper,
                configuredModelIdentifier);
    }
}
