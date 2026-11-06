package com.chikere.futureai.configuration;

import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeConfiguration;
import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.paid.openai.OpenAiALevelPaidNarrativeConfiguration;
import com.chikere.futureai.narrative.alevel.paid.openai.OpenAiALevelPaidNarrativeGenerator;
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
    void bothProfessionalNarrativesUseTheProfessionalModelAndKeepTheirReasoningEffortSettings() {
        runner.withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-4o-mini")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    for (var options : List.of(
                            context.getBean(OpenAiProfessionalNarrativeGenerator.class).requestOptions(),
                            context.getBean(OpenAiProfessionalPaidNarrativeGenerator.class).requestOptions())) {
                        assertThat(options.model()).contains("gpt-6.1-sol");
                        assertThat(options.reasoningEffort()).contains("none");
                        assertThat(options.temperature()).isEmpty();
                    }
                });
        runner.withPropertyValues("spring.ai.model.chat=openai",
                        "app.professional.narrative.openai.reasoning-effort=low",
                        "app.professional.paid-narrative.openai.reasoning-effort=minimal")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context.getBean(OpenAiProfessionalNarrativeGenerator.class).requestOptions()
                            .reasoningEffort()).contains("low");
                    assertThat(context.getBean(OpenAiProfessionalPaidNarrativeGenerator.class).requestOptions()
                            .reasoningEffort()).contains("minimal");
                });
    }

    @Test
    void everyJourneyNarrativeUsesItsOwnModelAndNeverTheGlobalChatModel() throws IOException {
        Properties properties = new Properties();
        try (InputStream in = java.nio.file.Files.newInputStream(
                java.nio.file.Path.of("src/main/resources/application.properties"))) {
            properties.load(in);
        }
        // The global model is only Spring AI's infrastructure default; each journey has its own setting.
        assertThat(properties.getProperty("spring.ai.openai.chat.model")).isEqualTo("${OPENAI_MODEL:gpt-6-luna}");
        assertThat(properties.getProperty("app.narrative.university.model"))
                .isEqualTo("${UNIVERSITY_NARRATIVE_MODEL:gpt-6-luna}");
        assertThat(properties.getProperty("app.narrative.a-level.model")).isEqualTo("${A_LEVEL_NARRATIVE_MODEL:gpt-6-luna}");
        assertThat(properties.getProperty("app.narrative.professional.model"))
                .isEqualTo("${PROFESSIONAL_NARRATIVE_MODEL:gpt-6.1-sol}");

        runner.withUserConfiguration(OpenAiUniversityPaidNarrativeConfiguration.class,
                        OpenAiALevelPaidNarrativeConfiguration.class)
                .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-4o-mini")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    var university = List.of(
                            context.getBean(OpenAiUniversityNarrativeGenerator.class).requestOptions(),
                            context.getBean(OpenAiUniversityPaidNarrativeGenerator.class).requestOptions());
                    var aLevel = List.of(context.getBean(OpenAiALevelNarrativeGenerator.class).requestOptions(),
                            context.getBean(OpenAiALevelPaidNarrativeGenerator.class).requestOptions());
                    assertThat(university).allSatisfy(options -> assertThat(options.model()).contains("gpt-6-luna"));
                    assertThat(aLevel).allSatisfy(options -> assertThat(options.model()).contains("gpt-6-luna"));
                    assertThat(context.getBean(OpenAiUniversityPaidNarrativeGenerator.class).modelIdentifier())
                            .isEqualTo(Optional.of("gpt-6-luna"));
                    assertThat(context.getBean(OpenAiProfessionalPaidNarrativeGenerator.class).modelIdentifier())
                            .isEqualTo(Optional.of("gpt-6.1-sol"));
                    for (var options : List.of(university.get(0), university.get(1), aLevel.get(0), aLevel.get(1))) {
                        assertThat(options.reasoningEffort()).contains("none");
                        assertThat(options.temperature()).isEmpty();
                    }
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
