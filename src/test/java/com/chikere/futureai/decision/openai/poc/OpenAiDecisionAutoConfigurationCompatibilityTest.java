package com.chikere.futureai.decision.openai.poc;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiDecisionAutoConfiguration;
import org.springframework.ai.openai.OpenAiDecisionModel;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the Spring Boot 4.0.8 / Spring AI 2.1.0-RC2 Decisions auto-configuration boundary. */
class OpenAiDecisionAutoConfigurationCompatibilityTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpenAiDecisionAutoConfiguration.class));

    @Test
    void configuresGpt6LunaFromTheExistingOpenAiKeyWhenThePocIsEnabled() {
        contextRunner.withPropertyValues(
                        "spring.ai.model.decision=openai",
                        "spring.ai.openai.api-key=test-only-openai-key",
                        "spring.ai.openai.decision.model=gpt-6-luna")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAiDecisionModel.class);
                    assertThat(context.getBean(OpenAiDecisionModel.class).getOptions().getModel())
                            .isEqualTo("gpt-6-luna");
                    assertThat(context.getBean(OpenAiCommonProperties.class).getApiKey())
                            .isEqualTo("test-only-openai-key");
                });
    }

    @Test
    void doesNotConfigureADecisionModelWhenThePocIsDisabled() {
        contextRunner.withPropertyValues("spring.ai.model.decision=none")
                .run(context -> assertThat(context).doesNotHaveBean(OpenAiDecisionModel.class));
    }
}
