package com.chikere.futureai.configuration;

import com.chikere.futureai.narrative.openai.OpenAiNarrativeModelPolicy;
import com.chikere.futureai.narrative.openai.OpenAiNarrativeModelPolicy.Journey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.chikere.futureai.configuration.ApplicationProfiles.DEV;
import static com.chikere.futureai.configuration.ApplicationProfiles.DEV_NO_JEV;
import static com.chikere.futureai.configuration.ApplicationProfiles.LIVE_SMOKE;
import static com.chikere.futureai.configuration.ApplicationProfiles.PROD;

/**
 * Refuses to start an application that is configured in a way it cannot honour.
 *
 * <p>Two kinds of problem are caught:</p>
 *
 * <ul>
 *   <li><strong>Contradictory profiles.</strong> The application supports exactly one of
 *       {@code dev}, {@code dev-no-jev}, {@code live-smoke} or {@code prod}. Combining any two is
 *       refused at startup rather than silently picking a winner.</li>
 *   <li><strong>Missing configuration for an enabled capability.</strong> Checks are scoped to what
 *       is actually switched on: {@code dev} needs no external-service credentials; {@code prod}
 *       requires credentials only for its enabled capabilities. {@code live-smoke} also needs the
 *       explicit {@code APP_LIVE_SMOKE_ENABLED=true} switch, so the profile alone never bypasses
 *       payment.</li>
 *   <li><strong>A narrative model the journey does not allow.</strong> Wherever real OpenAI narratives run
 *       ({@code prod}, {@code dev-no-jev}, {@code live-smoke}), each journey's configured model must be on that
 *       journey's allowlist ({@link OpenAiNarrativeModelPolicy}); it is never silently replaced by the default.
 *       {@code dev} calls no model and is not checked.</li>
 * </ul>
 *
 * <p>No message ever contains a credential or other secret value. Startup failures are pasted into tickets. The
 * one value quoted is a refused narrative model name, which is not a secret and is what the operator must fix.</p>
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
        if (active.contains(LIVE_SMOKE)) {
            // The prod profile is this application's one reliable signal of a real deployment.
            if (active.contains(PROD)) {
                conflicts.add("Profiles 'live-smoke' and 'prod' are contradictory: live smoke testing bypasses "
                        + "payment and must never run as a production deployment.");
            }
            for (String other : List.of(DEV, DEV_NO_JEV)) {
                if (active.contains(other)) {
                    conflicts.add("Profiles 'live-smoke' and '" + other + "' are contradictory: live smoke "
                            + "testing uses real providers, so choose exactly one profile.");
                }
            }
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
        if (active.contains(LIVE_SMOKE)) {
            problems.addAll(liveSmokeSettings(environment));
        }
        problems.addAll(narrativeModelSettings(active, environment));
        return problems;
    }

    /**
     * The journeys' narrative models, in every profile that runs real OpenAI narratives and while the OpenAI chat
     * model is enabled (its generators exist only then). Never in {@code dev}, which calls no model.
     */
    private static List<String> narrativeModelSettings(Set<String> active, Environment environment) {
        boolean realNarratives = active.contains(PROD) || active.contains(DEV_NO_JEV) || active.contains(LIVE_SMOKE);
        if (!realNarratives || active.contains(DEV)
                || !"openai".equals(environment.getProperty("spring.ai.model.chat", "openai"))) {
            return List.of();
        }
        List<String> problems = new ArrayList<>();
        for (Journey journey : Journey.values()) {
            String configured = environment.getProperty(journey.property(), journey.defaultModel());
            Optional<String> problem = OpenAiNarrativeModelPolicy.problem(journey, configured);
            problem.ifPresent(problems::add);
        }
        return problems;
    }

    private static List<String> prodSettings(Environment environment) {
        List<String> problems = new ArrayList<>();
        if (usesJev(environment) && environment.getProperty("TYPESAFE_API_KEY", "").isBlank()) {
            problems.add("Missing required configuration: TYPESAFE_API_KEY "
                    + "(required by profile 'prod' when app.judgment.provider=jev)");
        }
        if (environment.getProperty("OPENAI_API_KEY", "").isBlank()) {
            problems.add("Missing required configuration: OPENAI_API_KEY "
                    + "(required by profile 'prod': real OpenAI narrative)");
        }
        if (stripeEnabled(environment)) {
            problems.addAll(missing(environment, STRIPE_SETTINGS,
                    "profile 'prod' when app.stripe.enabled=true"));
        }
        if (liveProfessionalEvidenceEnabled(environment)) {
            problems.addAll(missing(environment, EVIDENCE_SETTINGS,
                    "profile 'prod' when app.professional.evidence.live-enabled=true"));
        }
        String configured = environment.getProperty("app.public-base-url", "");
        if (configured.isBlank()) {
            problems.add("Missing required configuration: app.public-base-url "
                    + "(required by profile 'prod': public application and report links use it)");
            return problems;
        }
        try {
            PublicBaseUrl baseUrl = PublicBaseUrl.of(configured);
            boolean allowInsecure = environment.getProperty(
                    "app.public-base-url-allow-http", Boolean.class, false);
            if (!baseUrl.isSecure() && !allowInsecure) {
                problems.add("app.public-base-url must use https in production. Public application and report "
                        + "links use it. Set app.public-base-url-allow-http=true only "
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
                    + "(required by profile 'dev-no-jev': real OpenAI narrative)");
        }
        if (stripeEnabled(environment)) {
            problems.addAll(missing(environment, STRIPE_SETTINGS,
                    "profile 'dev-no-jev' when app.stripe.enabled=true"));
        }
        return problems;
    }

    private static List<String> liveSmokeSettings(Environment environment) {
        List<String> problems = new ArrayList<>();
        if (!environment.getProperty("app.live-smoke.enabled", Boolean.class, false)) {
            problems.add("live-smoke profile requires APP_LIVE_SMOKE_ENABLED=true "
                    + "(real Jev and OpenAI calls are made and payments are bypassed)");
        }
        if (usesJev(environment) && environment.getProperty("TYPESAFE_API_KEY", "").isBlank()) {
            problems.add("Missing required configuration: TYPESAFE_API_KEY "
                    + "(required by profile 'live-smoke' when app.judgment.provider=jev)");
        }
        if (environment.getProperty("OPENAI_API_KEY", "").isBlank()) {
            problems.add("Missing required configuration: OPENAI_API_KEY "
                    + "(required by profile 'live-smoke': real OpenAI narrative)");
        }
        return problems;
    }

    private static boolean usesJev(Environment environment) {
        return "jev".equalsIgnoreCase(environment.getProperty("app.judgment.provider", "openai").strip());
    }

    private static boolean stripeEnabled(Environment environment) {
        return environment.getProperty("app.stripe.enabled", Boolean.class, false);
    }

    private static boolean liveProfessionalEvidenceEnabled(Environment environment) {
        return environment.getProperty("app.professional.evidence.live-enabled", Boolean.class, false);
    }

    private static List<String> missing(Environment environment, List<String> settings,
            String requirement) {
        return settings.stream()
                .filter(setting -> environment.getProperty(setting, "").isBlank())
                .map(setting -> "Missing required configuration: " + setting
                        + " (required by " + requirement + ")")
                .toList();
    }
}
