package com.chikere.futureai.report.university.application;

import com.chikere.futureai.decision.university.ProfileBand;
import com.chikere.futureai.decision.university.UniversityProfileMeasure;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UniversityProfilePresentationTest {

    @Test
    void displayedRoundingNeverCrossesIntoTheNextBand() {
        assertThat(UniversityProfilePresentation.score(3.4999, ProfileBand.LOW)).isEqualTo("3.49");
        assertThat(UniversityProfilePresentation.score(3.496, ProfileBand.LOW)).isEqualTo("3.49");
        assertThat(UniversityProfilePresentation.score(6.996, ProfileBand.MODERATE)).isEqualTo("6.99");
        assertThat(UniversityProfilePresentation.score(6.9999, ProfileBand.MODERATE)).isEqualTo("6.99");
        assertThat(UniversityProfilePresentation.score(7.0, ProfileBand.HIGH)).isEqualTo("7.00");
        // A HIGH a hair under 7 in floating point may show 7.00; a MODERATE exactly at 3.5 shows 3.50.
        assertThat(UniversityProfilePresentation.score(7.0 - 1.0e-12, ProfileBand.HIGH)).isEqualTo("7.00");
        assertThat(UniversityProfilePresentation.score(3.5, ProfileBand.MODERATE)).isEqualTo("3.50");
    }

    @Test
    void ordinaryScoresRoundHalfUpToTwoDecimals() {
        assertThat(UniversityProfilePresentation.score(2.275, ProfileBand.LOW)).isEqualTo("2.28");
        assertThat(UniversityProfilePresentation.score(7.015, ProfileBand.HIGH)).isEqualTo("7.02");
        assertThat(UniversityProfilePresentation.score(10.0, ProfileBand.HIGH)).isEqualTo("10.00");
        assertThatThrownBy(() -> UniversityProfilePresentation.score(10.5, ProfileBand.HIGH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bandsAreWordedInTheMeasuresOwnContext() {
        assertThat(UniversityProfilePresentation.bandLabel(UniversityProfileMeasure.HUMAN_AND_PRACTICAL_CAPABILITY,
                ProfileBand.HIGH)).isEqualTo("High Human and Practical Capability");
        assertThat(UniversityProfilePresentation.bandLabel(UniversityProfileMeasure.TRANSFERABLE_CAPABILITY,
                ProfileBand.MODERATE)).isEqualTo("Moderate Transferable Capability");
        assertThat(UniversityProfilePresentation.bandLabel(UniversityProfileMeasure.WORKING_ALONGSIDE_AI,
                ProfileBand.LOW)).isEqualTo("Low Working Alongside AI");
        assertThat(UniversityProfilePresentation.HEADING).isEqualTo("Your course profile in an AI-shaped world")
                .doesNotContainIgnoringCase("readiness");
    }
}
