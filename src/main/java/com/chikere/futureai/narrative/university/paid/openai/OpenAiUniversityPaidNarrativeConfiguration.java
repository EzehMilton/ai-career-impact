package com.chikere.futureai.narrative.university.paid.openai;

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

/** The real University paid-narrative provider, in the same profiles and under the same switch as Professional's. */
@Configuration(proxyBeanMethods = false)
@Profile({"prod", "dev-no-jev", "live-smoke"})
public class OpenAiUniversityPaidNarrativeConfiguration {

    /** The journey's narrative model; absent, its default. Never Spring AI's global chat model. */
    private static final String MODEL = "${" + OpenAiNarrativeModelPolicy.UNIVERSITY_PROPERTY + ":"
            + OpenAiNarrativeModelPolicy.UNIVERSITY_DEFAULT + "}";

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "openai")
    OpenAiUniversityPaidNarrativeGenerator openAiUniversityPaidNarrativeGenerator(
            ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper,
            @Value(MODEL) String configuredModelIdentifier,
            @Value("${app.university.paid-narrative.openai.reasoning-effort:}") String configuredReasoningEffort) {
        String model = OpenAiNarrativeModelPolicy.require(Journey.UNIVERSITY, configuredModelIdentifier);
        return new OpenAiUniversityPaidNarrativeGenerator(chatClientBuilder.build(), objectMapper, model,
                Optional.ofNullable(configuredReasoningEffort));
    }
}
