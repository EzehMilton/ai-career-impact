package com.chikere.futureai.assessment.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.evidence.university.application.EvidenceSource;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceProvider;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceStore;
import com.chikere.futureai.evidence.university.fixture.LocalFixtureUniversityEvidenceConfiguration;
import com.chikere.futureai.evidence.university.fixture.LocalFixtureUniversityEvidenceProvider;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityConfiguration;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.judgment.typesafe.university.UniversityJevAutoConfiguration;
import com.chikere.futureai.judgment.typesafe.university.UniversityJevJudgmentService;
import com.chikere.futureai.narrative.university.application.NarrativeSource;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeStore;
import com.chikere.futureai.narrative.university.fixture.LocalFixtureUniversityNarrativeConfiguration;
import com.chikere.futureai.narrative.university.fixture.LocalFixtureUniversityNarrativeGenerator;
import com.chikere.futureai.narrative.university.openai.OpenAiUniversityNarrativeConfiguration;
import com.chikere.futureai.narrative.university.openai.OpenAiUniversityNarrativeGenerator;
import com.chikere.futureai.payment.university.application.UniversityPaymentGateway;
import com.chikere.futureai.payment.university.application.UniversityPurchaseStore;
import com.chikere.futureai.payment.university.fixture.LocalFixtureUniversityPaymentConfiguration;
import com.chikere.futureai.payment.university.fixture.LocalFixtureUniversityPaymentGateway;
import com.chikere.futureai.payment.university.fixture.LocalUniversityPaymentSimulator;
import com.chikere.futureai.payment.university.stripe.StripeUniversityPaymentConfiguration;
import com.chikere.futureai.payment.university.stripe.StripeUniversityPaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class UniversityProviderWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-test",
                    "app.university.report.base-url=http://localhost:8080", "app.judgment.provider=jev")
            .withConfiguration(AutoConfigurations.of(UniversityJevAutoConfiguration.class))
            .withUserConfiguration(
                    UniversityAssessmentConfiguration.class,
                    LocalFixtureUniversityConfiguration.class,
                    LocalFixtureUniversityNarrativeConfiguration.class,
                    LocalFixtureUniversityEvidenceConfiguration.class,
                    LocalFixtureUniversityPaymentConfiguration.class,
                    StripeUniversityPaymentConfiguration.class,
                    OpenAiUniversityNarrativeConfiguration.class)
            .withBean(TypeSafeClient.class, () -> mock(TypeSafeClient.class))
            .withBean(UniversityAssessmentStore.class, () -> mock(UniversityAssessmentStore.class))
            .withBean(UniversityNarrativeStore.class, () -> mock(UniversityNarrativeStore.class))
            .withBean(UniversityEvidenceStore.class, () -> mock(UniversityEvidenceStore.class))
            .withBean(UniversityPurchaseStore.class, () -> mock(UniversityPurchaseStore.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(ChatClient.Builder.class, () -> {
                ChatClient.Builder builder = mock(ChatClient.Builder.class);
                org.mockito.Mockito.when(builder.build()).thenReturn(mock(ChatClient.class));
                return builder;
            });

    @Test
    void devProfileSelectsOnlyFixtures() {
        contextRunner.withPropertyValues("app.university.fixture-id=computer_science")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(UniversityJudgmentProvider.class)).hasSize(1);
                    assertThat(context.getBean(UniversityJudgmentProvider.class))
                            .isInstanceOf(LocalFixtureUniversityJudgmentProvider.class);
                    assertThat(context).doesNotHaveBean(UniversityJevJudgmentService.class);

                    var result = context.getBean(UniversityAssessmentService.class).assess(
                            new UniversityAssessmentState("Computer Science",
                                    "Programming, algorithms, systems and a final-year project.",
                                    List.of("Algorithms"), List.of("Programming"), List.of(),
                                    Optional.empty(), Optional.empty()));
                    // New submissions are decided by university-decision-v2: a three-measure course profile.
                    assertThat(result.usesCourseProfile()).isTrue();
                    assertThat(result.decisionPolicyVersion())
                            .isEqualTo(com.chikere.futureai.decision.university.UniversityDecisionPolicyV2.POLICY_VERSION);
                    assertThat(result.profile().measures()).hasSize(3);

                    assertThat(context.getBean(UniversityNarrativeGenerator.class))
                            .isInstanceOf(LocalFixtureUniversityNarrativeGenerator.class);
                    assertThat(context.getBean(UniversityNarrativeGenerator.class).source())
                            .isEqualTo(NarrativeSource.LOCAL_FIXTURE);
                    assertThat(context).doesNotHaveBean(OpenAiUniversityNarrativeGenerator.class);
                    assertThat(context).hasSingleBean(UniversityEvidenceProvider.class);
                    assertThat(context.getBean(UniversityEvidenceProvider.class))
                            .isInstanceOf(LocalFixtureUniversityEvidenceProvider.class);
                    assertThat(context.getBean(UniversityEvidenceProvider.class).source())
                            .isEqualTo(EvidenceSource.LOCAL_FIXTURE);
                    assertThat(context).hasSingleBean(UniversityPaymentGateway.class);
                    assertThat(context.getBean(UniversityPaymentGateway.class))
                            .isInstanceOf(LocalFixtureUniversityPaymentGateway.class);
                    assertThat(context).hasSingleBean(LocalUniversityPaymentSimulator.class);
                    assertThat(context).doesNotHaveBean(StripeUniversityPaymentGateway.class);
                });
    }

    @Test
    void devProfileFailsClearlyWithoutAnExplicitSupportedFixtureId() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("app.university.fixture-id"));
        contextRunner.withPropertyValues("app.university.fixture-id=unknown")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Unsupported app.university.fixture-id"));
    }

    @Test
    void devNoJevAndProdActivateStripeAndFailClearlyWithoutItsConfiguration() {
        // dev-no-jev also requires a fixture-id because it activates the fixture Jev provider
        contextRunner
                .withPropertyValues("app.university.fixture-id=computer_science", "app.stripe.enabled=true")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev-no-jev"))
                .run(context -> assertThat(context.getStartupFailure())
                        .as("dev-no-jev should fail without Stripe key")
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Stripe secret key"));
        contextRunner
                .withPropertyValues("app.university.fixture-id=computer_science",
                        "app.stripe.enabled=true",
                        "app.stripe.secret-key=sk_test_not_called",
                        "app.stripe.university-webhook-secret=whsec_not_called")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev-no-jev"))
                .run(context -> assertThat(context.getStartupFailure())
                        .as("dev-no-jev should fail without price ID")
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Stripe university price ID"));
        contextRunner
                .withPropertyValues("app.university.fixture-id=computer_science",
                        "app.stripe.enabled=true",
                        "app.stripe.secret-key=sk_test_not_called",
                        "app.stripe.university-webhook-secret=whsec_not_called",
                        "app.university.report.stripe-price-id=price_university_not_called")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev-no-jev"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(UniversityPaymentGateway.class))
                            .isInstanceOf(StripeUniversityPaymentGateway.class);
                    assertThat(context).doesNotHaveBean(LocalFixtureUniversityPaymentGateway.class);
                });

        // prod does not use a fixture-id
        contextRunner.withPropertyValues("app.stripe.enabled=true")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context.getStartupFailure())
                        .as("prod should fail without Stripe key")
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Stripe secret key"));
        contextRunner.withPropertyValues("app.stripe.enabled=true",
                        "app.stripe.secret-key=sk_test_not_called",
                        "app.stripe.university-webhook-secret=whsec_not_called")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context.getStartupFailure())
                        .as("prod should fail without price ID")
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Stripe university price ID"));
        contextRunner.withPropertyValues("app.stripe.enabled=true",
                        "app.stripe.secret-key=sk_test_not_called",
                        "app.stripe.university-webhook-secret=whsec_not_called",
                        "app.university.report.stripe-price-id=price_university_not_called")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(UniversityPaymentGateway.class))
                            .isInstanceOf(StripeUniversityPaymentGateway.class);
                    assertThat(context).doesNotHaveBean(LocalFixtureUniversityPaymentGateway.class);
                });
    }
}
