package com.chikere.futureai.narrative.professional.paid.openai;

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

/** The real paid-narrative provider, in the same profiles and under the same switch as the role-level narrative. */
@Configuration(proxyBeanMethods = false)
@Profile({"prod", "dev-no-jev", "live-smoke"})
public class OpenAiProfessionalPaidNarrativeConfiguration {

    /** The journey's narrative model; absent, its default. Never Spring AI's global chat model. */
    private static final String MODEL = "${" + OpenAiNarrativeModelPolicy.PROFESSIONAL_PROPERTY + ":"
            + OpenAiNarrativeModelPolicy.PROFESSIONAL_DEFAULT + "}";

    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "openai")
    OpenAiProfessionalPaidNarrativeGenerator openAiProfessionalPaidNarrativeGenerator(
            ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper,
            @Value(MODEL) String configuredModelIdentifier,
            @Value("${app.professional.paid-narrative.openai.reasoning-effort:}") String configuredReasoningEffort) {
        // Empty reasoning effort: chosen from the model (none for GPT-6, otherwise the low custom temperature).
        String model = OpenAiNarrativeModelPolicy.require(Journey.PROFESSIONAL, configuredModelIdentifier);
        return new OpenAiProfessionalPaidNarrativeGenerator(chatClientBuilder.build(), objectMapper, model,
                Optional.ofNullable(configuredReasoningEffort));
    }
}
