package com.chikere.futureai.narrative.professional.application;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalNarrativeValidatorTest {

    @Test
    void rejectsCategoricalAiCapabilityAndReplacementClaims() {
        for (String claim : List.of("AI cannot replicate this work at present.",
                "AI will replace this role.", "This role is safe from AI.",
                "This role will disappear.")) {
            assertThatThrownBy(() -> new ProfessionalNarrativeValidator().validate(withSummary(claim)))
                    .hasMessageContaining("categorical AI");
        }
    }

    @Test
    void acceptsCautiousCapabilityExplanationAndUnrelatedCannot() {
        var cautious = withSummary("The assessment suggests AI may assist with some tasks; "
                + "it cannot determine your individual job prospects.");
        assertThat(new ProfessionalNarrativeValidator().validate(cautious)).isSameAs(cautious);
    }

    private ProfessionalNarrative withSummary(String summary) {
        return new ProfessionalNarrative("Headline", summary, "Meaning", "Exposure", "Human",
                "Complementarity", "Adaptability", List.of("Action one", "Action two", "Action three"),
                "Limitations");
    }

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
