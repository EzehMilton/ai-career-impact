package com.chikere.futureai.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The address the application hands out to Stripe and to visitors.
 *
 * <p>A wrong value here is not a cosmetic problem: a payment return link built from it is where a
 * paying customer lands, so it is validated once, loudly, at startup rather than discovered when
 * someone's browser goes nowhere.</p>
 */
class PublicBaseUrlTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.com",
            "https://example.com/",
            "http://localhost:8080",
            "https://sub.example.co.uk:8443/app"})
    void realisticAddressesAreAccepted(String text) {
        assertThat(PublicBaseUrl.of(text).value().toString()).endsWith("/");
    }

    @Test
    void aTrailingSlashIsAddedSoResolvingIsPredictable() {
        // Without this, "https://example.com" + "a/b" quietly loses the last path segment.
        assertThat(PublicBaseUrl.of("https://example.com/app").resolve("/professional-assessment"))
                .hasToString("https://example.com/app/professional-assessment");
        assertThat(PublicBaseUrl.of("https://example.com").resolve("professional-assessment"))
                .hasToString("https://example.com/professional-assessment");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "example.com",
            "/relative/path",
            "ftp://example.com",
            "file:///etc/passwd",
            "javascript:alert(1)",
            "https://",
            "https://example.com?tracking=1",
            "https://example.com#section"})
    void anythingThatIsNotAPlainAbsoluteHttpAddressIsRefused(String text) {
        assertThatThrownBy(() -> PublicBaseUrl.of(text))
                .as("%s", text)
                .isInstanceOf(ConfigurationProblemException.class);
    }

    @Test
    void anAbsentValueIsReportedAsMissingConfigurationByName() {
        assertThatThrownBy(() -> PublicBaseUrl.of("  "))
                .isInstanceOf(ConfigurationProblemException.class)
                .hasMessage("Missing required configuration: app.public-base-url");
        assertThatThrownBy(() -> PublicBaseUrl.of(null))
                .isInstanceOf(ConfigurationProblemException.class);
    }

    @Test
    void whetherTheAddressIsSecureIsAnswerableBecauseProductionDependsOnIt() {
        assertThat(PublicBaseUrl.of("https://example.com").isSecure()).isTrue();
        assertThat(PublicBaseUrl.of("HTTPS://example.com").isSecure()).isTrue();
        assertThat(PublicBaseUrl.of("http://example.com").isSecure()).isFalse();
    }
}
