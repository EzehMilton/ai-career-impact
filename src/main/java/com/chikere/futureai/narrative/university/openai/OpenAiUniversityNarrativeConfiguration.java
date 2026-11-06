package com.chikere.futureai.narrative.university.openai;

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
public class OpenAiUniversityNarrativeConfiguration {

    /** The journey's narrative model; absent, its default. Never Spring AI's global chat model. */
    private static final String MODEL = "${" + OpenAiNarrativeModelPolicy.UNIVERSITY_PROPERTY + ":"
            + OpenAiNarrativeModelPolicy.UNIVERSITY_DEFAULT + "}";

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "openai")
    OpenAiUniversityNarrativeGenerator openAiUniversityNarrativeGenerator(
            ChatClient.Builder chatClientBuilder,
            ObjectMapper objectMapper,
            @Value(MODEL) String configuredModelIdentifier) {
        String model = OpenAiNarrativeModelPolicy.require(Journey.UNIVERSITY, configuredModelIdentifier);
        return new OpenAiUniversityNarrativeGenerator(chatClientBuilder.build(), objectMapper, model);
    }
}
