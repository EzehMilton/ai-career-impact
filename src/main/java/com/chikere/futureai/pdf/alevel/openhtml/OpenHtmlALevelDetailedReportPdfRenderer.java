package com.chikere.futureai.pdf.alevel.openhtml;

import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfGenerationException;
import com.chikere.futureai.report.alevel.application.ALevelReportVersions;
import com.chikere.futureai.report.alevel.application.ALevelSubjectProfile;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReport;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportPdfRenderer;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * {@code a-level-report-pdf-v2}: the stored a-level-report-v2 as a local, pure-Java PDF. Built-in fonts, inline CSS
 * and no network inputs. It shows the customer-facing report only: no provenance, rule id, confidence value, version,
 * narrative status or fallback marker. Headings stay with their content and cards do not split across pages.
 */
@Component
public final class OpenHtmlALevelDetailedReportPdfRenderer implements ALevelDetailedReportPdfRenderer {

    @Override
    public String pdfVersion() {
        return ALevelReportVersions.PDF_V2;
    }

    @Override
    public byte[] render(ALevelDetailedReport report) {
        return render(report, false);
    }

    byte[] renderV3(ALevelDetailedReport report) {
        return render(report, true);
    }

    private byte[] render(ALevelDetailedReport report, boolean protectSectionLeads) {
        try (var output = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder();
            builder.withHtmlContent(html(report, protectSectionLeads), null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        }
        catch (Exception failure) {
            throw new ALevelReportPdfGenerationException("PDF renderer failed", failure);
        }
    }

    String html(ALevelDetailedReport report) {
        return html(report, false);
    }

    String htmlV3(ALevelDetailedReport report) {
        return html(report, true);
    }

    private String html(ALevelDetailedReport report, boolean protectSectionLeads) {
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
                .small { color:#64748b; font-size:8.5pt; }
                </style><title>A-level subject report</title></head><body>
                """);
        out.append("<section class=\"cover\"><div class=\"eyebrow\">Detailed A-level report</div><h1>")
                .append(escape(report.profile().heading())).append("</h1><p class=\"subtitle\">")
                .append(escape(String.join(", ", report.subjects()))).append("</p><div class=\"notice\"><p>")
                .append(escape(report.limitations().getFirst())).append("</p></div></section>");

        var profile = report.profile();
        sectionLeadStart(out, protectSectionLeads);
        out.append("<h2>Your A-level subject profile</h2><p>").append(escape(report.perspectives().profile()))
                .append("</p>");
        sectionLeadEnd(out, protectSectionLeads);
        measure(out, profile.optionsKeptOpen());
        out.append("<h3>").append(ALevelReportVersions.V3.equals(report.reportVersion())
                        ? "Skills your subjects develop" : "Capability Profile")
                .append("</h3><p class=\"small\">").append(escape(profile.capabilityProfile().note()))
                .append("</p>");
        profile.capabilityProfile().sides().forEach(side -> measure(out, side));
        out.append("<p>").append(escape(report.perspectives().capability())).append("</p>");
        measure(out, profile.learningAlongsideAi());

        sectionWithEntries(out, "What this combination gives you", report.combinationShape(), protectSectionLeads);

        sectionWithEntries(out, "Strengths to develop further", report.strengths(), protectSectionLeads);

        if (!report.areasToStrengthen().isEmpty()) {
            sectionWithEntries(out, "Areas to strengthen", report.areasToStrengthen(), protectSectionLeads);
        }

        var options = report.options();
        sectionLeadStart(out, protectSectionLeads);
        out.append("<h2>Options and applications</h2><p>").append(escape(report.perspectives().options()))
                .append("</p>");
        sectionLeadEnd(out, protectSectionLeads);
        out.append("<div class=\"card\"><h3>What this assessment can say</h3>");
        list(out, options.canSay());
        out.append("<h3>What it cannot say</h3>");
        list(out, options.cannotSay());
        out.append("</div>");
        entries(out, options.checks());
        if (!options.gradesNote().isBlank()) {
            out.append("<div class=\"notice\"><p>").append(escape(options.gradesNote())).append("</p></div>");
        }

        sectionLeadStart(out, protectSectionLeads);
        out.append("<h2>Using AI to support your learning</h2><div class=\"caution\"><p>")
                .append(escape(report.aiStudy().caution())).append("</p></div>");
        sectionLeadEnd(out, protectSectionLeads);
        out.append("<p>").append(escape(report.perspectives().aiLearning())).append("</p>");
        entries(out, report.aiStudy().uses());

        sectionWithEntries(out, "Skill priorities", report.skillPriorities(), protectSectionLeads);

        sectionLeadStart(out, protectSectionLeads);
        out.append("<h2>Your action plan</h2><p>").append(escape(report.perspectives().actionPlanIntroduction()))
                .append("</p>");
        sectionLeadEnd(out, protectSectionLeads);
        phase(out, "Now", report.actionPlan().now());
        phase(out, "During Year 13 and the rest of sixth form", report.actionPlan().sixthForm());
        phase(out, "Applications and next-step decisions", report.actionPlan().applications());

        sectionWithEntries(out, "Safeguards", report.safeguards(), protectSectionLeads);

        if (!report.evidence().isEmpty()) {
            sectionLeadStart(out, protectSectionLeads);
            out.append("<h2>Course information from published sources</h2><p class=\"small\">These are factual notes "
                    + "with their own sources. They are separate from your assessment and do not change it.</p>");
            sectionLeadEnd(out, protectSectionLeads);
            for (var item : report.evidence()) {
                out.append("<div class=\"card\"><h3>").append(escape(item.title())).append("</h3><p>")
                        .append(escape(item.summary())).append("</p><p class=\"small\">Source: ")
                        .append(escape(item.sourceName())).append("</p></div>");
            }
        }

        sectionLeadStart(out, protectSectionLeads);
        out.append("<h2>Perspective and limitations</h2><ul>");
        report.limitations().forEach(item -> out.append("<li>").append(escape(item)).append("</li>"));
        out.append("</ul>");
        sectionLeadEnd(out, protectSectionLeads);
        out.append("</body></html>");
        return out.toString();
    }

    private void measure(StringBuilder out, ALevelSubjectProfile.Measure measure) {
        out.append("<div class=\"card\"><h3>").append(escape(measure.name())).append("</h3><p><span class=\"score\">")
                .append(escape(measure.score())).append("</span> / 10 &#160; <span class=\"band\">")
                .append(escape(measure.bandLabel())).append("</span></p><p>").append(escape(measure.explanation()))
                .append("</p>");
        if (!measure.drivers().isEmpty()) {
            out.append("<p class=\"small\">What builds this: ").append(escape(String.join("; ", measure.drivers())))
                    .append("</p>");
        }
        if (!measure.limits().isEmpty()) {
            out.append("<p class=\"small\">What narrows it: ").append(escape(String.join("; ", measure.limits())))
                    .append("</p>");
        }
        if (!measure.confidenceNote().isBlank()) {
            out.append("<p class=\"small\">").append(escape(measure.confidenceNote())).append("</p>");
        }
        out.append("</div>");
    }

    private void phase(StringBuilder out, String title, List<ALevelDetailedReport.Entry> actions) {
        if (actions.isEmpty()) return;
        out.append("<div class=\"card\"><h3>").append(escape(title)).append("</h3><ul>");
        actions.forEach(action -> out.append("<li><strong>").append(escape(action.title())).append(".</strong> ")
                .append(escape(action.text())).append("</li>"));
        out.append("</ul></div>");
    }

    private void entries(StringBuilder out, List<ALevelDetailedReport.Entry> entries) {
        for (var entry : entries) {
            out.append("<div class=\"card\"><h3>").append(escape(entry.title())).append("</h3><p>")
                    .append(escape(entry.text())).append("</p></div>");
        }
    }

    private void sectionWithEntries(StringBuilder out, String heading, List<ALevelDetailedReport.Entry> entries,
                                    boolean protectSectionLead) {
        sectionLeadStart(out, protectSectionLead);
        out.append("<h2>").append(escape(heading)).append("</h2>");
        if (!entries.isEmpty()) {
            entry(out, entries.getFirst());
        }
        sectionLeadEnd(out, protectSectionLead);
        entries.stream().skip(1).forEach(entry -> entry(out, entry));
    }

    private void entry(StringBuilder out, ALevelDetailedReport.Entry entry) {
        out.append("<div class=\"card\"><h3>").append(escape(entry.title())).append("</h3><p>")
                .append(escape(entry.text())).append("</p></div>");
    }

    private void sectionLeadStart(StringBuilder out, boolean protectSectionLead) {
        if (protectSectionLead) {
            out.append("<div class=\"section-lead\" style=\"page-break-inside:avoid;\">");
        }
    }

    private void sectionLeadEnd(StringBuilder out, boolean protectSectionLead) {
        if (protectSectionLead) {
            out.append("</div>");
        }
    }

    private void list(StringBuilder out, List<String> items) {
        out.append("<ul>");
        items.forEach(item -> out.append("<li>").append(escape(item)).append("</li>"));
        out.append("</ul>");
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
