package com.chikere.futureai.pdf.alevel.openhtml;

import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportFixtures;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

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

    @Test
    void protectsEachMajorSectionHeadingWithItsFirstMeaningfulContentBlock() {
        String html = renderer.html(ALevelDetailedReportFixtures.reportV3("maths_economics_geography"));

        assertThat(html).contains(
                protectedHeading("Options and applications"),
                protectedHeading("Using AI to support your learning"),
                protectedHeading("Skill priorities"),
                protectedHeading("Your action plan"),
                protectedHeading("Safeguards"),
                protectedHeading("Perspective and limitations"));
    }

    @Test
    void renderedHeadingsShareAPageWithTheirFirstContentAcrossRepresentativeReports() throws Exception {
        for (String fixture : List.of("maths_economics_geography", "english_history_politics", "mixed_balanced")) {
            var report = ALevelDetailedReportFixtures.reportV3(fixture);
            byte[] pdf = renderer.render(report);
            writeVisualSampleWhenRequested(fixture, pdf);
            var pages = pageTexts(pdf);

            var expectedContent = new LinkedHashMap<String, String>();
            expectedContent.put("Options and applications", report.perspectives().options());
            expectedContent.put("Using AI to support your learning", report.aiStudy().caution());
            expectedContent.put("Skill priorities", report.skillPriorities().getFirst().title());
            expectedContent.put("Your action plan", report.perspectives().actionPlanIntroduction());
            expectedContent.put("Safeguards", report.safeguards().getFirst().title());
            expectedContent.put("Perspective and limitations", report.limitations().getFirst());

            expectedContent.forEach((heading, firstContent) -> {
                String headingPage = pages.stream().filter(page -> page.contains(normalise(heading))).findFirst()
                        .orElseThrow(() -> new AssertionError("Missing heading in " + fixture + ": " + heading));
                assertThat(headingPage).as("%s heading and first content in %s", heading, fixture)
                        .contains(normalise(firstContent));
            });
            assertThat(pages).as("no blank pages in %s", fixture).allMatch(page -> page.length() > 40);
        }
    }

    private String protectedHeading(String heading) {
        return "<div class=\"section-lead\" style=\"page-break-inside:avoid;\"><h2>" + heading + "</h2>";
    }

    private List<String> pageTexts(byte[] pdf) throws IOException {
        try (var document = Loader.loadPDF(pdf)) {
            var pages = new java.util.ArrayList<String>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                var stripper = new PDFTextStripper();
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(normalise(stripper.getText(document)));
            }
            return List.copyOf(pages);
        }
    }

    private String normalise(String value) {
        return value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }

    private void writeVisualSampleWhenRequested(String fixture, byte[] pdf) throws IOException {
        String directory = System.getProperty("a-level.pdf.visual-dir");
        if (directory == null || directory.isBlank()) {
            return;
        }
        Path outputDirectory = Path.of(directory);
        Files.createDirectories(outputDirectory);
        Files.write(outputDirectory.resolve(fixture + ".pdf"), pdf);
        try (var document = Loader.loadPDF(pdf)) {
            var pdfRenderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                ImageIO.write(pdfRenderer.renderImageWithDPI(page, 110), "png",
                        outputDirectory.resolve(fixture + "-page-" + (page + 1) + ".png").toFile());
            }
        }
    }
}
