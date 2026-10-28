package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.livesmoke.LiveSmokeCaseCatalog;
import com.chikere.futureai.livesmoke.LiveSmokeJourney;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The live-smoke calibration courses are the frozen Stage 5B inputs, field for field, so live runs stay comparable. */
class LiveSmokeCalibrationCasesTest {

    private final LiveSmokeCaseCatalog catalog = new LiveSmokeCaseCatalog();

    @Test
    void everyCalibrationCourseMatchesItsFrozenBaselineInput() {
        assertThat(UniversityBaselineCases.ALL).hasSize(7);
        for (var baseline : UniversityBaselineCases.ALL) {
            var smoke = catalog.find(LiveSmokeJourney.UNIVERSITY, "calibration-" + baseline.id()).orElseThrow();
            var state = baseline.state();
            assertThat(smoke.fields()).as(baseline.id())
                    .containsEntry("courseTitle", state.courseTitle())
                    .containsEntry("courseDescription", state.courseDescription())
                    .containsEntry("keyModules", String.join("\n", state.keyModules()))
                    .containsEntry("skillsDeveloped", String.join("\n", state.skillsDeveloped()))
                    .containsEntry("preferredCareerAreas", String.join("\n", state.preferredCareerAreas()))
                    .containsEntry("country", state.country().orElseThrow())
                    .doesNotContainKey("institution");
            // Each lists more than eight modules, so each exercises the module-selection step.
            assertThat(state.keyModules()).hasSizeGreaterThan(8);
        }
    }
}
