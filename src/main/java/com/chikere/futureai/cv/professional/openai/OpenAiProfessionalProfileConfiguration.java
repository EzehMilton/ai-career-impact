package com.chikere.futureai.cv.professional.openai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("!local-fixture")
public class OpenAiProfessionalProfileConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "spring.ai.model", name = "chat", havingValue = "openai")
    OpenAiProfessionalProfileExtractor openAiProfessionalProfileExtractor(ChatClient.Builder builder) {
        return new OpenAiProfessionalProfileExtractor(builder.build());
    }
}
