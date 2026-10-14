package com.chikere.futureai.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.chikere.futureai.configuration.ApplicationProfiles.DEV;
import static com.chikere.futureai.configuration.ApplicationProfiles.DEV_NO_JEV;
import static com.chikere.futureai.configuration.ApplicationProfiles.PROD;

/**
 * Refuses to start an application that is configured in a way it cannot honour.
 *
 * <p>Two kinds of problem are caught:</p>
 *
 * <ul>
 *   <li><strong>Contradictory profiles.</strong> The application supports exactly one of
 *       {@code dev}, {@code dev-no-jev}, or {@code prod}. Combining any two is refused at
 *       startup rather than silently picking a winner.</li>
 *   <li><strong>Missing configuration for an enabled capability.</strong> Checks are scoped to what
 *       is actually switched on: {@code dev} needs no external-service credentials; {@code prod}
 *       needs all of them and is refused if any are absent.</li>
 * </ul>
 *
 * <p>No message ever contains a configuration value. Startup failures are pasted into tickets.</p>
 */
@Component
public class StartupConfigurationValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(StartupConfigurationValidator.class);

    private static final List<String> STRIPE_SETTINGS = List.of(
            "app.stripe.secret-key",
            "app.stripe.webhook-secret",
            "app.stripe.university-webhook-secret",
            "app.stripe.a-level-webhook-secret",
            "app.professional.report.stripe-price-id",
            "app.university.report.stripe-price-id",
            "app.a-level.report.stripe-price-id");

    private static final List<String> EVIDENCE_SETTINGS = List.of(
            "app.professional.evidence.classified-profession",
            "app.professional.evidence.canonical-occupation",
            "app.professional.evidence.soc2020-code");

    public StartupConfigurationValidator(Environment environment) {
        validate(environment);
    }

    /** Package-visible so the rules can be exercised without starting an application. */
    static void validate(Environment environment) {
        Set<String> active = Set.of(environment.getActiveProfiles());
        List<String> problems = new ArrayList<>(conflictingProfiles(active));
        problems.addAll(missingSettings(active, environment));
        if (problems.isEmpty()) {
            return;
        }
        String detail = String.join("\n  - ", problems);
        LOGGER.error("Refusing to start: the active configuration cannot be honoured.\n  - {}", detail);
        throw new ConfigurationProblemException(
                "The active configuration cannot be honoured:\n  - " + detail);
    }

    private static List<String> conflictingProfiles(Set<String> active) {
        List<String> conflicts = new ArrayList<>();
        if (active.contains(DEV) && active.contains(PROD)) {
            conflicts.add("Profiles 'dev' and 'prod' are contradictory: production must never "
                    + "serve fixture results.");
        }
        if (active.contains(DEV) && active.contains(DEV_NO_JEV)) {
            conflicts.add("Profiles 'dev' and 'dev-no-jev' are contradictory: choose exactly one "
                    + "development profile.");
        }
        if (active.contains(DEV_NO_JEV) && active.contains(PROD)) {
            conflicts.add("Profiles 'dev-no-jev' and 'prod' are contradictory: choose exactly one "
                    + "profile.");
        }
        return conflicts;
    }

    private static List<String> missingSettings(Set<String> active, Environment environment) {
        List<String> problems = new ArrayList<>();
        if (active.contains(PROD)) {
            problems.addAll(prodSettings(environment));
        }
        if (active.contains(DEV_NO_JEV)) {
            problems.addAll(devNoJevSettings(environment));
        }
        return problems;
    }

    private static List<String> prodSettings(Environment environment) {
        List<String> problems = new ArrayList<>();
        if (environment.getProperty("TYPESAFE_API_KEY", "").isBlank()) {
            problems.add("Missing required configuration: TYPESAFE_API_KEY "
                    + "(required by profile 'prod': real Jev / TypeSafe judgments)");
        }
        if (environment.getProperty("OPENAI_API_KEY", "").isBlank()) {
            problems.add("Missing required configuration: OPENAI_API_KEY "
                    + "(required by profile 'prod': real OpenAI narrative and CV extraction)");
        }
        problems.addAll(missing(environment, STRIPE_SETTINGS, PROD));
        problems.addAll(missing(environment, EVIDENCE_SETTINGS, PROD));
        String configured = environment.getProperty("app.public-base-url", "");
        if (configured.isBlank()) {
            problems.add("Missing required configuration: app.public-base-url "
                    + "(required by profile 'prod': payment return links are built from it)");
            return problems;
        }
        try {
            PublicBaseUrl baseUrl = PublicBaseUrl.of(configured);
            boolean allowInsecure = environment.getProperty(
                    "app.public-base-url-allow-http", Boolean.class, false);
            if (!baseUrl.isSecure() && !allowInsecure) {
                problems.add("app.public-base-url must use https in production. Payment return "
                        + "links are built from it. Set app.public-base-url-allow-http=true only "
                        + "if TLS genuinely terminates elsewhere and that is a deliberate choice.");
            }
        } catch (ConfigurationProblemException invalid) {
            problems.add(invalid.getMessage());
        }
        return problems;
    }

    private static List<String> devNoJevSettings(Environment environment) {
        List<String> problems = new ArrayList<>();
        if (environment.getProperty("OPENAI_API_KEY", "").isBlank()) {
            problems.add("Missing required configuration: OPENAI_API_KEY "
                    + "(required by profile 'dev-no-jev': real OpenAI narrative and CV extraction)");
        }
        problems.addAll(missing(environment, STRIPE_SETTINGS, DEV_NO_JEV));
        return problems;
    }

    private static List<String> missing(Environment environment, List<String> settings,
            String profile) {
        return settings.stream()
                .filter(setting -> environment.getProperty(setting, "").isBlank())
                .map(setting -> "Missing required configuration: " + setting
                        + " (required by profile '" + profile + "')")
                .toList();
    }
}