package com.chikere.futureai.pdf.alevel.openhtml;

import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportFixtures;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHtmlALevelDetailedReportPdfRendererV3Test {

    private final OpenHtmlALevelDetailedReportPdfRendererV3 renderer =
            new OpenHtmlALevelDetailedReportPdfRendererV3(new OpenHtmlALevelDetailedReportPdfRenderer());

    @Test
    void rendersV3WordingWithTheIndependentV3PdfVersion() {
        var report = ALevelDetailedReportFixtures.reportV3("maths_economics_geography");
        String html = renderer.html(report);

        assertThat(renderer.pdfVersion()).isEqualTo("a-level-report-pdf-v3");
        assertThat(html).contains("Future study options", "Skills your subjects develop", "Maths and problem-solving",
                        "Writing, ideas and the wider world", "How AI can help you learn")
                .doesNotContain("Options Kept Open", "Capability Profile", "Analytical &amp; Quantitative",
                        "Communication, Interpretation &amp; Context", "Learning Alongside AI");
        assertThat(new String(renderer.render(report), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
    }
}
