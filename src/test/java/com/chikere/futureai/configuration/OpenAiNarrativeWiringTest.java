package com.chikere.futureai.configuration;

import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeConfiguration;
import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.paid.openai.OpenAiProfessionalPaidNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.paid.openai.OpenAiProfessionalPaidNarrativeGenerator;
import com.chikere.futureai.narrative.university.openai.OpenAiUniversityNarrativeConfiguration;
import com.chikere.futureai.narrative.university.openai.OpenAiUniversityNarrativeGenerator;
import com.chikere.futureai.narrative.university.paid.openai.OpenAiUniversityPaidNarrativeConfiguration;
import com.chikere.futureai.narrative.university.paid.openai.OpenAiUniversityPaidNarrativeGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

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
    void bothProfessionalNarrativesChooseRequestSettingsFromTheConfiguredModelAndReasoningEffort() {
        runner.withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-6-luna")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    for (var options : List.of(
                            context.getBean(OpenAiProfessionalNarrativeGenerator.class).requestOptions(),
                            context.getBean(OpenAiProfessionalPaidNarrativeGenerator.class).requestOptions())) {
                        assertThat(options.reasoningEffort()).contains("none");
                        assertThat(options.temperature()).isEmpty();
                    }
                });
        runner.withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-6-luna",
                        "app.professional.narrative.openai.reasoning-effort=low",
                        "app.professional.paid-narrative.openai.reasoning-effort=minimal")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context.getBean(OpenAiProfessionalNarrativeGenerator.class).requestOptions()
                            .reasoningEffort()).contains("low");
                    assertThat(context.getBean(OpenAiProfessionalPaidNarrativeGenerator.class).requestOptions()
                            .reasoningEffort()).contains("minimal");
                });
        runner.withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-4o-mini")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context.getBean(OpenAiProfessionalNarrativeGenerator.class).requestOptions()
                            .temperature()).contains(0.2);
                    assertThat(context.getBean(OpenAiProfessionalPaidNarrativeGenerator.class).requestOptions()
                            .temperature()).contains(0.2);
                });
    }

    @Test
    void theUniversityPaidNarrativeUsesTheSharedOpenAiModelWithNoUniversityOverride() throws IOException {
        // One shared model for every OpenAI narrative: OPENAI_MODEL, defaulting to gpt-4o-mini.
        Properties properties = new Properties();
        try (InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }
        assertThat(properties.getProperty("spring.ai.openai.chat.model")).isEqualTo("${OPENAI_MODEL:gpt-4o-mini}");
        assertThat(properties.stringPropertyNames()).noneMatch(name -> name.startsWith("app.university.paid-narrative"));

        var paid = runner.withUserConfiguration(OpenAiUniversityPaidNarrativeConfiguration.class);
        paid.withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-4o-mini")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    var university = context.getBean(OpenAiUniversityPaidNarrativeGenerator.class);
                    assertThat(university.modelIdentifier()).contains("gpt-4o-mini")
                            .isEqualTo(context.getBean(OpenAiProfessionalPaidNarrativeGenerator.class).modelIdentifier());
                    assertThat(university.requestOptions().temperature()).contains(0.2);
                    assertThat(university.requestOptions().reasoningEffort()).isEmpty();
                });
        paid.withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-6-luna")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    var university = context.getBean(OpenAiUniversityPaidNarrativeGenerator.class);
                    assertThat(university.modelIdentifier()).isEqualTo(Optional.of("gpt-6-luna"));
                    assertThat(university.requestOptions().reasoningEffort()).contains("none");
                    assertThat(university.requestOptions().temperature()).isEmpty();
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
