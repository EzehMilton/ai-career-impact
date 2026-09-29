package com.chikere.futureai.assessment.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentDecisionTest {

    @Test
    void supportsJourneySpecificMeaningsWithoutAUniversalRiskScore() {
        AssessmentDecision professionalExposure = decision(
                JourneyType.PROFESSIONAL, 72, "High task exposure");
        AssessmentDecision universityReadiness = decision(
                JourneyType.UNIVERSITY, 72, "Strong future readiness");
        AssessmentDecision aLevelOptionality = decision(
                JourneyType.A_LEVEL, 72, "Broad future optionality");

        assertThat(professionalExposure.overallIndicator()).hasValue(72);
        assertThat(universityReadiness.resultLabel()).isEqualTo("Strong future readiness");
        assertThat(aLevelOptionality.resultLabel()).isEqualTo("Broad future optionality");
    }

    @Test
    void indicatorCanBeAbsentWhenMoreInformationIsRequired() {
        AssessmentDecision decision = new AssessmentDecision(
                JourneyType.UNIVERSITY,
                OptionalDouble.empty(),
                "More course detail needed",
                AssessmentConfidence.LOW,
                AssessmentStatus.MORE_INFORMATION_REQUIRED,
                List.of("Module information is missing"),
                List.of());

        assertThat(decision.overallIndicator()).isEmpty();
    }

    @Test
    void confidenceLevelsAreDomainCategoriesWithoutNumericThresholds() {
        assertThat(AssessmentConfidence.values()).containsExactly(
                AssessmentConfidence.HIGH,
                AssessmentConfidence.MODERATE,
                AssessmentConfidence.LOW);
    }

    @Test
    void decisionFactorsAreDefensivelyCopiedAndUnmodifiable() {
        ArrayList<String> drivers = new ArrayList<>(List.of("Routine analysis"));
        AssessmentDecision decision = new AssessmentDecision(
                JourneyType.PROFESSIONAL,
                OptionalDouble.of(55),
                "Mixed exposure",
                AssessmentConfidence.MODERATE,
                AssessmentStatus.READY,
                drivers,
                List.of("Stakeholder trust"));

        drivers.add("Documentation");

        assertThat(decision.keyDrivers()).containsExactly("Routine analysis");
        assertThatThrownBy(() -> decision.keyDrivers().add("Another driver"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private AssessmentDecision decision(JourneyType journey, double indicator, String label) {
        return new AssessmentDecision(
                journey,
                OptionalDouble.of(indicator),
                label,
                AssessmentConfidence.HIGH,
                AssessmentStatus.READY,
                List.of("A journey-specific driver"),
                List.of("A journey-specific strength"));
    }
}
