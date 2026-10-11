package com.chikere.futureai.cv.professional.openai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile("!local-fixture | live-cv-extraction")
public class OpenAiProfessionalProfileConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "openai")
    OpenAiProfessionalProfileExtractor openAiProfessionalProfileExtractor(ChatClient.Builder builder,
            ObjectProvider<Clock> clock,
            @Value("${app.professional.cv.maximum-extraction-characters:"
                    + OpenAiProfessionalProfileExtractor.DEFAULT_MAXIMUM_EXTRACTION_CHARACTERS + "}")
            int maximumExtractionCharacters) {
        return new OpenAiProfessionalProfileExtractor(builder.build(), clock.getIfAvailable(Clock::systemUTC),
                maximumExtractionCharacters);
    }
}
