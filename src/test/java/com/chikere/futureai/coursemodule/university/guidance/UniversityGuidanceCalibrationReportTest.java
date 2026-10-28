package com.chikere.futureai.coursemodule.university.guidance;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Writes target/university-guidance-calibration/report.md from the seven frozen courses. */
class UniversityGuidanceCalibrationReportTest {

    @Test
    void writesTheGuidanceCalibrationReportForAllSevenCourses() throws Exception {
        String report = UniversityGuidanceCalibrationReport.render(GuidanceFixtures.all(), new UniversityGuidancePolicyV1());
        Path file = Path.of("target", "university-guidance-calibration", "report.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, report, StandardCharsets.UTF_8);
        assertThat(report).contains("## nursing", "## computer-science", "## english-literature");
    }
}
