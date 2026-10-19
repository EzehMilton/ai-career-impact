package com.chikere.futureai.cost;

import org.springframework.ai.chat.client.ChatClientCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires cost estimation. The customizer adds {@link OpenAiUsageAdvisor} to Spring AI's shared
 * {@code ChatClient.Builder}, so every OpenAI adapter is covered without changing any of them.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ProviderCostProperties.class)
public class ProviderCostConfiguration {

    @Bean
    ProviderCostTracker providerCostTracker(ProviderCostProperties rates) {
        return new ProviderCostTracker(rates);
    }

    @Bean
    ChatClientCustomizer providerCostChatClientCustomizer(ProviderCostTracker tracker) {
        return builder -> builder.defaultAdvisors(new OpenAiUsageAdvisor(tracker));
    }
}
