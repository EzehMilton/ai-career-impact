package com.chikere.futureai.narrative.university.openai;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!local-fixture")
public class OpenAiUniversityNarrativeConfiguration {

    @Bean
    @ConditionalOnBean(ChatClient.Builder.class)
    OpenAiUniversityNarrativeGenerator openAiUniversityNarrativeGenerator(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            @Value("${spring.ai.openai.chat.model:}") String configuredModelIdentifier) {
        return new OpenAiUniversityNarrativeGenerator(
                chatClientBuilder.build(), objectMapper, configuredModelIdentifier);
    }
}
