package com.chikere.futureai.assessment.alevel.application;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceProvider;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceStore;
import com.chikere.futureai.evidence.alevel.application.EvidenceSource;
import com.chikere.futureai.evidence.alevel.fixture.LocalFixtureALevelEvidenceConfiguration;
import com.chikere.futureai.evidence.alevel.fixture.LocalFixtureALevelEvidenceProvider;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelConfiguration;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJevAutoConfiguration;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJevJudgmentService;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeStore;
import com.chikere.futureai.narrative.alevel.application.NarrativeSource;
import com.chikere.futureai.narrative.alevel.fixture.LocalFixtureALevelNarrativeConfiguration;
import com.chikere.futureai.narrative.alevel.fixture.LocalFixtureALevelNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeConfiguration;
import com.chikere.futureai.narrative.alevel.openai.OpenAiALevelNarrativeGenerator;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentGateway;
import com.chikere.futureai.payment.alevel.application.ALevelPurchaseStore;
import com.chikere.futureai.payment.alevel.fixture.LocalALevelPaymentSimulator;
import com.chikere.futureai.payment.alevel.fixture.LocalFixtureALevelPaymentConfiguration;
import com.chikere.futureai.payment.alevel.fixture.LocalFixtureALevelPaymentGateway;
import com.chikere.futureai.payment.alevel.stripe.StripeALevelPaymentConfiguration;
import com.chikere.futureai.payment.alevel.stripe.StripeALevelPaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ALevelProviderWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-test",
                    // Return URLs come from the one configured public base URL. A context
                    // runner does not read application.properties, so it is supplied here.
                    "app.a-level.report.base-url=http://localhost:8080")
            .withConfiguration(AutoConfigurations.of(ALevelJevAutoConfiguration.class))
            .withUserConfiguration(
                    ALevelAssessmentConfiguration.class,
                    LocalFixtureALevelConfiguration.class,
                    LocalFixtureALevelNarrativeConfiguration.class,
                    LocalFixtureALevelEvidenceConfiguration.class,
                    LocalFixtureALevelPaymentConfiguration.class,
                    StripeALevelPaymentConfiguration.class,
                    OpenAiALevelNarrativeConfiguration.class)
            .withBean(TypeSafeClient.class, () -> mock(TypeSafeClient.class))
            .withBean(ALevelAssessmentStore.class, () -> mock(ALevelAssessmentStore.class))
            .withBean(ALevelNarrativeStore.class, () -> mock(ALevelNarrativeStore.class))
            .withBean(ALevelEvidenceStore.class, () -> mock(ALevelEvidenceStore.class))
            .withBean(ALevelPurchaseStore.class, () -> mock(ALevelPurchaseStore.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ChatClient.Builder.class, () -> {
                ChatClient.Builder builder = mock(ChatClient.Builder.class);
                org.mockito.Mockito.when(builder.build()).thenReturn(mock(ChatClient.class));
                return builder;
            });

    @Test
    void normalModeSelectsOnlyTheJevAdapterAndNoPaymentOrEvidenceProvider() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBeansOfType(ALevelJudgmentProvider.class)).hasSize(1);
            assertThat(context.getBean(ALevelJudgmentProvider.class))
                    .isInstanceOf(ALevelJevJudgmentService.class);
            assertThat(context.getBean(ALevelJudgmentProvider.class).source())
                    .isEqualTo(AssessmentSource.JEV);
            assertThat(context).doesNotHaveBean(LocalFixtureALevelJudgmentProvider.class);
            assertThat(context).hasSingleBean(ALevelNarrativeGenerator.class);
            assertThat(context.getBean(ALevelNarrativeGenerator.class))
                    .isInstanceOf(OpenAiALevelNarrativeGenerator.class);
            assertThat(context.getBean(ALevelNarrativeGenerator.class).modelIdentifier())
                    .contains("gpt-test");
            assertThat(context).doesNotHaveBean(ALevelEvidenceProvider.class);
            assertThat(context).doesNotHaveBean(ALevelPaymentGateway.class);
        });
    }

    @Test
    void localFixtureModeSelectsOnlyFixturesEvenWhenATypeSafeClientExists() {
        contextRunner.withPropertyValues("app.a-level.fixture-id=maths_economics_geography")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local-fixture"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(ALevelJudgmentProvider.class)).hasSize(1);
                    assertThat(context.getBean(ALevelJudgmentProvider.class))
                            .isInstanceOf(LocalFixtureALevelJudgmentProvider.class);
                    assertThat(context).doesNotHaveBean(ALevelJevJudgmentService.class);

                    var result = context.getBean(ALevelAssessmentService.class).assess(
                            new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                                    Map.of(), List.of(), List.of(), List.of(),
                                    Optional.empty(), Optional.empty()));
                    assertThat(result.decision().measure(ALevelMeasure.FUTURE_OPTIONALITY)
                            .orElseThrow().indicator()).isBetween(8.31, 8.32);

                    assertThat(context.getBean(ALevelNarrativeGenerator.class))
                            .isInstanceOf(LocalFixtureALevelNarrativeGenerator.class);
                    assertThat(context.getBean(ALevelNarrativeGenerator.class).source())
                            .isEqualTo(NarrativeSource.LOCAL_FIXTURE);
                    assertThat(context).doesNotHaveBean(OpenAiALevelNarrativeGenerator.class);
                    assertThat(context).hasSingleBean(ALevelEvidenceProvider.class);
                    assertThat(context.getBean(ALevelEvidenceProvider.class))
                            .isInstanceOf(LocalFixtureALevelEvidenceProvider.class);
                    assertThat(context.getBean(ALevelEvidenceProvider.class).source())
                            .isEqualTo(EvidenceSource.LOCAL_FIXTURE);
                    assertThat(context).hasSingleBean(ALevelPaymentGateway.class);
                    assertThat(context.getBean(ALevelPaymentGateway.class))
                            .isInstanceOf(LocalFixtureALevelPaymentGateway.class);
                    assertThat(context).hasSingleBean(LocalALevelPaymentSimulator.class);
                    assertThat(context).doesNotHaveBean(StripeALevelPaymentGateway.class);
                });
    }

    @Test
    void localFixtureModeFailsClearlyWithoutAnExplicitSupportedId() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("local-fixture"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("app.a-level.fixture-id"));
        contextRunner.withPropertyValues("app.a-level.fixture-id=unknown")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("local-fixture"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Unsupported app.a-level.fixture-id"));
    }

    @Test
    void livePaymentProfileSelectsOnlyStripeAndFailsClearlyWithoutItsOwnConfiguration() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("live-payment"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Stripe secret key"));
        contextRunner.withPropertyValues("app.stripe.secret-key=sk_test_not_called",
                        "app.stripe.a-level-webhook-secret=whsec_not_called")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("live-payment"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Stripe A-level price ID"));
        contextRunner.withPropertyValues("app.stripe.secret-key=sk_test_not_called",
                        "app.stripe.a-level-webhook-secret=whsec_not_called",
                        "app.a-level.report.stripe-price-id=price_a_level_not_called")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("live-payment"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ALevelPaymentGateway.class);
                    assertThat(context.getBean(ALevelPaymentGateway.class))
                            .isInstanceOf(StripeALevelPaymentGateway.class);
                    assertThat(context.getBean(ALevelPaymentGateway.class).priceConfigurationKey())
                            .isEqualTo("price_a_level_not_called");
                    assertThat(context).doesNotHaveBean(LocalFixtureALevelPaymentGateway.class);
                });
    }
}
