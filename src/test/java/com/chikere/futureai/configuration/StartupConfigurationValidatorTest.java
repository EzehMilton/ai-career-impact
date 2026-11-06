package com.chikere.futureai.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rules that decide whether an application is allowed to start.
 *
 * <p>Two properties matter as much as the rules themselves: that local development stays usable
 * without any production secret, and that no failure message ever repeats a configured value.</p>
 */
class StartupConfigurationValidatorTest {

    private static final String[] STRIPE = {
            "app.stripe.secret-key", "app.stripe.webhook-secret",
            "app.stripe.university-webhook-secret", "app.stripe.a-level-webhook-secret",
            "app.professional.report.stripe-price-id", "app.university.report.stripe-price-id",
            "app.a-level.report.stripe-price-id"};

    private static final String[] EVIDENCE = {
            "app.professional.evidence.classified-profession",
            "app.professional.evidence.canonical-occupation",
            "app.professional.evidence.soc2020-code"};

    // ── dev ─────────────────────────────────────────────────────────────────────────────────────

    @Test
    void devNeedsNoProductionConfigurationAtAll() {
        assertThatCode(() -> StartupConfigurationValidator.validate(environment("dev")))
                .doesNotThrowAnyException();
    }

    @Test
    void devDoesNotRequireOpenAiKey() {
        assertThatCode(() -> StartupConfigurationValidator.validate(environment("dev")))
                .doesNotThrowAnyException();
    }

    // ── live-smoke ───────────────────────────────────────────────────────────────────────────────

    @Test
    void liveSmokeWithoutTheExplicitSwitchRefusesToStart() {
        MockEnvironment env = environment("live-smoke")
                .withProperty("TYPESAFE_API_KEY", "test-only-key")
                .withProperty("OPENAI_API_KEY", "test-only-key");

        assertThatThrownBy(() -> StartupConfigurationValidator.validate(env))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("live-smoke profile requires APP_LIVE_SMOKE_ENABLED=true")
                .hasMessageNotContaining("test-only-key");
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(
                env.withProperty("app.live-smoke.enabled", "false")))
                .hasMessageContaining("APP_LIVE_SMOKE_ENABLED=true");
    }

    @Test
    void liveSmokeWithTheSwitchAndBothProviderKeysStartsWithoutStripeOrEvidenceSettings() {
        MockEnvironment env = environment("live-smoke")
                .withProperty("app.live-smoke.enabled", "true")
                .withProperty("TYPESAFE_API_KEY", "test-only-key")
                .withProperty("OPENAI_API_KEY", "test-only-key");

        assertThatCode(() -> StartupConfigurationValidator.validate(env)).doesNotThrowAnyException();
    }

    @Test
    void liveSmokeRequiresBothRealProviderKeys() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment("live-smoke")
                .withProperty("app.live-smoke.enabled", "true")))
                .hasMessageContaining("TYPESAFE_API_KEY").hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void liveSmokeIsRefusedAlongsideProductionOrAnotherProfile() {
        for (String other : new String[] {"prod", "dev", "dev-no-jev"}) {
            MockEnvironment env = environment("live-smoke", other)
                    .withProperty("app.live-smoke.enabled", "true")
                    .withProperty("TYPESAFE_API_KEY", "test-only-key")
                    .withProperty("OPENAI_API_KEY", "test-only-key");
            assertThatThrownBy(() -> StartupConfigurationValidator.validate(env)).as(other)
                    .isInstanceOf(ConfigurationProblemException.class)
                    .hasMessageContaining("Profiles 'live-smoke' and '" + other + "' are contradictory");
        }
    }

    // ── dev-no-jev ───────────────────────────────────────────────────────────────────────────────

    @Test
    void devNoJevRequiresOpenAiKeyButNotTypeSafeKey() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment("dev-no-jev")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("OPENAI_API_KEY")
                .hasMessageNotContaining("TYPESAFE_API_KEY");
    }

    @Test
    void devNoJevDoesNotRequireEvidenceVariables() {
        MockEnvironment env = environment("dev-no-jev")
                .withProperty("OPENAI_API_KEY", "test-only-key");
        for (String s : STRIPE) env.withProperty(s, "configured");

        assertThatCode(() -> StartupConfigurationValidator.validate(env))
                .doesNotThrowAnyException();
    }

    @Test
    void devNoJevNamesAllMissingStripeSettingsAtOnce() {
        MockEnvironment env = environment("dev-no-jev")
                .withProperty("OPENAI_API_KEY", "test-only-key");

        assertThatThrownBy(() -> StartupConfigurationValidator.validate(env))
                .isInstanceOf(ConfigurationProblemException.class)
                .satisfies(failure -> {
                    for (String setting : STRIPE) {
                        assertThat(failure).hasMessageContaining("Missing required configuration: " + setting);
                    }
                });
    }

    @Test
    void devNoJevMessagesIdentifyTheProfile() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment("dev-no-jev")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("dev-no-jev");
    }

    // ── prod ────────────────────────────────────────────────────────────────────────────────────

    @Test
    void prodNamesAllMissingAiKeysWithoutRevealingValues() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment("prod")
                .withProperty("app.public-base-url", "https://example.com")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("TYPESAFE_API_KEY")
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void prodWithoutAPublicBaseUrlIsRefused() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment("prod")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("Missing required configuration: app.public-base-url");
    }

    @Test
    void prodRefusesAnInsecurePublicBaseUrlAndSaysWhy() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(
                environment("prod").withProperty("app.public-base-url", "http://example.com")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("must use https in production")
                .hasMessageContaining("app.public-base-url-allow-http");
    }

    @Test
    void prodAcceptsPlainHttpOnlyWhenTheOperatorSaysSoDeliberately() {
        assertThatCode(() -> StartupConfigurationValidator.validate(
                prodEnvironment()
                        .withProperty("app.public-base-url", "http://internal.example")
                        .withProperty("app.public-base-url-allow-http", "true")))
                .doesNotThrowAnyException();
    }

    @Test
    void prodWithAnHttpsBaseUrlAndAllSettingsStarts() {
        assertThatCode(() -> StartupConfigurationValidator.validate(
                prodEnvironment().withProperty("app.public-base-url", "https://example.com")))
                .doesNotThrowAnyException();
    }

    @Test
    void prodNamesAllMissingStripeSettingsAtOnce() {
        MockEnvironment env = environment("prod")
                .withProperty("TYPESAFE_API_KEY", "test-only-jev-key")
                .withProperty("OPENAI_API_KEY", "test-only-openai-key")
                .withProperty("app.public-base-url", "https://example.com");
        for (String s : EVIDENCE) env.withProperty(s, "configured");

        assertThatThrownBy(() -> StartupConfigurationValidator.validate(env))
                .isInstanceOf(ConfigurationProblemException.class)
                .satisfies(failure -> {
                    for (String setting : STRIPE) {
                        assertThat(failure).hasMessageContaining("Missing required configuration: " + setting);
                    }
                });
    }

    @Test
    void prodNamesAllMissingEvidenceSettingsAtOnce() {
        MockEnvironment env = environment("prod")
                .withProperty("TYPESAFE_API_KEY", "test-only-jev-key")
                .withProperty("OPENAI_API_KEY", "test-only-openai-key")
                .withProperty("app.public-base-url", "https://example.com");
        for (String s : STRIPE) env.withProperty(s, "configured");

        assertThatThrownBy(() -> StartupConfigurationValidator.validate(env))
                .isInstanceOf(ConfigurationProblemException.class)
                .satisfies(failure -> {
                    for (String setting : EVIDENCE) {
                        assertThat(failure).hasMessageContaining("Missing required configuration: " + setting);
                    }
                });
    }

    @Test
    void prodMessagesIdentifyTheProfile() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment("prod")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("prod");
    }

    // ── profile exclusivity ──────────────────────────────────────────────────────────────────────

    @Test
    void devAndProdTogetherIsRefused() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(
                environment("dev", "prod")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("'dev' and 'prod' are contradictory");
    }

    @Test
    void devAndDevNoJevTogetherIsRefused() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(
                environment("dev", "dev-no-jev")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("'dev' and 'dev-no-jev' are contradictory");
    }

    @Test
    void devNoJevAndProdTogetherIsRefused() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(
                environment("dev-no-jev", "prod")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("'dev-no-jev' and 'prod' are contradictory");
    }

    @Test
    void noProfileActiveIsPermittedForLocalDevelopmentWithoutSpringProfilesActive() {
        assertThatCode(() -> StartupConfigurationValidator.validate(environment()))
                .doesNotThrowAnyException();
    }

    // ── safety ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void noFailureMessageEverRepeatsAConfiguredValue() {
        MockEnvironment environment = environment("prod")
                .withProperty("app.public-base-url", "http://not-secret-but-still-not-echoed.example")
                .withProperty("app.stripe.secret-key", "sk_live_pretend_secret_value");

        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageNotContaining("sk_live_pretend_secret_value")
                .hasMessageNotContaining("not-secret-but-still-not-echoed");
    }

    // ── helpers ──────────────────────────────────────────────────────────────────────────────────

    // ── narrative models ─────────────────────────────────────────────────────────────────────────

    @Test
    void theDefaultJourneyModelsAreAcceptedEverywhereRealNarrativesRun() {
        assertThatCode(() -> StartupConfigurationValidator.validate(
                prodEnvironment().withProperty("app.public-base-url", "https://example.com")))
                .doesNotThrowAnyException();
        assertThatCode(() -> StartupConfigurationValidator.validate(devNoJevEnvironment())).doesNotThrowAnyException();
        assertThatCode(() -> StartupConfigurationValidator.validate(liveSmokeEnvironment())).doesNotThrowAnyException();
    }

    @Test
    void prodRefusesAProfessionalModelBelowItsTierAndNamesIt() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(prodEnvironment()
                .withProperty("app.public-base-url", "https://example.com")
                .withProperty("app.narrative.professional.model", "gpt-6-luna")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("Professional narrative model 'gpt-6-luna' is below the application's allowed "
                        + "Professional model tier.")
                .hasMessageNotContaining("test-only");
    }

    @Test
    void devNoJevValidatesTheNarrativeModelsButStillNeedsNoTypeSafeKey() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(devNoJevEnvironment()
                .withProperty("app.narrative.university.model", "gpt-4o-mini")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("University narrative model 'gpt-4o-mini' is below")
                .hasMessageNotContaining("TYPESAFE_API_KEY");
        assertThatCode(() -> StartupConfigurationValidator.validate(devNoJevEnvironment()
                .withProperty("app.narrative.university.model", "gpt-6.1-sol")))
                .doesNotThrowAnyException();
    }

    @Test
    void liveSmokeValidatesTheNarrativeModelsAndStillNeedsBothProviderKeys() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(liveSmokeEnvironment()
                .withProperty("app.narrative.a-level.model", "gpt-7-imaginary")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("A-Level narrative model 'gpt-7-imaginary' is not an allowed A-Level narrative "
                        + "model.");
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment("live-smoke")
                .withProperty("app.live-smoke.enabled", "true")))
                .hasMessageContaining("TYPESAFE_API_KEY").hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void devCallsNoModelSoItsNarrativeModelSettingsAreNotChecked() {
        assertThatCode(() -> StartupConfigurationValidator.validate(environment("dev")
                .withProperty("app.narrative.professional.model", "gpt-4o-mini")))
                .doesNotThrowAnyException();
    }

    @Test
    void anEmptyModelIsRefusedRatherThanReplacedByTheDefault() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(devNoJevEnvironment()
                .withProperty("app.narrative.professional.model", " ")))
                .hasMessageContaining("Professional narrative model (app.narrative.professional.model) is set but "
                        + "empty");
    }

    private MockEnvironment devNoJevEnvironment() {
        MockEnvironment env = environment("dev-no-jev").withProperty("OPENAI_API_KEY", "test-only-key");
        for (String s : STRIPE) env.withProperty(s, "configured");
        return env;
    }

    private MockEnvironment liveSmokeEnvironment() {
        return environment("live-smoke").withProperty("app.live-smoke.enabled", "true")
                .withProperty("TYPESAFE_API_KEY", "test-only-key").withProperty("OPENAI_API_KEY", "test-only-key");
    }

    private MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private MockEnvironment prodEnvironment() {
        MockEnvironment env = environment("prod")
                .withProperty("TYPESAFE_API_KEY", "test-only-jev-key")
                .withProperty("OPENAI_API_KEY", "test-only-openai-key");
        for (String s : STRIPE) env.withProperty(s, "configured");
        for (String s : EVIDENCE) env.withProperty(s, "configured");
        return env;
    }
}