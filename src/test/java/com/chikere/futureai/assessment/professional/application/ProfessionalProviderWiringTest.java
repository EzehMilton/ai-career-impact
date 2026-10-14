package com.chikere.futureai.assessment.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.cv.professional.application.ProfessionalProfileExtractor;
import com.chikere.futureai.cv.professional.fixture.LocalFixtureProfessionalProfileConfiguration;
import com.chikere.futureai.cv.professional.fixture.LocalFixtureProfessionalProfileExtractor;
import com.chikere.futureai.cv.professional.openai.OpenAiProfessionalProfileConfiguration;
import com.chikere.futureai.cv.professional.openai.OpenAiProfessionalProfileExtractor;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalConfiguration;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevAutoConfiguration;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevJudgmentService;
import com.chikere.futureai.narrative.professional.application.NarrativeSource;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeStore;
import com.chikere.futureai.narrative.professional.fixture.LocalFixtureProfessionalNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.fixture.LocalFixtureProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chikere.futureai.evidence.professional.application.EvidenceSource;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceProvider;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceStore;
import com.chikere.futureai.evidence.professional.fixture.LocalFixtureProfessionalEvidenceConfiguration;
import com.chikere.futureai.evidence.professional.fixture.LocalFixtureProfessionalEvidenceProvider;
import com.chikere.futureai.evidence.professional.nomis.NomisProfessionalEvidenceConfiguration;
import com.chikere.futureai.evidence.professional.nomis.NomisProfessionalEvidenceProvider;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationStore;
import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassifier;
import com.chikere.futureai.occupation.professional.configured.ConfiguredProfessionalOccupationConfiguration;
import com.chikere.futureai.occupation.professional.configured.ConfiguredProfessionalOccupationClassifier;
import com.chikere.futureai.occupation.professional.fixture.LocalFixtureProfessionalOccupationConfiguration;
import com.chikere.futureai.occupation.professional.fixture.LocalFixtureProfessionalOccupationClassifier;
import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.payment.professional.fixture.*;
import com.chikere.futureai.payment.professional.stripe.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.client.RestClient;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProfessionalProviderWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-test",
                    "app.professional.report.base-url=http://localhost:8080")
            .withConfiguration(AutoConfigurations.of(ProfessionalJevAutoConfiguration.class))
            .withUserConfiguration(
                    ProfessionalAssessmentConfiguration.class,
                    LocalFixtureProfessionalConfiguration.class,
                    LocalFixtureProfessionalNarrativeConfiguration.class,
                    LocalFixtureProfessionalEvidenceConfiguration.class,
                    NomisProfessionalEvidenceConfiguration.class,
                    LocalFixtureProfessionalOccupationConfiguration.class,
                    ConfiguredProfessionalOccupationConfiguration.class,
                    ProfessionalPaymentConfiguration.class,
                    LocalFixtureProfessionalPaymentConfiguration.class,
                    StripeProfessionalPaymentConfiguration.class,
                    LocalFixtureProfessionalProfileConfiguration.class,
                    OpenAiProfessionalProfileConfiguration.class,
                    OpenAiProfessionalNarrativeConfiguration.class)
            .withBean(TypeSafeClient.class, () -> mock(TypeSafeClient.class))
            .withBean(ProfessionalAssessmentStore.class, () -> mock(ProfessionalAssessmentStore.class))
            .withBean(ProfessionalNarrativeStore.class, () -> mock(ProfessionalNarrativeStore.class))
            .withBean(ProfessionalEvidenceStore.class, () -> mock(ProfessionalEvidenceStore.class))
            .withBean(ProfessionalOccupationClassificationStore.class,
                    () -> mock(ProfessionalOccupationClassificationStore.class))
            .withBean(ProfessionalPurchaseStore.class, () -> mock(ProfessionalPurchaseStore.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(ChatClient.Builder.class, () -> {
                ChatClient.Builder builder = mock(ChatClient.Builder.class);
                org.mockito.Mockito.when(builder.build()).thenReturn(mock(ChatClient.class));
                return builder;
            });

    @Test
    void devProfileSelectsOnlyFixtures() {
        contextRunner.withPropertyValues("app.professional.fixture-id=senior_software_architect")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(ProfessionalJudgmentProvider.class)).hasSize(1);
                    assertThat(context.getBean(ProfessionalJudgmentProvider.class))
                            .isInstanceOf(LocalFixtureProfessionalJudgmentProvider.class);
                    assertThat(context.getBean(ProfessionalJudgmentProvider.class).source())
                            .isEqualTo(AssessmentSource.LOCAL_FIXTURE);

                    var result = context.getBean(ProfessionalAssessmentService.class).assess(
                            new ProfessionalAssessmentState("Software Architect", "Designs complex systems",
                                    List.of("Design systems"), List.of("Architecture"), OptionalInt.empty()));
                    assertThat(result.decision().measure(ProfessionalMeasure.AI_EXPOSURE).orElseThrow().indicator())
                            .isBetween(4.80, 4.82);

                    assertThat(context).doesNotHaveBean(ProfessionalJevJudgmentService.class);
                    assertThat(context.getBean(ProfessionalNarrativeGenerator.class))
                            .isInstanceOf(LocalFixtureProfessionalNarrativeGenerator.class);
                    assertThat(context.getBean(ProfessionalNarrativeGenerator.class).source())
                            .isEqualTo(NarrativeSource.LOCAL_FIXTURE);
                    assertThat(context).doesNotHaveBean(OpenAiProfessionalNarrativeGenerator.class);
                    assertThat(context.getBean(ProfessionalProfileExtractor.class))
                            .isInstanceOf(LocalFixtureProfessionalProfileExtractor.class);
                    assertThat(context).doesNotHaveBean(OpenAiProfessionalProfileExtractor.class);
                    assertThat(context.getBean(ProfessionalEvidenceProvider.class))
                            .isInstanceOf(LocalFixtureProfessionalEvidenceProvider.class);
                    assertThat(context.getBean(ProfessionalEvidenceProvider.class).source())
                            .isEqualTo(EvidenceSource.LOCAL_FIXTURE);
                    assertThat(context.getBean(ProfessionalOccupationClassifier.class))
                            .isInstanceOf(LocalFixtureProfessionalOccupationClassifier.class);
                    assertThat(context.getBean(ProfessionalPaymentGateway.class))
                            .isInstanceOf(LocalFixtureProfessionalPaymentGateway.class);
                    assertThat(context).hasSingleBean(LocalProfessionalPaymentSimulator.class);
                    assertThat(context).doesNotHaveBean(NomisProfessionalEvidenceProvider.class);
                    assertThat(context).doesNotHaveBean(StripeProfessionalPaymentGateway.class);
                });
    }

    @Test
    void devProfileFailsClearlyWithoutAnExplicitSupportedFixtureId() {
        contextRunner.withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("app.professional.fixture-id"));
        contextRunner.withPropertyValues("app.professional.fixture-id=unknown")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Unsupported app.professional.fixture-id"));
    }

    @Test
    void devNoJevProfileSelectsFixtureJevButRealOpenAiAndStripe() {
        contextRunner.withPropertyValues(
                        "app.professional.fixture-id=senior_software_architect",
                        "app.stripe.secret-key=sk_test_not_called",
                        "app.stripe.webhook-secret=whsec_not_called",
                        "app.professional.report.stripe-price-id=price_not_called")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev-no-jev"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ProfessionalJudgmentProvider.class))
                            .isInstanceOf(LocalFixtureProfessionalJudgmentProvider.class);
                    assertThat(context).doesNotHaveBean(ProfessionalJevJudgmentService.class);
                    assertThat(context.getBean(ProfessionalNarrativeGenerator.class))
                            .isInstanceOf(OpenAiProfessionalNarrativeGenerator.class);
                    assertThat(context.getBean(ProfessionalNarrativeGenerator.class).source())
                            .isEqualTo(NarrativeSource.OPENAI);
                    assertThat(context).doesNotHaveBean(LocalFixtureProfessionalNarrativeGenerator.class);
                    assertThat(context.getBean(ProfessionalProfileExtractor.class))
                            .isInstanceOf(OpenAiProfessionalProfileExtractor.class);
                    assertThat(context).doesNotHaveBean(LocalFixtureProfessionalProfileExtractor.class);
                    assertThat(context.getBean(ProfessionalEvidenceProvider.class))
                            .isInstanceOf(LocalFixtureProfessionalEvidenceProvider.class);
                    assertThat(context.getBean(ProfessionalEvidenceProvider.class).source())
                            .isEqualTo(EvidenceSource.LOCAL_FIXTURE);
                    assertThat(context).doesNotHaveBean(NomisProfessionalEvidenceProvider.class);
                    assertThat(context.getBean(ProfessionalPaymentGateway.class))
                            .isInstanceOf(StripeProfessionalPaymentGateway.class);
                    assertThat(context).doesNotHaveBean(LocalFixtureProfessionalPaymentGateway.class);
                });
    }

    @Test
    void prodProfileSelectsAllRealServicesAndNoFixtures() {
        contextRunner.withPropertyValues(
                        "app.stripe.secret-key=sk_test_not_called",
                        "app.stripe.webhook-secret=whsec_not_called",
                        "app.professional.report.stripe-price-id=price_not_called",
                        "app.professional.evidence.classified-profession=Software Architect",
                        "app.professional.evidence.canonical-occupation=IT architects",
                        "app.professional.evidence.soc2020-code=2133")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(ProfessionalJudgmentProvider.class))
                            .isInstanceOf(ProfessionalJevJudgmentService.class);
                    assertThat(context.getBean(ProfessionalJudgmentProvider.class).source())
                            .isEqualTo(AssessmentSource.JEV);
                    assertThat(context).doesNotHaveBean(LocalFixtureProfessionalJudgmentProvider.class);
                    assertThat(context.getBean(ProfessionalNarrativeGenerator.class))
                            .isInstanceOf(OpenAiProfessionalNarrativeGenerator.class);
                    assertThat(context).doesNotHaveBean(LocalFixtureProfessionalNarrativeGenerator.class);
                    assertThat(context.getBean(ProfessionalProfileExtractor.class))
                            .isInstanceOf(OpenAiProfessionalProfileExtractor.class);
                    assertThat(context).doesNotHaveBean(LocalFixtureProfessionalProfileExtractor.class);
                    assertThat(context.getBean(ProfessionalEvidenceProvider.class))
                            .isInstanceOf(NomisProfessionalEvidenceProvider.class);
                    assertThat(context.getBean(ProfessionalEvidenceProvider.class).source())
                            .isEqualTo(EvidenceSource.LIVE);
                    assertThat(context).doesNotHaveBean(LocalFixtureProfessionalEvidenceProvider.class);
                    assertThat(context.getBean(ProfessionalOccupationClassifier.class))
                            .isInstanceOf(ConfiguredProfessionalOccupationClassifier.class);
                    assertThat(context).doesNotHaveBean(LocalFixtureProfessionalOccupationClassifier.class);
                    assertThat(context.getBean(ProfessionalPaymentGateway.class))
                            .isInstanceOf(StripeProfessionalPaymentGateway.class);
                    assertThat(context).doesNotHaveBean(LocalFixtureProfessionalPaymentGateway.class);
                });
    }

    @Test
    void stripeFailsClearlyWithoutConfigurationUnderProd() {
        contextRunner
                .withPropertyValues(
                        "app.professional.evidence.classified-profession=Software Architect",
                        "app.professional.evidence.canonical-occupation=IT architects",
                        "app.professional.evidence.soc2020-code=2133")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Stripe secret key"));
    }

    @Test
    void stripeFailsClearlyWithoutConfigurationUnderDevNoJev() {
        contextRunner
                .withPropertyValues("app.professional.fixture-id=senior_software_architect")
                .withInitializer(context -> context.getEnvironment().setActiveProfiles("dev-no-jev"))
                .run(context -> assertThat(context.getStartupFailure())
                        .hasRootCauseInstanceOf(IllegalArgumentException.class)
                        .hasStackTraceContaining("Stripe secret key"));
    }
}
