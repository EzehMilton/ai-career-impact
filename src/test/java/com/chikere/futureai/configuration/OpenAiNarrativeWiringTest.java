package com.chikere.futureai.configuration;

import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeConfiguration;
import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.paid.openai.OpenAiProfessionalPaidNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.paid.openai.OpenAiProfessionalPaidNarrativeGenerator;
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
                    OpenAiProfessionalPaidNarrativeConfiguration.class,
                    OpenAiUniversityNarrativeConfiguration.class,
                    OpenAiALevelNarrativeConfiguration.class)
            .withConfiguration(AutoConfigurations.of(ChatBuilderAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void registersAllNarrativesWhenChatIsOpenAiEvenIfBuilderIsAutoConfiguredLater() {
        runner.withPropertyValues("spring.ai.model.chat=openai")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("dev-no-jev"))
                .run(context -> {
                    assertThat(context).hasSingleBean(OpenAiProfessionalNarrativeGenerator.class);
                    assertThat(context).hasSingleBean(OpenAiProfessionalPaidNarrativeGenerator.class);
                    assertThat(context).hasSingleBean(OpenAiUniversityNarrativeGenerator.class);
                    assertThat(context).hasSingleBean(OpenAiALevelNarrativeGenerator.class);
                });
    }

    @Test
    void doesNotRegisterLiveNarrativesWhenChatIsDisabled() {
        runner.withPropertyValues("spring.ai.model.chat=none")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("dev-no-jev"))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(OpenAiProfessionalNarrativeGenerator.class);
                    assertThat(context).doesNotHaveBean(OpenAiProfessionalPaidNarrativeGenerator.class);
                    assertThat(context).doesNotHaveBean(OpenAiUniversityNarrativeGenerator.class);
                    assertThat(context).doesNotHaveBean(OpenAiALevelNarrativeGenerator.class);
                });
    }

    @Test
    void thePaidNarrativeProviderNeverExistsInTheDevProfileOrOrdinaryTests() {
        for (String profile : new String[] {"dev", "test"}) {
            runner.withPropertyValues("spring.ai.model.chat=openai")
                    .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                    .run(context -> assertThat(context).as(profile)
                            .doesNotHaveBean(OpenAiProfessionalPaidNarrativeGenerator.class));
        }
    }

    @AutoConfiguration
    static class ChatBuilderAutoConfiguration {
        @Bean ChatClient.Builder chatClientBuilder() {
            return mock(ChatClient.Builder.class);
        }
    }
}
