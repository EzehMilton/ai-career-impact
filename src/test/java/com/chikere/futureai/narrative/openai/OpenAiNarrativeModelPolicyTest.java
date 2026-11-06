package com.chikere.futureai.narrative.openai;

import com.chikere.futureai.configuration.ConfigurationProblemException;
import com.chikere.futureai.narrative.openai.OpenAiNarrativeModelPolicy.Journey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The journey narrative model allowlists: explicit names, never version arithmetic. */
class OpenAiNarrativeModelPolicyTest {

    @Test
    void eachJourneyDefaultsToItsMinimumModel() {
        assertThat(Journey.UNIVERSITY.defaultModel()).isEqualTo("gpt-6-luna");
        assertThat(Journey.A_LEVEL.defaultModel()).isEqualTo("gpt-6-luna");
        assertThat(Journey.PROFESSIONAL.defaultModel()).isEqualTo("gpt-6.1-sol");
        assertThat(Journey.UNIVERSITY.property()).isEqualTo("app.narrative.university.model");
        assertThat(Journey.A_LEVEL.property()).isEqualTo("app.narrative.a-level.model");
        assertThat(Journey.PROFESSIONAL.property()).isEqualTo("app.narrative.professional.model");
    }

    @ParameterizedTest
    @CsvSource({
            "UNIVERSITY, gpt-6-luna", "UNIVERSITY, gpt-6.1-terra", "UNIVERSITY, gpt-6.1-sol", "UNIVERSITY, gpt-6-astra",
            "A_LEVEL, gpt-6-luna", "A_LEVEL, gpt-6.1-terra", "A_LEVEL, gpt-6.1-sol", "A_LEVEL, gpt-6-astra",
            "PROFESSIONAL, gpt-6.1-sol", "PROFESSIONAL, gpt-6-astra"})
    void theMinimumAndDeliberateUpgradesAreAllowed(Journey journey, String model) {
        assertThat(OpenAiNarrativeModelPolicy.problem(journey, model)).isEmpty();
        assertThat(OpenAiNarrativeModelPolicy.require(journey, " " + model + " ")).isEqualTo(model);
    }

    @ParameterizedTest
    @CsvSource({
            "PROFESSIONAL, gpt-6-luna", "PROFESSIONAL, gpt-6.1-terra", "PROFESSIONAL, gpt-4o-mini",
            "PROFESSIONAL, gpt-4.1", "PROFESSIONAL, gpt-5-mini",
            "UNIVERSITY, gpt-4o-mini", "UNIVERSITY, gpt-5-mini", "A_LEVEL, gpt-4o-mini", "A_LEVEL, gpt-4.1-mini"})
    void modelsBelowTheJourneysTierAreRefused(Journey journey, String model) {
        assertThat(OpenAiNarrativeModelPolicy.problem(journey, model)).hasValueSatisfying(problem -> assertThat(problem)
                .contains("narrative model '" + model + "' is below the application's allowed")
                .contains(journey.property()));
        assertThatThrownBy(() -> OpenAiNarrativeModelPolicy.require(journey, model))
                .isInstanceOf(ConfigurationProblemException.class).hasMessageContaining(model);
    }

    @Test
    void theProfessionalRefusalReadsAsTheOperatorExpects() {
        assertThat(OpenAiNarrativeModelPolicy.problem(Journey.PROFESSIONAL, "gpt-6-luna")).hasValueSatisfying(problem ->
                assertThat(problem).startsWith("Professional narrative model 'gpt-6-luna' is below the application's "
                        + "allowed Professional model tier.").contains("[gpt-6.1-sol, gpt-6-astra]"));
    }

    @Test
    void unknownSnapshotAndEmptyModelsAreRefusedAndNeverReplacedByTheDefault() {
        for (Journey journey : Journey.values()) {
            assertThat(OpenAiNarrativeModelPolicy.problem(journey, "gpt-7-imaginary")).hasValueSatisfying(problem ->
                    assertThat(problem).contains("'gpt-7-imaginary' is not an allowed"));
            // A dated snapshot is not on the allowlist: the allowlist names exactly what may be configured.
            assertThat(OpenAiNarrativeModelPolicy.problem(journey, journey.defaultModel() + "-2026-10-01")).isPresent();
            assertThatThrownBy(() -> OpenAiNarrativeModelPolicy.require(journey, "")).hasMessageContaining("empty");
            assertThatThrownBy(() -> OpenAiNarrativeModelPolicy.require(journey, null)).hasMessageContaining("empty");
        }
    }
}
