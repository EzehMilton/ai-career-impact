package com.chikere.futureai.pdf.university.openhtml;

import com.chikere.futureai.pdf.university.application.RenderedUniversityReportPdf;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfGenerationException;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfModel;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfRenderer;
import com.chikere.futureai.report.university.application.UniversityReport;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
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
public final class OpenHtmlUniversityReportPdfRenderer implements UniversityReportPdfRenderer {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.UK)
            .withZone(ZoneId.of("Europe/London"));

    @Override
    public RenderedUniversityReportPdf render(UniversityReportPdfModel model) {
        try (var output = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder();
            builder.withHtmlContent(html(model), null);
            builder.toStream(output);
            builder.run();
            return new RenderedUniversityReportPdf(output.toByteArray());
        }
        catch (Exception failure) {
            throw new UniversityReportPdfGenerationException("PDF renderer failed", failure);
        }
    }

    String html(UniversityReportPdfModel model) {
        UniversityReport report = model.report();
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
                .column { width:48%; display:inline-block; vertical-align:top; margin-right:1.5%; }
                .small { color:#64748b; font-size:8.5pt; } a { color:#075985; text-decoration:underline; }
                </style><title>Purchased university course report</title></head><body>
                """);

        out.append("<section class=\"cover\"><div class=\"eyebrow\">Purchased course report</div><h1>")
                .append(escape(report.headline())).append("</h1><p class=\"subtitle\">")
                .append(escape(report.courseTitle()));
        report.institution().ifPresent(value -> out.append(" &#160;&#183;&#160; ").append(escape(value)));
        out.append("</p><div class=\"notice\"><strong>About this report</strong><p>")
                .append(escape(report.assessmentSummary().methodologyCaveat())).append("</p></div>");
        if (report.developmentFixture()) {
            out.append("<div class=\"warning\"><strong>Development fixture report</strong><p>This content uses "
                    + "local fixture assessment, narrative, evidence and payment data. No live provider was "
                    + "contacted.</p></div>");
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
                .append(escape(report.assessmentSummary().employmentDisclaimer())).append("</div>");

        heading(out, "Your five measures");
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

        heading(out, "Strengths and development areas");
        listBlock(out, "Key strengths", report.keyStrengths());
        listBlock(out, "Areas worth supplementing", report.developmentAreas());

        heading(out, "Full interpretation");
        var narrative = report.narrative();
        if (narrative.available()) {
            section(out, "What this means", narrative.whatThisMeans().orElse(null));
            section(out, "Future Readiness", narrative.futureReadinessExplanation().orElse(null));
            section(out, "What the course develops well", narrative.strengthsExplanation().orElse(null));
            section(out, "What you may want to strengthen",
                    narrative.developmentOpportunitiesExplanation().orElse(null));
            section(out, "Working alongside AI", narrative.complementarityExplanation().orElse(null));
            section(out, "Transferable capabilities", narrative.transferableSkillsExplanation().orElse(null));
            section(out, "Career adaptability", narrative.careerAdaptabilityExplanation().orElse(null));
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

        if (!report.skillPriorities().isEmpty()) {
            heading(out, "Skills worth developing alongside the course");
            for (var skill : report.skillPriorities()) {
                out.append("<div class=\"card\"><p class=\"small\">").append(label(skill.priority().name()))
                        .append("</p><h3>").append(escape(skill.title())).append("</h3><p>")
                        .append(escape(skill.rationale())).append("</p></div>");
            }
        }

        heading(out, "Career areas you mentioned");
        out.append("<p>").append(escape(report.careerAreas().guidance())).append("</p>");
        listBlock(out, null, report.careerAreas().areasToExplore());
        out.append("<p class=\"small\">").append(escape(report.careerAreas().limitation())).append("</p>");

        heading(out, "External context");
        out.append("<p class=\"small\">This is saved factual context, separate from the measures above.</p>");
        if (report.evidence().items().isEmpty()) {
            out.append("<p>").append(escape(report.evidence().message())).append("</p>");
        }
        for (var item : report.evidence().items()) {
            out.append("<div class=\"card\"><p class=\"small\">").append(label(item.category()))
                    .append("</p><h3>").append(escape(item.title())).append("</h3><p>")
                    .append(escape(item.summary())).append("</p>");
            item.metricValue().ifPresent(value -> out.append("<p><span class=\"pill\">")
                    .append(escape(item.metricName().orElse(""))).append(":</span> ")
                    .append(escape(plain(value))).append(" ")
                    .append(escape(item.metricUnit().orElse(""))).append("</p>"));
            out.append("<p class=\"small\">Source: ").append(escape(item.sourceName()));
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

    private String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    private String measureName(String name) {
        return switch (name) {
            case "FUTURE_READINESS" -> "Future Readiness";
            case "AI_COMPLEMENTARITY" -> "AI Complementarity";
            case "HUMAN_DIFFERENTIATION" -> "Human Differentiation";
            case "SKILL_TRANSFERABILITY" -> "Skill Transferability";
            case "CAREER_ADAPTABILITY" -> "Career Adaptability";
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
