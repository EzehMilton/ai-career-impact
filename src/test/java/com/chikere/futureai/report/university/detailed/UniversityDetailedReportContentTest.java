package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.narrative.university.paid.ScriptedPaidNarrativeGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The customer-facing university-report-v2 content rules, on a report generated from the frozen Nursing fixtures. */
class UniversityDetailedReportContentTest {

    private final DetailedReportTestPorts ports = new DetailedReportTestPorts();

    private UniversityDetailedReport report() {
        UUID id = ports.paidCourseProfile(8);
        ports.narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());
        return ports.service().prepare(id, Optional.empty()).report();
    }

    @Test
    void theCourseProfileHasExactlyThreeMeasuresAndNoOverallScore() {
        var profile = report().profile();

        assertThat(profile.heading()).isEqualTo("Your course profile in an AI-shaped world");
        assertThat(profile.measures()).extracting(measure -> measure.name()).containsExactly(
                "Working Alongside AI", "Human and Practical Capability", "Transferable Capability");
        profile.measures().forEach(measure -> {
            assertThat(measure.strengthenedBy()).hasSizeLessThanOrEqualTo(2);
            assertThat(measure.heldBackBy()).hasSizeLessThanOrEqualTo(2);
            assertThat(measure.bandLabel()).matches("(?i).*(low|moderate|high).*");
        });
        assertThat(profile.summary()).doesNotContainIgnoringCase("overall").doesNotContainIgnoringCase("strongest");
    }

    @Test
    void everySectionFollowsItsContentRules() {
        var report = report();

        assertThat(report.aiLearningCaution()).isEqualTo("These are areas where AI may help with learning, "
                + "preparation, exploration or checking. This does not mean AI use is permitted in assessed work.");
        assertThat(report.skills()).hasSizeBetween(3, 5);
        assertThat(report.plan()).hasSize(3).allSatisfy(phase -> assertThat(phase.actions()).hasSizeGreaterThanOrEqualTo(2));
        assertThat(report.safeguards()).isNotEmpty();
        assertThat(report.modules().cards()).hasSize(8).allSatisfy(card -> {
            assertThat(card.classification()).isNotBlank();
            assertThat(card.explanation()).isNotBlank();
        });
        assertThat(String.join(" ", report.limitations())).contains("not your own ability")
                .contains("not a prediction of employment, job prospects or salary")
                .contains("AI capabilities and university policies change")
                .contains("check your institution's")
                .contains("External labour-market evidence is not part of");
    }

    @Test
    void theStoredReportHoldsNoDiagnosticsOrForbiddenClaims() throws Exception {
        var report = report();
        String customerFacing = new ObjectMapper().findAndRegisterModules()
                .writeValueAsString(withoutProvenance(report));

        assertThat(customerFacing).doesNotContainIgnoringCase("described_clearly")
                .doesNotContainIgnoringCase("describedClearly")
                .doesNotContainIgnoringCase("probability")
                .doesNotContainIgnoringCase("ruleId")
                .doesNotContainPattern("\\bUM\\d")
                .doesNotContainIgnoringCase("relaxed")
                .doesNotContainIgnoringCase("fallback")
                .doesNotContainIgnoringCase("AI cannot")
                .doesNotContainIgnoringCase("AI-proof")
                .doesNotContainIgnoringCase("safe from automation");
    }

    private static UniversityDetailedReport withoutProvenance(UniversityDetailedReport report) {
        return new UniversityDetailedReport(report.reportVersion(), report.courseTitle(), report.institution(),
                report.profile(), report.perspectives(), report.modules(), report.aiLearningCaution(),
                report.aiLearning(), report.ownCapability(), report.technicalFoundations(), report.skills(),
                report.safeguards(), report.plan(), report.limitations(), new UniversityDetailedReport.Provenance(
                        null, null, "", "", "", "", "", "", ""), report.practical());
    }
}
