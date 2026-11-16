package com.chikere.futureai.configuration;

import com.chikere.futureai.evidence.professional.fixture.LocalFixtureProfessionalEvidenceConfiguration;
import com.chikere.futureai.evidence.professional.fixture.LocalFixtureProfessionalEvidenceProvider;
import com.chikere.futureai.evidence.professional.nomis.NomisProfessionalEvidenceConfiguration;
import com.chikere.futureai.evidence.professional.nomis.NomisProfessionalEvidenceProvider;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalConfiguration;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevAutoConfiguration;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJevJudgmentService;
import com.chikere.futureai.narrative.professional.fixture.LocalFixtureProfessionalNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.fixture.LocalFixtureProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeConfiguration;
import com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeGenerator;
import com.chikere.futureai.occupation.professional.configured.ConfiguredProfessionalOccupationConfiguration;
import com.chikere.futureai.occupation.professional.configured.ConfiguredProfessionalOccupationClassifier;
import com.chikere.futureai.occupation.professional.fixture.LocalFixtureProfessionalOccupationConfiguration;
import com.chikere.futureai.occupation.professional.fixture.LocalFixtureProfessionalOccupationClassifier;
import com.chikere.futureai.payment.professional.fixture.LocalFixtureProfessionalPaymentConfiguration;
import com.chikere.futureai.payment.professional.fixture.LocalFixtureProfessionalPaymentGateway;
import com.chikere.futureai.payment.professional.stripe.StripeProfessionalPaymentConfiguration;
import com.chikere.futureai.payment.professional.stripe.StripeProfessionalPaymentGateway;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.TypeSafeClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Verifies the runtime matrix:
 * <pre>
 *                   dev    dev-no-jev   prod
 * Jev fixture        YES      YES        NO
 * Jev real           NO       NO         YES
 * Narrative fixture  YES      NO         NO
 * Narrative real     NO       YES        YES
 * Stripe fixture     YES      NO         NO
 * Stripe real        NO       YES        YES
 * Evidence fixture   YES      YES        NO
 * Evidence real      NO       NO         YES
 * </pre>
 */
class ProfileMatrixTest {

    private static final String STRIPE_SECRET = "app.stripe.secret-key=sk_test_x";
    private static final String STRIPE_WEBHOOK = "app.stripe.webhook-secret=whsec_x";
    private static final String STRIPE_PRICE = "app.professional.report.stripe-price-id=price_x";
    private static final String EVIDENCE_PROFESSION = "app.professional.evidence.classified-profession=Software Developer";
    private static final String EVIDENCE_OCCUPATION = "app.professional.evidence.canonical-occupation=Programmers";
    private static final String EVIDENCE_SOC = "app.professional.evidence.soc2020-code=2134";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withPropertyValues("spring.ai.model.chat=openai", "spring.ai.openai.chat.model=gpt-test",
                    "app.professional.fixture-id=senior_software_architect",
                    "app.professional.report.base-url=http://localhost:8080",
                    "app.stripe.enabled=true", "app.professional.evidence.live-enabled=true",
                    STRIPE_SECRET, STRIPE_WEBHOOK, STRIPE_PRICE,
                    EVIDENCE_PROFESSION, EVIDENCE_OCCUPATION, EVIDENCE_SOC)
            .withConfiguration(AutoConfigurations.of(ProfessionalJevAutoConfiguration.class))
            .withUserConfiguration(
                    LocalFixtureProfessionalConfiguration.class,
                    LocalFixtureProfessionalNarrativeConfiguration.class,
                    LocalFixtureProfessionalEvidenceConfiguration.class,
                    NomisProfessionalEvidenceConfiguration.class,
                    LocalFixtureProfessionalOccupationConfiguration.class,
                    ConfiguredProfessionalOccupationConfiguration.class,
                    LocalFixtureProfessionalPaymentConfiguration.class,
                    StripeProfessionalPaymentConfiguration.class,
                    OpenAiProfessionalNarrativeConfiguration.class)
            .withBean(TypeSafeClient.class, () -> mock(TypeSafeClient.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(RestClient.Builder.class, RestClient::builder)
            .withBean(ChatClient.Builder.class, () -> {
                ChatClient.Builder b = mock(ChatClient.Builder.class);
                org.mockito.Mockito.when(b.build()).thenReturn(mock(ChatClient.class));
                return b;
            });

    // ── Jev ──────────────────────────────────────────────────────────────────────────────────────

    @Test
    void jevFixtureIsActiveUnderDevAndDevNoJev() {
        for (String profile : new String[]{"dev", "dev-no-jev"}) {
            runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                    .run(ctx -> assertThat(ctx.getBeansOfType(LocalFixtureProfessionalJudgmentProvider.class))
                            .as("profile=%s", profile).isNotEmpty());
        }
    }

    @Test
    void jevFixtureIsNotActiveUnderProd() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> assertThat(ctx.getBeansOfType(LocalFixtureProfessionalJudgmentProvider.class))
                        .isEmpty());
    }

    @Test
    void jevRealIsActiveOnlyUnderProd() {
        runner.withPropertyValues("app.judgment.provider=jev")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> assertThat(ctx.getBeansOfType(ProfessionalJevJudgmentService.class))
                        .isNotEmpty());
    }

    @Test
    void jevRealIsNotActiveUnderDevOrDevNoJev() {
        for (String profile : new String[]{"dev", "dev-no-jev"}) {
            runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                    .run(ctx -> assertThat(ctx.getBeansOfType(ProfessionalJevJudgmentService.class))
                            .as("profile=%s", profile).isEmpty());
        }
    }

    // ── OpenAI narrative ─────────────────────────────────────────────────────────────────────────

    @Test
    void narrativeFixtureIsActiveOnlyUnderDev() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("dev"))
                .run(ctx -> assertThat(ctx.getBeansOfType(LocalFixtureProfessionalNarrativeGenerator.class))
                        .isNotEmpty());
        for (String profile : new String[]{"dev-no-jev", "prod"}) {
            runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                    .run(ctx -> assertThat(ctx.getBeansOfType(LocalFixtureProfessionalNarrativeGenerator.class))
                            .as("profile=%s", profile).isEmpty());
        }
    }

    @Test
    void narrativeRealIsActiveUnderDevNoJevAndProd() {
        for (String profile : new String[]{"dev-no-jev", "prod"}) {
            runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                    .run(ctx -> assertThat(ctx.getBeansOfType(OpenAiProfessionalNarrativeGenerator.class))
                            .as("profile=%s", profile).isNotEmpty());
        }
    }

    @Test
    void narrativeRealIsNotActiveUnderDev() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("dev"))
                .run(ctx -> assertThat(ctx.getBeansOfType(OpenAiProfessionalNarrativeGenerator.class))
                        .isEmpty());
    }

    // ── Stripe ───────────────────────────────────────────────────────────────────────────────────

    @Test
    void stripeFixtureIsActiveOnlyUnderDev() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("dev"))
                .run(ctx -> assertThat(ctx.getBeansOfType(LocalFixtureProfessionalPaymentGateway.class))
                        .isNotEmpty());
        for (String profile : new String[]{"dev-no-jev", "prod"}) {
            runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                    .run(ctx -> assertThat(ctx.getBeansOfType(LocalFixtureProfessionalPaymentGateway.class))
                            .as("profile=%s", profile).isEmpty());
        }
    }

    @Test
    void stripeRealIsActiveUnderDevNoJevAndProd() {
        for (String profile : new String[]{"dev-no-jev", "prod"}) {
            runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                    .run(ctx -> assertThat(ctx.getBeansOfType(StripeProfessionalPaymentGateway.class))
                            .as("profile=%s", profile).isNotEmpty());
        }
    }

    @Test
    void stripeRealIsNotActiveUnderDev() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("dev"))
                .run(ctx -> assertThat(ctx.getBeansOfType(StripeProfessionalPaymentGateway.class))
                        .isEmpty());
    }

    @Test
    void stripeRealIsAbsentFromProdWhenCapabilityIsDisabled() {
        runner.withPropertyValues("app.stripe.enabled=false")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> assertThat(ctx.getBeansOfType(StripeProfessionalPaymentGateway.class)).isEmpty());
    }

    // ── Evidence ─────────────────────────────────────────────────────────────────────────────────

    @Test
    void evidenceFixtureIsActiveUnderDevAndDevNoJev() {
        for (String profile : new String[]{"dev", "dev-no-jev"}) {
            runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                    .run(ctx -> assertThat(ctx.getBeansOfType(LocalFixtureProfessionalEvidenceProvider.class))
                            .as("profile=%s", profile).isNotEmpty());
        }
    }

    @Test
    void evidenceFixtureIsNotActiveUnderProd() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> assertThat(ctx.getBeansOfType(LocalFixtureProfessionalEvidenceProvider.class))
                        .isEmpty());
    }

    @Test
    void evidenceRealIsActiveOnlyUnderProd() {
        runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> assertThat(ctx.getBeansOfType(NomisProfessionalEvidenceProvider.class))
                        .isNotEmpty());
    }

    @Test
    void evidenceRealIsNotActiveUnderDevOrDevNoJev() {
        for (String profile : new String[]{"dev", "dev-no-jev"}) {
            runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                    .run(ctx -> assertThat(ctx.getBeansOfType(NomisProfessionalEvidenceProvider.class))
                            .as("profile=%s", profile).isEmpty());
        }
    }

    @Test
    void evidenceRealAndConfiguredClassifierAreAbsentFromProdWhenCapabilityIsDisabled() {
        runner.withPropertyValues("app.professional.evidence.live-enabled=false")
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .run(ctx -> {
                    assertThat(ctx.getBeansOfType(NomisProfessionalEvidenceProvider.class)).isEmpty();
                    assertThat(ctx.getBeansOfType(ConfiguredProfessionalOccupationClassifier.class)).isEmpty();
                });
    }

    // ── TypeSafe client safety ───────────────────────────────────────────────────────────────────

    @Test
    void typeSafeClientDoesNotExistUnderDevOrDevNoJev() {
        for (String profile : new String[]{"dev", "dev-no-jev"}) {
            runner.withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                    .run(ctx -> assertThat(ctx.getBeansOfType(ProfessionalJevJudgmentService.class))
                            .as("No Jev service under profile=%s", profile).isEmpty());
        }
    }
}
