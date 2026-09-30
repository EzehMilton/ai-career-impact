package com.chikere.futureai.occupation.professional.fixture;

import com.chikere.futureai.occupation.professional.application.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LocalFixtureProfessionalOccupationClassifierTest {
    private final ProfessionalOccupationClassificationRequest request =
            new ProfessionalOccupationClassificationRequest("Arbitrary submitted title",
                    List.of("Design systems"), List.of("Architecture"), "United Kingdom");

    @Test
    void exactKnownFixtureIsDeterministicAndVersioned() {
        var classifier = new LocalFixtureProfessionalOccupationClassifier("senior_software_architect");
        var first = classifier.classify(request).orElseThrow();
        assertThat(classifier.classify(request)).contains(first);
        assertThat(first.canonicalOccupation()).isEqualTo("IT business analysts, architects and systems designers");
        assertThat(first.classificationSystem()).isEqualTo(OccupationClassificationSystem.SOC_2020);
        assertThat(first.classificationCode()).isEqualTo("2133");
        assertThat(first.source()).isEqualTo(OccupationClassificationSource.LOCAL_FIXTURE);
        assertThat(first.classifierVersion()).isEqualTo("professional-occupation-classifier-v1");
    }

    @Test
    void approximateOrUnknownFixtureNeverFuzzyMatches() {
        assertThatThrownBy(() -> new LocalFixtureProfessionalOccupationClassifier("software_architect"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
        assertThatThrownBy(() -> new LocalFixtureProfessionalOccupationClassifier("Registered Nurse"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unsupported");
    }
}
