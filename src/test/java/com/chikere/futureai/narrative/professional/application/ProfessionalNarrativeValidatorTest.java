package com.chikere.futureai.narrative.professional.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfessionalNarrativeValidatorTest {

    @Test
    void rejectsNumericScoreOrPercentageClaims() {
        var narrative = new ProfessionalNarrative("Headline", "Exposure is 9.5 / 10", "Meaning", "Exposure",
                "Human", "Complementarity", "Adaptability",
                List.of("Action one", "Action two", "Action three"), "Limitations");

        assertThatThrownBy(() -> new ProfessionalNarrativeValidator().validate(narrative))
                .hasMessageContaining("numeric scores");
    }

    @Test
    void modelRejectsMissingSectionsAndWrongActionBounds() {
        assertThatThrownBy(() -> new ProfessionalNarrative("", "Summary", "Meaning", "Exposure", "Human",
                "Complementarity", "Adaptability", List.of("One", "Two", "Three"), "Limitations"))
                .hasMessageContaining("headline");
        assertThatThrownBy(() -> new ProfessionalNarrative("Headline", "Summary", "Meaning", "Exposure", "Human",
                "Complementarity", "Adaptability", List.of("One", "Two"), "Limitations"))
                .hasMessageContaining("between 3 and 5");
    }
}
