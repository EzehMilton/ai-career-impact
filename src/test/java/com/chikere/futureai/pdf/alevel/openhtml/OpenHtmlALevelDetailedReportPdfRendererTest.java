package com.chikere.futureai.pdf.alevel.openhtml;

import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportFixtures;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** a-level-report-pdf-v2 and the report it renders: every section, the caution, and no diagnostics or claims. */
class OpenHtmlALevelDetailedReportPdfRendererTest {

    private final OpenHtmlALevelDetailedReportPdfRenderer renderer = new OpenHtmlALevelDetailedReportPdfRenderer();

    @Test
    void rendersEverySectionInOrderWithTheAiCaution() {
        String html = renderer.html(ALevelDetailedReportFixtures.report("english_history_politics"));

        assertThat(html).containsSubsequence("Your A-level subject profile", "Options Kept Open", "Capability Profile",
                "Analytical &amp; Quantitative", "Communication, Interpretation &amp; Context", "Learning Alongside AI",
                "What this combination gives you", "Strengths to develop further", "Areas to strengthen",
                "Options and applications", "What this assessment can say", "What it cannot say",
                "Using AI to support your learning", "does not mean AI use is permitted in assessed work",
                "Skill priorities", "Your action plan", "Now", "During Year 13 and the rest of sixth form",
                "Applications and next-step decisions", "Safeguards", "Perspective and limitations");
        assertThat(html).contains("page-break-inside:avoid", "page-break-after:avoid");
    }

    @Test
    void omitsEmptySectionsAndShowsNoDiagnostics() {
        String html = renderer.html(ALevelDetailedReportFixtures.report("mixed_balanced"));

        assertThat(html).doesNotContain("Areas to strengthen", "Course information from published sources",
                "currently unavailable", "a-level-decision", "a-level-guidance", "a-level-paid-narrative", "AVAILABLE",
                "provenance", "quantitative_reasoning", "%", "Future Optionality", "AI Career Impact Report");
    }

    @Test
    void theStoredReportHoldsNoForbiddenClaimsOrPercentages() throws Exception {
        for (String fixture : java.util.List.of("english_history_politics", "mathematics_physics_computer_science",
                "art_design_media", "mixed_balanced")) {
            var report = ALevelDetailedReportFixtures.report(fixture);
            String customerText = new ObjectMapper().findAndRegisterModules().writeValueAsString(
                    java.util.List.of(report.profile(), report.perspectives(), report.combinationShape(),
                            report.strengths(), report.areasToStrengthen(), report.options(), report.aiStudy(),
                            report.skillPriorities(), report.actionPlan(), report.safeguards()))
                    .replaceAll("\"provenance\":\\[[^\\]]*\\]", "");
            assertThat(customerText).as(fixture).doesNotContainIgnoringCase("guarantee")
                    .doesNotContainIgnoringCase("ai-proof").doesNotContainIgnoringCase("ai cannot")
                    .doesNotContainIgnoringCase("salary").doesNotContain("%").doesNotContainIgnoringCase("overall score")
                    .doesNotContainIgnoringCase("future optionality").doesNotContainIgnoringCase("weak at");
            assertThat(report.aiStudy().caution()).contains("does not mean AI use is permitted in assessed work");
            // The limitations are the one place that names these, as explicit disclaimers.
            assertThat(String.join(" ", report.limitations())).contains("not your ability",
                    "does not predict university offers, admission, employment or salary",
                    "each course publishes its own subject and grade requirements");
        }
    }

    @Test
    void producesTheSamePdfForTheSameStoredReport() {
        var report = ALevelDetailedReportFixtures.report("maths_economics_geography");
        byte[] pdf = renderer.render(report);

        assertThat(new String(pdf, 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        assertThat(renderer.html(report)).isEqualTo(renderer.html(report));
        assertThat(renderer.pdfVersion()).isEqualTo("a-level-report-pdf-v2");
    }
}
