package com.chikere.futureai.occupation.professional.configured;

import com.chikere.futureai.occupation.professional.application.ProfessionalOccupationClassificationRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfiguredProfessionalOccupationClassifierTest {
    @Test
    void matchesOnlyTheExactConfiguredProfession() {
        var classifier = new ConfiguredProfessionalOccupationClassifier("Software Architect",
                "IT business analysts, architects and systems designers", "2133");
        assertThat(classifier.classify(request("Software Architect"))).isPresent();
        assertThat(classifier.classify(request("software architect"))).isEmpty();
        assertThat(classifier.classify(request("Senior Software Architect"))).isEmpty();
    }

    private ProfessionalOccupationClassificationRequest request(String profession) {
        return new ProfessionalOccupationClassificationRequest(profession, List.of("Design"),
                List.of("Architecture"), "United Kingdom");
    }
}
