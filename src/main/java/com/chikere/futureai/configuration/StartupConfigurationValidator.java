package com.chikere.futureai.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.chikere.futureai.configuration.ApplicationProfiles.JEV_CAPTURE;
import static com.chikere.futureai.configuration.ApplicationProfiles.LIVE_EVIDENCE;
import static com.chikere.futureai.configuration.ApplicationProfiles.LIVE_CV_EXTRACTION;
import static com.chikere.futureai.configuration.ApplicationProfiles.LIVE_PAYMENT;
import static com.chikere.futureai.configuration.ApplicationProfiles.LOCAL_FIXTURE;
import static com.chikere.futureai.configuration.ApplicationProfiles.PROD;

/**
 * Refuses to start an application that is configured in a way it cannot honour.
 *
 * <p>The alternative is discovering the problem when the first customer tries to pay, which is both
 * later and more expensive. Two kinds of problem are caught:</p>
 *
 * <ul>
 *   <li><strong>Contradictory profiles.</strong> Asking for production and fixtures at once, or for
 *       live payment alongside the simulator that overrides it, is a request the application cannot
 *       satisfy as asked. Rather than silently picking one, it stops.</li>
 *   <li><strong>Missing configuration for an enabled capability.</strong> Checks are scoped to what
 *       is actually switched on: fixtures need no Stripe keys, and an application without live
 *       payment needs no price ids.</li>
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

    private static final List<String> LIVE_EVIDENCE_SETTINGS = List.of(
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
        if (active.contains(PROD) && active.contains(LOCAL_FIXTURE)) {
            conflicts.add("Profiles 'prod' and 'local-fixture' are contradictory: production must "
                    + "never serve fixture results.");
        }
        if (active.contains(LOCAL_FIXTURE) && active.contains(LIVE_PAYMENT)) {
            conflicts.add("Profiles 'local-fixture' and 'live-payment' are contradictory: the "
                    + "fixture simulator overrides Stripe, so no real payment would be taken.");
        }
        if (active.contains(LOCAL_FIXTURE) && active.contains(LIVE_EVIDENCE)) {
            conflicts.add("Profiles 'local-fixture' and 'live-evidence' are contradictory: the "
                    + "fixture provider overrides the live one, so no real evidence would be used.");
        }
        if (active.contains(PROD) && active.contains(JEV_CAPTURE)) {
            conflicts.add("Profiles 'prod' and 'jev-capture' are contradictory: capture writes "
                    + "assessment input to local files and is for development only.");
        }
        if (active.contains(LIVE_CV_EXTRACTION) && !active.contains(LOCAL_FIXTURE)) {
            conflicts.add("Profile 'live-cv-extraction' is a local development overlay and must "
                    + "be combined with 'local-fixture'.");
        }
        return conflicts;
    }

    private static List<String> missingSettings(Set<String> active, Environment environment) {
        List<String> problems = new ArrayList<>();
        if (active.contains(PROD)) {
            problems.addAll(productionSettings(environment));
        }
        if (active.contains(LIVE_PAYMENT) && !active.contains(LOCAL_FIXTURE)) {
            problems.addAll(missing(environment, STRIPE_SETTINGS));
        }
        if (active.contains(LIVE_EVIDENCE) && !active.contains(LOCAL_FIXTURE)) {
            problems.addAll(missing(environment, LIVE_EVIDENCE_SETTINGS));
        }
        if (active.contains(LIVE_CV_EXTRACTION)
                && environment.getProperty("OPENAI_API_KEY", "").isBlank()) {
            problems.add("Missing required configuration: OPENAI_API_KEY "
                    + "(required by profile 'live-cv-extraction')");
        }
        return problems;
    }

    private static List<String> productionSettings(Environment environment) {
        // The datasource is deliberately not checked here. Spring Boot already refuses to start
        // without one, with a clearer message than this could give, and a DataSource can legitimately
        // be supplied other than through spring.datasource.url.
        List<String> problems = new ArrayList<>();
        if (environment.getProperty("TYPESAFE_API_KEY", "").isBlank()) {
            problems.add("Missing required configuration: TYPESAFE_API_KEY");
        }
        if (environment.getProperty("OPENAI_API_KEY", "").isBlank()) {
            problems.add("Missing required configuration: OPENAI_API_KEY");
        }
        String configured = environment.getProperty("app.public-base-url", "");
        if (configured.isBlank()) {
            problems.add("Missing required configuration: app.public-base-url");
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
        }
        catch (ConfigurationProblemException invalid) {
            problems.add(invalid.getMessage());
        }
        return problems;
    }

    private static List<String> missing(Environment environment, List<String> settings) {
        return settings.stream()
                .filter(setting -> environment.getProperty(setting, "").isBlank())
                .map(setting -> "Missing required configuration: " + setting)
                .toList();
    }
}
