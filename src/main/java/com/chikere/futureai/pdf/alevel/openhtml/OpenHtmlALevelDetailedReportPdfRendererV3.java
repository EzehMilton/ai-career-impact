package com.chikere.futureai.pdf.alevel.openhtml;

import com.chikere.futureai.report.alevel.application.ALevelReportVersions;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReport;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportPdfRenderer;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * V3 deliberately keeps the v2 visual layout. The report snapshot supplies the new wording, while the separate PDF
 * version prevents a v3 snapshot from ever being rendered accidentally by the historical v2 renderer.
 */
@Component
public final class OpenHtmlALevelDetailedReportPdfRendererV3 implements ALevelDetailedReportPdfRenderer {

    private final OpenHtmlALevelDetailedReportPdfRenderer layout;

    public OpenHtmlALevelDetailedReportPdfRendererV3(OpenHtmlALevelDetailedReportPdfRenderer layout) {
        this.layout = Objects.requireNonNull(layout);
    }

    @Override
    public String pdfVersion() {
        return ALevelReportVersions.PDF_V3;
    }

    @Override
    public byte[] render(ALevelDetailedReport report) {
        if (!ALevelReportVersions.V3.equals(report.reportVersion())) {
            throw new IllegalArgumentException("v3 PDF renderer requires a-level-report-v3");
        }
        return layout.renderV3(report);
    }

    String html(ALevelDetailedReport report) {
        if (!ALevelReportVersions.V3.equals(report.reportVersion())) {
            throw new IllegalArgumentException("v3 PDF renderer requires a-level-report-v3");
        }
        return layout.htmlV3(report);
    }
}
