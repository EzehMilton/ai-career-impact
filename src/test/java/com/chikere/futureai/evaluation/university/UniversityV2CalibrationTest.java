package com.chikere.futureai.evaluation.university;

import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV2;
import com.chikere.futureai.decision.university.UniversityProfileMeasure;
import com.chikere.futureai.evaluation.university.UniversityBaselineHarness.Mode;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assumptions.assumeThat;

/** Calibration of the approved v2 formulas over the frozen captures; it mirrors, never replaces, the policy. */
class UniversityV2CalibrationTest {

    @Test
    void theApprovedCalibrationIsExactlyTheProductionPolicy() {
        List<UniversityProfileMeasure> measures = List.of(UniversityProfileMeasure.WORKING_ALONGSIDE_AI,
                UniversityProfileMeasure.HUMAN_AND_PRACTICAL_CAPABILITY, UniversityProfileMeasure.TRANSFERABLE_CAPABILITY);
        assertThat(UniversityV2Calibration.APPROVED).hasSize(3);
        for (int index = 0; index < 3; index++) {
            assertThat(UniversityV2Calibration.APPROVED.get(index).id()).isEqualTo(measures.get(index).name());
            assertThat(UniversityV2Calibration.APPROVED.get(index).weights())
                    .isEqualTo(UniversityDecisionPolicyV2.weightsFor(measures.get(index)));
        }
        assertThat(UniversityV2Calibration.HISTORICAL_CANDIDATE_A.name()).contains("historical", "not approved");
    }

    @Test
    void everyDimensionContributesAndContextualComplexityIsInAllThreeOnPurpose() {
        Set<String> used = new HashSet<>();
        UniversityV2Calibration.APPROVED.forEach(candidate -> used.addAll(candidate.weights().keySet()));
        assertThat(used).containsExactlyInAnyOrderElementsOf(UniversityDecisionPolicyV1.SCORE_DIMENSIONS);
        for (String dimension : UniversityDecisionPolicyV1.SCORE_DIMENSIONS) {
            long measures = UniversityV2Calibration.APPROVED.stream()
                    .filter(candidate -> candidate.weights().containsKey(dimension)).count();
            if (dimension.equals("contextual_complexity")) {
                assertThat(measures).as(dimension).isEqualTo(3);
            }
            else {
                assertThat(measures).as(dimension).isBetween(1L, 2L);
            }
        }
    }

    @Test
    void theApprovedCalibrationReproducesTheSevenApprovedProfiles() throws Exception {
        Map<String, double[]> expected = Map.of(
                "computer-science", new double[] {8.91, 5.65, 7.18},
                "mechanical-engineering", new double[] {9.00, 5.94, 6.27},
                "graphic-design", new double[] {7.51, 7.97, 4.60},
                "nursing", new double[] {7.02, 7.67, 4.16},
                "english-literature", new double[] {6.42, 5.19, 6.78},
                "law", new double[] {7.28, 5.33, 6.00},
                "business-management", new double[] {7.30, 5.87, 7.66});
        var courses = UniversityV2Calibration.load(UniversityV2Calibration.FIXTURES);
        assertThat(courses).extracting(UniversityV2Calibration.Course::id)
                .containsExactlyInAnyOrderElementsOf(expected.keySet());
        for (var course : courses) {
            for (int index = 0; index < 3; index++) {
                assertThat(UniversityV2Calibration.evaluate(UniversityV2Calibration.APPROVED.get(index), course)
                        .indicator()).as(course.id() + " " + index).isCloseTo(expected.get(course.id())[index],
                                within(0.01));
            }
        }
    }

    @Test
    void theSimulationRunsOverTheDryRunCapturesAndRanksFactorsDeterministically() throws Exception {
        Path directory = Files.createTempDirectory("university-v2");
        var runs = new UniversityBaselineHarness(new UniversityBaselineHarnessTest.Synthetic(), new UniversityDecisionPolicyV1())
                .run(UniversityBaselineCases.ALL);
        UniversityBaselineHarness.write(directory, Mode.DRY_RUN, "synthetic", Instant.now(), runs);

        var courses = UniversityV2Calibration.load(directory);
        assertThat(courses).hasSize(7);
        for (var candidate : UniversityV2Calibration.APPROVED) {
            for (var course : courses) {
                var first = UniversityV2Calibration.evaluate(candidate, course);
                assertThat(UniversityV2Calibration.evaluate(candidate, course)).isEqualTo(first);
                double sum = first.contributions().stream().mapToDouble(UniversityV2Calibration.Factor::effect).sum();
                assertThat(first.indicator()).isCloseTo(5.0 + sum, org.assertj.core.data.Offset.offset(1.0e-9));
            }
        }
        assertThat(UniversityV2Calibration.report(courses)).contains("v1 confidence diagnosis", "Transferable Capability",
                "Historical comparison only");
    }

    /** Writes the calibration report once the frozen Stage 5B captures are in place; skipped until then. */
    @Test
    void writesTheCalibrationReportFromTheFrozenLiveCaptures() throws Exception {
        assumeThat(Files.isDirectory(UniversityV2Calibration.FIXTURES)).as("frozen Stage 5B captures").isTrue();
        var courses = UniversityV2Calibration.load(UniversityV2Calibration.FIXTURES);
        assertThat(courses).hasSize(7);
        Path report = Path.of("target", "university-v2-calibration", "report.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, UniversityV2Calibration.report(courses), StandardCharsets.UTF_8);
    }
}
