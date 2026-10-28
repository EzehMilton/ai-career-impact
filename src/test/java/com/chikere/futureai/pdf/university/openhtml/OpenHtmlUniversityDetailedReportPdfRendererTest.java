package com.chikere.futureai.pdf.university.openhtml;

import com.chikere.futureai.report.university.detailed.UniversityDetailedReport;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportFixtures;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** university-report-pdf-v2: every section, in order, from the stored report only, with no diagnostics. */
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
        assertThat(renderer.pdfVersion()).isEqualTo("university-report-pdf-v2");
    }
}
