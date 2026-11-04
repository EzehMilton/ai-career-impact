package com.chikere.futureai.pdf.university.openhtml;

import com.chikere.futureai.pdf.university.application.UniversityReportPdfGenerationException;
import com.chikere.futureai.report.university.application.UniversityCourseProfile;
import com.chikere.futureai.report.university.application.UniversityReportVersions;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReport;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportPdfRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;

/**
 * The stored detailed report as a local, pure-Java PDF, in the layout of its own version: a university-report-v2
 * document in {@code university-report-pdf-v2}, exactly as before, and a university-report-v3 document in
 * {@code university-report-pdf-v3} ({@link UniversityDetailedReportPdfV3Html}). Built-in fonts, inline
 * CSS and no network inputs. It shows the customer-facing report only: no rule id, probability, close-call, version
 * diagnostic or fallback marker. Headings stay with their content and cards do not split across pages.
 */
@Component
public final class OpenHtmlUniversityDetailedReportPdfRenderer implements UniversityDetailedReportPdfRenderer {

    @Override
    public String pdfVersion() {
        return UniversityReportVersions.PDF_V3;
    }

    @Override
    public Set<String> pdfVersions() {
        return Set.of(UniversityReportVersions.PDF_V2, UniversityReportVersions.PDF_V3);
    }

    @Override
    public byte[] render(UniversityDetailedReport report) {
        try (var output = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder();
            builder.withHtmlContent(html(report), null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        }
        catch (Exception failure) {
            throw new UniversityReportPdfGenerationException("PDF renderer failed", failure);
        }
    }

    String html(UniversityDetailedReport report) {
        return UniversityReportVersions.V2.equals(report.reportVersion()) ? htmlV2(report)
                : UniversityDetailedReportPdfV3Html.html(report);
    }

    /** university-report-pdf-v2, unchanged: stored v2 reports render exactly as they always have. */
    private String htmlV2(UniversityDetailedReport report) {
        StringBuilder out = new StringBuilder(40_000);
        out.append("""
                <html xmlns="http://www.w3.org/1999/xhtml" lang="en"><head><meta charset="UTF-8" />
                <style>
                @page { size:A4; margin:18mm 16mm 18mm; @bottom-center { content:"AI Career Impact · " counter(page) " / " counter(pages); color:#64748b; font-size:8pt; } }
                * { box-sizing:border-box; } body { background:#fff; font-family:Arial, Helvetica, sans-serif; color:#172033; font-size:10pt; line-height:1.48; margin:0; }
                h1 { font-size:26pt; line-height:1.12; color:#102a43; margin:8mm 0 4mm; }
                h2 { color:#123b5d; font-size:16pt; border-bottom:1px solid #cbd5e1; padding-bottom:2mm; margin:8mm 0 3mm; page-break-after:avoid; }
                h3 { color:#174b70; font-size:11.5pt; margin:3mm 0 1.5mm; page-break-after:avoid; }
                p { margin:0 0 2.5mm; } ul { margin:1.5mm 0 3mm; padding-left:6mm; } li { margin-bottom:1.2mm; }
                .cover { page-break-after:always; padding-top:26mm; } .eyebrow { color:#0f766e; font-weight:bold; text-transform:uppercase; letter-spacing:.8pt; }
                .subtitle { font-size:14pt; color:#475569; }
                .notice { background:#ecfeff; border-left:3px solid #0f766e; padding:3mm 4mm; margin:3mm 0; page-break-inside:avoid; }
                .caution { background:#fff7ed; border-left:3px solid #c2410c; padding:3mm 4mm; margin:3mm 0; page-break-inside:avoid; }
                .card { border:1px solid #d8e0e8; border-radius:4px; padding:3.5mm 4mm; margin:0 0 3mm; page-break-inside:avoid; }
                .score { font-size:20pt; font-weight:bold; color:#0f766e; } .band { font-weight:bold; color:#334155; }
                .label { color:#0f766e; font-weight:bold; } .small { color:#64748b; font-size:8.5pt; }
                .section { page-break-inside:auto; }
                </style><title>University course report</title></head><body>
                """);
        out.append("<section class=\"cover\"><div class=\"eyebrow\">Detailed course report</div><h1>")
                .append(escape(report.profile().heading())).append("</h1><p class=\"subtitle\">")
                .append(escape(report.courseTitle()));
        if (!report.institution().isBlank()) {
            out.append(" &#160;&#183;&#160; ").append(escape(report.institution()));
        }
        out.append("</p><div class=\"notice\"><p>").append(escape(report.limitations().getFirst()))
                .append("</p></div></section>");

        out.append("<h2>Your course profile</h2>");
        for (UniversityCourseProfile.Measure measure : report.profile().measures()) {
            out.append("<div class=\"card\"><h3>").append(escape(measure.name())).append("</h3><p><span class=\"score\">")
                    .append(escape(measure.score())).append("</span> / 10 &#160; <span class=\"band\">")
                    .append(escape(measure.bandLabel())).append("</span></p><p>").append(escape(measure.explanation()))
                    .append("</p>");
            list(out, "What strengthens this measure", measure.strengthenedBy());
            list(out, "What holds it back", measure.heldBackBy());
            if (!measure.confidenceNote().isBlank()) {
                out.append("<p class=\"small\">").append(escape(measure.confidenceNote())).append("</p>");
            }
            out.append("</div>");
        }

        out.append("<h2>What this means</h2><p>").append(escape(report.perspectives().overall())).append("</p><p>")
                .append(escape(report.perspectives().transferable())).append("</p>");

        out.append("<h2>Your modules</h2><p class=\"small\">").append(escape(report.modules().selectionNote()))
                .append("</p>");
        for (var module : report.modules().cards()) {
            out.append("<div class=\"card\"><h3>").append(escape(module.title())).append("</h3><p class=\"label\">")
                    .append(escape(module.classification())).append("</p><p>").append(escape(module.explanation()))
                    .append("</p>");
            module.notes().forEach(note -> out.append("<p class=\"small\">").append(escape(note)).append("</p>"));
            out.append("</div>");
        }

        out.append("<h2>Where AI can help your learning</h2><div class=\"caution\"><p>")
                .append(escape(report.aiLearningCaution())).append("</p></div><p>")
                .append(escape(report.perspectives().aiLearning())).append("</p>");
        entries(out, report.aiLearning());

        out.append("<h2>Where your own capability matters most</h2><p>")
                .append(escape(report.perspectives().humanPractical())).append("</p>");
        entries(out, report.ownCapability());

        if (!report.technicalFoundations().isEmpty()) {
            out.append("<h2>Technical and analytical foundations</h2>");
            entries(out, report.technicalFoundations());
        }

        out.append("<h2>Skills to strengthen</h2>");
        for (var skill : report.skills()) {
            out.append("<div class=\"card\"><h3>").append(escape(skill.title())).append("</h3><p>")
                    .append(escape(skill.explanation())).append("</p></div>");
        }

        out.append("<h2>Safeguards</h2>");
        entries(out, report.safeguards());

        out.append("<h2>Your action plan</h2><p>").append(escape(report.perspectives().actionPlanIntroduction()))
                .append("</p>");
        for (var phase : report.plan()) {
            out.append("<div class=\"card\"><h3>").append(escape(phase.title())).append("</h3><ul>");
            phase.actions().forEach(action -> out.append("<li>").append(escape(action)).append("</li>"));
            out.append("</ul></div>");
        }

        out.append("<h2>Perspective and limitations</h2><ul>");
        report.limitations().forEach(item -> out.append("<li>").append(escape(item)).append("</li>"));
        out.append("</ul></body></html>");
        return out.toString();
    }

    private void list(StringBuilder out, String heading, List<String> values) {
        if (values.isEmpty()) return;
        out.append("<p class=\"small\">").append(escape(heading)).append(": ")
                .append(escape(String.join("; ", values))).append("</p>");
    }

    private void entries(StringBuilder out, List<UniversityDetailedReport.Entry> entries) {
        for (var entry : entries) {
            out.append("<div class=\"card\"><h3>").append(escape(entry.title())).append("</h3><p>")
                    .append(escape(entry.text())).append("</p></div>");
        }
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
