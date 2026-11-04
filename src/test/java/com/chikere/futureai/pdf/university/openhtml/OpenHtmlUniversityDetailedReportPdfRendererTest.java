package com.chikere.futureai.pdf.university.openhtml;

import com.chikere.futureai.report.university.detailed.UniversityDetailedReport;
import com.chikere.futureai.report.university.detailed.UniversityCalibrationChains;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The detailed-report PDF in the layout of the stored document's own version: university-report-pdf-v2 for a stored v2
 * report, exactly as before, and university-report-pdf-v3 for v3. Every section, in order, with no diagnostics.
 */
class OpenHtmlUniversityDetailedReportPdfRendererTest {

    private final OpenHtmlUniversityDetailedReportPdfRenderer renderer = new OpenHtmlUniversityDetailedReportPdfRenderer();

    private static UniversityDetailedReport report(List<UniversityDetailedReport.Entry> technical) {
        return UniversityDetailedReportFixtures.report(technical);
    }

    @Test
    void rendersEverySectionInOrderAndEscapesStoredText() {
        String html = renderer.html(report(List.of(new UniversityDetailedReport.Entry("Pharmacology", "Dosage."))));

        assertThat(html).containsSubsequence("Your course profile in an AI-shaped world", "Your course profile",
                "What this means", "Your modules", "Where AI can help your learning",
                "This does not mean AI use is permitted in assessed work", "Where your own capability matters most",
                "Technical and analytical foundations", "Skills to strengthen", "Safeguards", "Your action plan",
                "Now", "This year", "Later", "Perspective and limitations");
        assertThat(html).contains("BSc Nursing &lt;Adult&gt;").contains("page-break-inside:avoid");
    }

    @Test
    void omitsTheTechnicalSectionWhenThereIsNothingInIt() {
        assertThat(renderer.html(report(List.of()))).doesNotContain("Technical and analytical foundations");
    }

    @Test
    void showsNoVersionsStatusesOrDiagnostics() {
        String html = renderer.html(report(List.of()));

        assertThat(html).doesNotContain("university-decision-v2").doesNotContain("university-paid-narrative")
                .doesNotContain("AVAILABLE").doesNotContainIgnoringCase("fallback")
                .doesNotContainIgnoringCase("probability").doesNotContain("described_clearly");
    }

    @Test
    void producesTheSamePdfForTheSameStoredReport() {
        var stored = report(List.of());
        byte[] pdf = renderer.render(stored);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(renderer.html(stored)).isEqualTo(renderer.html(stored));
        assertThat(renderer.pdfVersion()).isEqualTo("university-report-pdf-v3");
        assertThat(renderer.pdfVersions()).containsExactlyInAnyOrder("university-report-pdf-v2",
                "university-report-pdf-v3");
    }

    @Test
    void aStoredV2ReportRendersTheExactPdfHtmlItWasStoredWith() throws Exception {
        var stored = new ObjectMapper().findAndRegisterModules().readValue(
                historical("university-report-v2-nursing.json"), UniversityDetailedReport.class);
        assertThat(stored.reportVersion()).isEqualTo("university-report-v2");
        assertThat(stored.practical()).isNull();
        // Captured from the pre-v3 code: a stored v2 report is never re-laid out.
        assertThat(renderer.html(stored)).isEqualTo(historical("university-report-pdf-v2-nursing.html"));
    }

    @Test
    void aV3ReportUsesTheV3LayoutWithNoDiagnostics() {
        var report = UniversityCalibrationChains.report(UniversityCalibrationChains.NURSING);
        String html = renderer.html(report);
        assertThat(html).contains("How to study in an AI-shaped university", "How AI could help",
                        "What you should still be able to do yourself", "A sensible study experiment",
                        "In this course, that means", "Why your capability matters here")
                .doesNotContain("university-decision-v2", "university-paid-narrative", "AVAILABLE", "module-2",
                        "skill-technical-foundations", "next_30_days")
                .doesNotContainIgnoringCase("fallback").doesNotContainIgnoringCase("probability");
        assertThat(new String(renderer.render(report), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }

    private static String historical(String name) throws Exception {
        return Files.readString(Path.of("src/test/resources/historical/university", name));
    }
}
