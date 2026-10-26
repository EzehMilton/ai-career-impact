package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.payment.professional.application.PurchasedProfessionalReportSnapshot;
import com.chikere.futureai.pdf.professional.application.*;
import com.chikere.futureai.pdf.professional.openhtml.OpenHtmlProfessionalReportPdfRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHtmlProfessionalReportPdfRendererTest {
    @Test
    void rendersFrozenV1SnapshotAsNetworkFreePdfWithMajorPurchasedSections() throws Exception {
        var source = ProfessionalReportAssemblerTest.source(true, true, true);
        var report = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        var snapshot = new PurchasedProfessionalReportSnapshot(java.util.UUID.randomUUID(),
                source.assessment().id(), report.reportVersion(), source.assessment().createdAt(), report);
        var model = new ProfessionalReportPdfModelMapper().from(snapshot);

        byte[] bytes = new OpenHtmlProfessionalReportPdfRenderer().render(model).bytes();
        assertThat(bytes).isNotEmpty().startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        try (var document = Loader.loadPDF(bytes)) {
            assertThat(document.getNumberOfPages()).isBetween(3, 12);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Executive summary", "Your four measures", "AI Exposure",
                    "Why you received this result", "What your results mean for your work",
                    "What to focus on next", "Work guidance", "About this guidance", "Career context",
                    "About your assessment", "professional-report-v1", "professional-report-pdf-v1",
                    "AI Exposure describes characteristics of the work. It is not a probability");
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                var stripper = new PDFTextStripper(); stripper.setStartPage(page); stripper.setEndPage(page);
                assertThat(stripper.getText(document)).as("page %s should contain readable content", page)
                        .isNotBlank();
            }
        }
    }
}
