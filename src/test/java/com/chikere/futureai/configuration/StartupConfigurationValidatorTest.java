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

    @Test
    void localFixtureNeedsNoProductionConfigurationAtAll() {
        // The whole point of the fixture profile is that a developer can run the product with
        // nothing but a database. If this test ever needs a secret added to it, something has gone
        // wrong in the main code.
        assertThatCode(() -> StartupConfigurationValidator.validate(environment("local-fixture")))
                .doesNotThrowAnyException();
    }

    @Test
    void anOrdinaryDevelopmentContextWithNoProfileHasNoProductionValidation() {
        assertThatCode(() -> StartupConfigurationValidator.validate(environment()))
                .doesNotThrowAnyException();
    }

    @Test
    void productionAndFixturesTogetherIsRefused() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(
                environment("prod", "local-fixture")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("'prod' and 'local-fixture' are contradictory");
    }

    @Test
    void askingForLivePaymentWhileFixturesOverrideItIsRefused() {
        // Silently simulating a payment for someone who asked for a real one is the worst available
        // outcome, so this is a startup failure rather than a warning.
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(
                environment("local-fixture", "live-payment")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("no real payment would be taken");
    }

    @Test
    void askingForLiveEvidenceWhileFixturesOverrideItIsRefused() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(
                environment("local-fixture", "live-evidence")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("no real evidence would be used");
    }

    @Test
    void productionWithoutAPublicBaseUrlIsRefused() {
        MockEnvironment environment = environment("prod");

        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("Missing required configuration: app.public-base-url");
    }

    @Test
    void productionRefusesAnInsecurePublicBaseUrlAndSaysWhy() {
        MockEnvironment environment = environment("prod")
                .withProperty("app.public-base-url", "http://example.com");

        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("must use https in production")
                .hasMessageContaining("app.public-base-url-allow-http");
    }

    @Test
    void productionAcceptsPlainHttpOnlyWhenTheOperatorSaysSoDeliberately() {
        // TLS terminating at a load balancer is a real deployment, so there is an escape hatch —
        // but it has to be asked for, rather than being what happens when nobody thought about it.
        MockEnvironment environment = productionEnvironment()
                .withProperty("app.public-base-url", "http://internal.example")
                .withProperty("app.public-base-url-allow-http", "true");

        assertThatCode(() -> StartupConfigurationValidator.validate(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void productionWithAnHttpsBaseUrlStarts() {
        assertThatCode(() -> StartupConfigurationValidator.validate(productionEnvironment()
                .withProperty("app.public-base-url", "https://example.com")))
                .doesNotThrowAnyException();
    }

    @Test
    void productionNamesBothMissingAiKeysWithoutRevealingValues() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment("prod")
                .withProperty("app.public-base-url", "https://example.com")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("TYPESAFE_API_KEY")
                .hasMessageContaining("OPENAI_API_KEY");
    }

    @Test
    void livePaymentNamesEveryMissingStripeSettingAtOnce() {
        // One restart should tell an operator everything that is wrong, not just the first thing.
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment("live-payment")))
                .isInstanceOf(ConfigurationProblemException.class)
                .satisfies(failure -> {
                    for (String setting : STRIPE) {
                        assertThat(failure).hasMessageContaining("Missing required configuration: " + setting);
                    }
                });
    }

    @Test
    void livePaymentWithEveryStripeSettingStarts() {
        MockEnvironment environment = environment("live-payment");
        for (String setting : STRIPE) {
            environment.withProperty(setting, "configured");
        }

        assertThatCode(() -> StartupConfigurationValidator.validate(environment))
                .doesNotThrowAnyException();
    }

    @Test
    void liveEvidenceRequiresItsOccupationMappingButNothingFromStripe() {
        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment("live-evidence")))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageContaining("app.professional.evidence.classified-profession")
                .hasMessageContaining("app.professional.evidence.soc2020-code")
                .hasMessageNotContaining("stripe");
    }

    @Test
    void noFailureMessageEverRepeatsAConfiguredValue() {
        MockEnvironment environment = environment("prod", "live-payment")
                .withProperty("app.public-base-url", "http://not-secret-but-still-not-echoed.example")
                .withProperty("app.stripe.secret-key", "sk_live_pretend_secret_value");

        assertThatThrownBy(() -> StartupConfigurationValidator.validate(environment))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessageNotContaining("sk_live_pretend_secret_value")
                .hasMessageNotContaining("not-secret-but-still-not-echoed");
    }

    private MockEnvironment environment(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return environment;
    }

    private MockEnvironment productionEnvironment() {
        return environment("prod")
                .withProperty("TYPESAFE_API_KEY", "test-only-jev-key")
                .withProperty("OPENAI_API_KEY", "test-only-openai-key");
    }
}
