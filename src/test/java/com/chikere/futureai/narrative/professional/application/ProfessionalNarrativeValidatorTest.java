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
    void rejectsPercentageClaimsThatAreNotFollowedByAWordCharacter() {
        var narrative = new ProfessionalNarrative("Headline", "Around 92% of similar roles change",
                "Meaning", "Exposure", "Human", "Complementarity", "Adaptability",
                List.of("Action one", "Action two", "Action three"), "Limitations");
        var trailing = new ProfessionalNarrative("Headline", "Summary", "Meaning", "Exposure", "Human",
                "Complementarity", "Adaptability",
                List.of("Action one", "Action two", "Exposure sits at 70%"), "Limitations");

        assertThatThrownBy(() -> new ProfessionalNarrativeValidator().validate(narrative))
                .hasMessageContaining("numeric scores");
        assertThatThrownBy(() -> new ProfessionalNarrativeValidator().validate(trailing))
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
