package com.chikere.futureai.pdf.alevel.openhtml;

import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfGenerationException;
import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfModel;
import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfRenderer;
import com.chikere.futureai.pdf.alevel.application.RenderedALevelReportPdf;
import com.chikere.futureai.report.alevel.application.ALevelReport;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Local, pure-Java renderer.
 *
 * <p>The template uses built-in font families, inline CSS and no images, remote stylesheets, CDN
 * resources or other network inputs. Persisted safe HTTP(S) evidence URLs are emitted only as
 * hyperlinks; they are never fetched.</p>
 */
@Component
public final class OpenHtmlALevelReportPdfRenderer implements ALevelReportPdfRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.UK)
            .withZone(ZoneId.of("Europe/London"));

    @Override
    public RenderedALevelReportPdf render(ALevelReportPdfModel model) {
        try (var output = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder();
            builder.withHtmlContent(html(model), null);
            builder.toStream(output);
            builder.run();
            return new RenderedALevelReportPdf(output.toByteArray());
        }
        catch (Exception failure) {
            throw new ALevelReportPdfGenerationException("PDF renderer failed", failure);
        }
    }

    String html(ALevelReportPdfModel model) {
        ALevelReport report = model.report();
        StringBuilder out = new StringBuilder(32_000);
        out.append("""
                <html xmlns="http://www.w3.org/1999/xhtml" lang="en"><head><meta charset="UTF-8" />
                <style>
                @page { size:A4; margin:18mm 16mm 18mm; @bottom-center { content:"AI Career Impact · " counter(page) " / " counter(pages); color:#64748b; font-size:8pt; } }
                * { box-sizing:border-box; } body { background:#fff; font-family:Arial, Helvetica, sans-serif; color:#172033; font-size:10pt; line-height:1.48; margin:0; }
                h1 { font-size:28pt; line-height:1.12; color:#102a43; margin:8mm 0 4mm; } h2 { color:#123b5d; font-size:17pt; border-bottom:1px solid #cbd5e1; padding-bottom:2mm; margin:9mm 0 4mm; }
                h3 { color:#174b70; font-size:12pt; margin:4mm 0 2mm; } p { margin:0 0 3mm; } ul { margin:2mm 0 4mm; padding-left:6mm; } li { margin-bottom:1.5mm; }
                .cover { page-break-after:always; padding-top:28mm; } .eyebrow { color:#0f766e; font-weight:bold; text-transform:uppercase; letter-spacing:.8pt; }
                .subtitle { font-size:14pt; color:#475569; } .meta { margin-top:20mm; color:#64748b; font-size:9pt; }
                .notice { background:#ecfeff; border-left:3px solid #0f766e; padding:3mm 4mm; margin:4mm 0; }
                .warning { background:#fff7ed; border-left:3px solid #c2410c; padding:3mm 4mm; }
                .card { border:1px solid #d8e0e8; border-radius:4px; padding:4mm; margin:0 0 4mm; page-break-inside:avoid; }
                .measure { width:48%; display:inline-block; vertical-align:top; margin-right:1.5%; min-height:46mm; }
                .score { font-size:22pt; font-weight:bold; color:#0f766e; margin:1mm 0; } .pill { color:#334155; font-weight:bold; }
                .small { color:#64748b; font-size:8.5pt; } a { color:#075985; text-decoration:underline; }
                </style><title>Purchased A-level report</title></head><body>
                """);

        String subjects = String.join(", ", report.selectedSubjects());
        out.append("<section class=\"cover\"><div class=\"eyebrow\">Purchased A-level report</div><h1>")
                .append(escape(report.headline())).append("</h1><p class=\"subtitle\">")
                .append(escape(subjects)).append("</p><div class=\"notice\"><strong>About this report</strong><p>")
                .append(escape(report.assessmentSummary().methodologyCaveat())).append("</p></div>");
        if (report.developmentFixture()) {
            out.append("<div class=\"warning\"><strong>Development fixture report</strong><p>This content "
                    + "uses local fixture assessment, narrative, evidence and payment data. No live "
                    + "provider was contacted.</p></div>");
        }
        out.append("<div class=\"meta\"><p>Purchased report date: ").append(DATE.format(model.purchasedAt()))
                .append("</p><p>Content version: ").append(escape(model.purchasedReportVersion()))
                .append("<br />PDF presentation: ").append(escape(model.pdfTemplateVersion()))
                .append("</p></div></section>");

        heading(out, "What this suggests");
        out.append("<div class=\"card\"><h3>").append(escape(report.headline())).append("</h3><p>")
                .append(escape(report.assessmentSummary().interpretation()))
                .append("</p><p><span class=\"pill\">Status:</span> ")
                .append(label(report.assessmentSummary().status().name()))
                .append(" &#160; <span class=\"pill\">Assessment confidence:</span> ")
                .append(label(report.assessmentSummary().confidence().name()))
                .append("</p></div><div class=\"warning\">")
                .append(escape(report.assessmentSummary().admissionsDisclaimer())).append("</div>");

        if (!report.predictedGrades().isEmpty()) {
            heading(out, "Your subjects and predicted grades");
            out.append("<ul>");
            report.selectedSubjects().forEach(subject -> {
                out.append("<li>").append(escape(subject));
                String grade = report.predictedGrades().get(subject);
                if (grade != null) out.append(" &#8212; predicted ").append(escape(grade));
                out.append("</li>");
            });
            out.append("</ul>");
        }

        heading(out, "Your six measures");
        for (var measure : report.measures()) {
            out.append("<article class=\"card measure\"><h3>").append(measureName(measure.measure().name()))
                    .append("</h3><div class=\"score\">")
                    .append(String.format(Locale.ROOT, "%.2f", measure.indicator()))
                    .append(" / 10</div><p><span class=\"pill\">Band:</span> ").append(label(measure.band().name()))
                    .append("<br /><span class=\"pill\">Confidence:</span> ")
                    .append(String.format(Locale.ROOT, "%.0f%%", measure.confidence() * 100)).append("</p><p>")
                    .append(escape(measure.conciseExplanation())).append("</p>");
            measure.detailedExplanation().ifPresent(value ->
                    out.append("<p>").append(escape(value)).append("</p>"));
            out.append("</article>");
        }

        heading(out, "Strengths and areas to research");
        listBlock(out, "Main strengths", report.mainStrengths());
        listBlock(out, "Areas you may want to supplement", report.developmentOpportunities());

        heading(out, "Full interpretation");
        var narrative = report.narrative();
        if (narrative.available()) {
            section(out, "What this means", narrative.whatThisMeans().orElse(null));
            section(out, "Future Optionality", narrative.futureOptionalityExplanation().orElse(null));
            section(out, "What your combination builds well", narrative.strengthsExplanation().orElse(null));
            section(out, "Degree pathway breadth", narrative.degreePathwayExplanation().orElse(null));
            section(out, "Analytical foundation", narrative.analyticalFoundationExplanation().orElse(null));
            section(out, "Human and creative strength", narrative.humanAndCreativeExplanation().orElse(null));
            section(out, "Transferable capabilities", narrative.transferabilityExplanation().orElse(null));
            section(out, "Adaptability", narrative.adaptabilityExplanation().orElse(null));
            section(out, "What you may want to strengthen",
                    narrative.developmentOpportunitiesExplanation().orElse(null));
            narrative.cautionOrLimitations().ifPresent(value ->
                    out.append("<div class=\"notice\">").append(escape(value)).append("</div>"));
        }
        else {
            out.append("<p>The personalised explanation was not available when this report was purchased.</p>");
        }

        if (!report.practicalActions().isEmpty()) {
            heading(out, "Practical next steps");
            listBlock(out, null, report.practicalActions());
        }

        if (!report.guidance().isEmpty()) {
            heading(out, "Skills worth developing alongside your subjects");
            for (var item : report.guidance()) {
                out.append("<div class=\"card\"><p class=\"small\">").append(label(item.category().name()))
                        .append("</p><h3>").append(escape(item.title())).append("</h3><p>")
                        .append(escape(item.suggestion())).append("</p></div>");
            }
        }

        heading(out, "Degree and career areas you mentioned");
        out.append("<p>").append(escape(report.preferredAreas().guidance())).append("</p>");
        listBlock(out, "Degree areas", report.preferredAreas().degreeAreas());
        listBlock(out, "Career areas", report.preferredAreas().careerAreas());
        out.append("<p class=\"small\">").append(escape(report.preferredAreas().limitation())).append("</p>");

        heading(out, "Factual context");
        out.append("<p class=\"small\">").append(escape(report.evidence().separationNote())).append("</p>");
        if (report.evidence().items().isEmpty()) {
            out.append("<p>").append(escape(report.evidence().message())).append("</p>");
        }
        for (var item : report.evidence().items()) {
            out.append("<div class=\"card\"><p class=\"small\">").append(label(item.category()))
                    .append("</p><h3>").append(escape(item.title())).append("</h3><p>")
                    .append(escape(item.summary())).append("</p><p class=\"small\">Source: ")
                    .append(escape(item.sourceName()));
            item.sourceUrl().ifPresent(url -> out.append(" &#8212; <a href=\"").append(escape(url.toString()))
                    .append("\">").append(escape(url.toString())).append("</a>"));
            out.append("</p><p class=\"small\">").append(escape(item.relevanceNote())).append("</p></div>");
        }

        heading(out, "Methodology and limitations");
        listBlock(out, null, report.methodology().limitations());
        out.append("<p class=\"small\">Judgement schema: ")
                .append(escape(report.methodology().judgmentSchemaVersion()))
                .append("<br />Decision policy: ").append(escape(report.methodology().decisionPolicyVersion()))
                .append("<br />Report composition: ").append(escape(report.methodology().reportVersion()))
                .append("</p>");

        out.append("</body></html>");
        return out.toString();
    }

    private void heading(StringBuilder out, String title) {
        out.append("<h2>").append(escape(title)).append("</h2>");
    }

    private void section(StringBuilder out, String title, String body) {
        if (body == null || body.isBlank()) return;
        out.append("<h3>").append(escape(title)).append("</h3><p>").append(escape(body)).append("</p>");
    }

    private void listBlock(StringBuilder out, String title, List<String> values) {
        if (values.isEmpty()) return;
        if (title != null) out.append("<h3>").append(escape(title)).append("</h3>");
        out.append("<ul>");
        values.forEach(value -> out.append("<li>").append(escape(value)).append("</li>"));
        out.append("</ul>");
    }

    private String measureName(String name) {
        return switch (name) {
            case "FUTURE_OPTIONALITY" -> "Future Optionality";
            case "DEGREE_PATHWAY_BREADTH" -> "Degree Pathway Breadth";
            case "ANALYTICAL_FOUNDATION" -> "Analytical Foundation";
            case "HUMAN_AND_CREATIVE_STRENGTH" -> "Human and Creative Strength";
            case "SKILL_TRANSFERABILITY" -> "Skill Transferability";
            case "AI_ERA_ADAPTABILITY" -> "AI-Era Adaptability";
            default -> label(name);
        };
    }

    private String label(String name) {
        String text = name.toLowerCase(Locale.ROOT).replace('_', ' ');
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
