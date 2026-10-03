package com.chikere.futureai.configuration;

import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeConfiguration;
import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.openai.OpenAiUniversityNarrativeConfiguration;
import com.chikere.futureai.narrative.university.openai.OpenAiUniversityNarrativeGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class OpenAiNarrativeWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(OpenAiProfessionalNarrativeConfiguration.class,
                    OpenAiUniversityNarrativeConfiguration.class,
                    OpenAiALevelNarrativeConfiguration.class)
            .withConfiguration(AutoConfigurations.of(ChatBuilderAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void registersAllNarrativesWhenChatIsOpenAiEvenIfBuilderIsAutoConfiguredLater() {
        runner.withPropertyValues("spring.ai.model.chat=openai")
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAiProfessionalNarrativeGenerator.class);
                    assertThat(context).hasSingleBean(OpenAiUniversityNarrativeGenerator.class);
                    assertThat(context).hasSingleBean(OpenAiALevelNarrativeGenerator.class);
                });
    }

    @Test
    void doesNotRegisterLiveNarrativesWhenChatIsDisabled() {
        runner.withPropertyValues("spring.ai.model.chat=none")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OpenAiProfessionalNarrativeGenerator.class);
                    assertThat(context).doesNotHaveBean(OpenAiUniversityNarrativeGenerator.class);
                    assertThat(context).doesNotHaveBean(OpenAiALevelNarrativeGenerator.class);
                });
    }

    @AutoConfiguration
    static class ChatBuilderAutoConfiguration {
        @Bean ChatClient.Builder chatClientBuilder() {
            return mock(ChatClient.Builder.class);
        }
    }
}
