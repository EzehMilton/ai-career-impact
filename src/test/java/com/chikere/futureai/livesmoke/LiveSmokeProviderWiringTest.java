package com.chikere.futureai.livesmoke;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelConfiguration;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalConfiguration;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityConfiguration;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJevAutoConfiguration;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJevJudgmentService;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevAutoConfiguration;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevJudgmentService;
import com.chikere.futureai.judgment.typesafe.university.UniversityJevAutoConfiguration;
import com.chikere.futureai.judgment.typesafe.university.UniversityJevJudgmentService;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.fixture.LocalFixtureALevelNarrativeConfiguration;
import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeConfiguration;
import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.fixture.LocalFixtureProfessionalNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import com.chikere.futureai.narrative.university.fixture.LocalFixtureUniversityNarrativeConfiguration;
import com.chikere.futureai.narrative.university.openai.OpenAiUniversityNarrativeConfiguration;
import com.chikere.futureai.narrative.university.openai.OpenAiUniversityNarrativeGenerator;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentGateway;
import com.chikere.futureai.payment.alevel.fixture.LocalFixtureALevelPaymentConfiguration;
import com.chikere.futureai.payment.alevel.stripe.StripeALevelPaymentConfiguration;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentGateway;
import com.chikere.futureai.payment.professional.fixture.LocalFixtureProfessionalPaymentConfiguration;
import com.chikere.futureai.payment.professional.stripe.StripeProfessionalPaymentConfiguration;
import com.chikere.futureai.payment.university.application.UniversityPaymentGateway;
import com.chikere.futureai.payment.university.fixture.LocalFixtureUniversityPaymentConfiguration;
import com.chikere.futureai.payment.university.stripe.StripeUniversityPaymentConfiguration;
import com.chikere.futureai.payment.professional.web.StripeWebhookController;
import com.chikere.futureai.payment.university.web.UniversityStripeWebhookController;
import com.chikere.futureai.payment.alevel.web.ALevelStripeWebhookController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which provider adapters the live-smoke profile selects, for all three journeys. The TypeSafe client and the
 * ChatClient builder are mocks, so no context here can reach Jev or OpenAI: the test proves the wiring only.
 */
class LiveSmokeProviderWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(context -> context.getEnvironment().setActiveProfiles("live-smoke"))
            .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-test",
                    "app.professional.fixture-id=senior_software_architect", "app.university.fixture-id=computer_science",
                    "app.a-level.fixture-id=maths_economics_geography")
            .withConfiguration(AutoConfigurations.of(ProfessionalJevAutoConfiguration.class,
                    UniversityJevAutoConfiguration.class, ALevelJevAutoConfiguration.class))
            .withUserConfiguration(
                    LocalFixtureProfessionalConfiguration.class, LocalFixtureUniversityConfiguration.class,
                    LocalFixtureALevelConfiguration.class,
                    LocalFixtureProfessionalNarrativeConfiguration.class, LocalFixtureUniversityNarrativeConfiguration.class,
                    LocalFixtureALevelNarrativeConfiguration.class,
                    OpenAiProfessionalNarrativeConfiguration.class, OpenAiUniversityNarrativeConfiguration.class,
                    OpenAiALevelNarrativeConfiguration.class,
                    LocalFixtureProfessionalPaymentConfiguration.class, LocalFixtureUniversityPaymentConfiguration.class,
                    LocalFixtureALevelPaymentConfiguration.class,
                    StripeProfessionalPaymentConfiguration.class, StripeUniversityPaymentConfiguration.class,
                    StripeALevelPaymentConfiguration.class,
                    StripeWebhookController.class, UniversityStripeWebhookController.class,
                    ALevelStripeWebhookController.class)
            .withBean(TypeSafeClient.class, () -> mock(TypeSafeClient.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ChatClient.Builder.class, () -> {
                ChatClient.Builder builder = mock(ChatClient.Builder.class);
                when(builder.build()).thenReturn(mock(ChatClient.class));
                return builder;
            });

    @Test
    void everyJourneyUsesTheRealJevAdapterAndNoFixtureJudgments() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(ProfessionalJudgmentProvider.class)).isInstanceOf(ProfessionalJevJudgmentService.class);
            assertThat(context.getBean(UniversityJudgmentProvider.class)).isInstanceOf(UniversityJevJudgmentService.class);
            assertThat(context.getBean(ALevelJudgmentProvider.class)).isInstanceOf(ALevelJevJudgmentService.class);
            assertThat(context.getBean(ProfessionalJudgmentProvider.class).source().name()).isEqualTo("JEV");
            assertThat(context.getBean(UniversityJudgmentProvider.class).source().name()).isEqualTo("JEV");
            assertThat(context.getBean(ALevelJudgmentProvider.class).source().name()).isEqualTo("JEV");
        });
    }

    @Test
    void everyJourneyUsesTheRealOpenAiAdapterAndNoFixtureNarratives() {
        runner.run(context -> {
            assertThat(context.getBean(ProfessionalNarrativeGenerator.class))
                    .isInstanceOf(OpenAiProfessionalNarrativeGenerator.class);
            assertThat(context.getBean(UniversityNarrativeGenerator.class))
                    .isInstanceOf(OpenAiUniversityNarrativeGenerator.class);
            assertThat(context.getBean(ALevelNarrativeGenerator.class)).isInstanceOf(OpenAiALevelNarrativeGenerator.class);
            // Exactly one generator per journey: there is no fixture to fall back to.
            assertThat(context.getBeansOfType(ProfessionalNarrativeGenerator.class)).hasSize(1);
            assertThat(context.getBeansOfType(UniversityNarrativeGenerator.class)).hasSize(1);
            assertThat(context.getBeansOfType(ALevelNarrativeGenerator.class)).hasSize(1);
        });
    }

    @Test
    void noStripeOrSimulatedPaymentGatewayOrWebhookExists() {
        runner.run(context -> {
            assertThat(context.getBeansOfType(ProfessionalPaymentGateway.class)).isEmpty();
            assertThat(context.getBeansOfType(UniversityPaymentGateway.class)).isEmpty();
            assertThat(context.getBeansOfType(ALevelPaymentGateway.class)).isEmpty();
            assertThat(context.getBeansOfType(StripeWebhookController.class)).isEmpty();
            assertThat(context.getBeansOfType(UniversityStripeWebhookController.class)).isEmpty();
            assertThat(context.getBeansOfType(ALevelStripeWebhookController.class)).isEmpty();
            assertThat(context.getBeanDefinitionNames()).filteredOn(name -> name.toLowerCase().contains("fixture"))
                    .isEmpty();
        });
    }
}
