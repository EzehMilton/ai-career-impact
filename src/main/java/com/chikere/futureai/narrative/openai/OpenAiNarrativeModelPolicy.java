package com.chikere.futureai.narrative.openai;

import com.chikere.futureai.configuration.ConfigurationProblemException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The OpenAI model each journey's narratives use, free and paid alike, and the models each journey allows.
 *
 * <p>One explicit allowlist per journey, never a comparison of version numbers parsed from model names. The first
 * entry is the journey's default and minimum; a deliberately configured model further down the list is an upgrade.
 * Anything else, including an older or unknown model, is refused at startup and when a generator is created. There
 * is no fallback or downgrade: a configured model that fails at runtime fails that narrative, which then takes the
 * journey's existing deterministic fallback.</p>
 *
 * <p>Journey narratives never use Spring AI's global {@code spring.ai.openai.chat.model}: every request sends its
 * journey's model explicitly. Model names are not secrets, so a refusal names the configured model.</p>
 */
public final class OpenAiNarrativeModelPolicy {

    public static final String UNIVERSITY_PROPERTY = "app.narrative.university.model";
    public static final String A_LEVEL_PROPERTY = "app.narrative.a-level.model";
    public static final String PROFESSIONAL_PROPERTY = "app.narrative.professional.model";

    public static final String UNIVERSITY_DEFAULT = "gpt-6-luna";
    public static final String A_LEVEL_DEFAULT = "gpt-6-luna";
    public static final String PROFESSIONAL_DEFAULT = "gpt-6.1-sol";

    /** One journey's narrative model setting: its property, default (and minimum) and allowed models. */
    public enum Journey {
        UNIVERSITY("University", UNIVERSITY_PROPERTY,
                List.of(UNIVERSITY_DEFAULT, "gpt-6.1-terra", "gpt-6.1-sol", "gpt-6-astra")),
        A_LEVEL("A-Level", A_LEVEL_PROPERTY,
                List.of(A_LEVEL_DEFAULT, "gpt-6.1-terra", "gpt-6.1-sol", "gpt-6-astra")),
        PROFESSIONAL("Professional", PROFESSIONAL_PROPERTY, List.of(PROFESSIONAL_DEFAULT, "gpt-6-astra"));

        private final String label;
        private final String property;
        private final List<String> allowed;

        Journey(String label, String property, List<String> allowed) {
            this.label = label;
            this.property = property;
            this.allowed = List.copyOf(allowed);
        }

        public String property() {
            return property;
        }

        public String defaultModel() {
            return allowed.getFirst();
        }

        public List<String> allowedModels() {
            return allowed;
        }
    }

    /** Every model some journey allows: a known narrative model, so refusing one for a journey means "below tier". */
    private static final Set<String> KNOWN = knownModels();

    private OpenAiNarrativeModelPolicy() {
    }

    /** Why the configured model is refused for the journey, or empty when it is allowed. */
    public static Optional<String> problem(Journey journey, String configured) {
        String model = configured == null ? "" : configured.strip();
        if (model.isEmpty()) {
            return Optional.of(journey.label + " narrative model (" + journey.property + ") is set but empty. "
                    + "Leave it unset for the default " + journey.defaultModel() + ", or set one of "
                    + journey.allowed + ".");
        }
        if (journey.allowed.contains(model)) {
            return Optional.empty();
        }
        String reason = KNOWN.contains(model) || legacy(model)
                ? " is below the application's allowed " + journey.label + " model tier."
                : " is not an allowed " + journey.label + " narrative model.";
        return Optional.of(journey.label + " narrative model '" + model + "'" + reason + " Allowed ("
                + journey.property + "): " + journey.allowed + ".");
    }

    /** The configured model, stripped, if the journey allows it; otherwise the application must not start. */
    public static String require(Journey journey, String configured) {
        problem(journey, configured).ifPresent(problem -> {
            throw new ConfigurationProblemException(problem);
        });
        return configured.strip();
    }

    /** Earlier OpenAI families: named only to word the refusal, never to rank or select a model. */
    private static boolean legacy(String model) {
        return model.startsWith("gpt-3") || model.startsWith("gpt-4") || model.startsWith("gpt-5")
                || model.startsWith("o1") || model.startsWith("o3") || model.startsWith("o4");
    }

    private static Set<String> knownModels() {
        Set<String> known = new LinkedHashSet<>();
        for (Journey journey : Journey.values()) {
            known.addAll(journey.allowed);
        }
        return Set.copyOf(known);
    }
}
