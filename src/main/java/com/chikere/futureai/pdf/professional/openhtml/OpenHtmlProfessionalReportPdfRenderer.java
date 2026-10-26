package com.chikere.futureai.pdf.professional.openhtml;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.pdf.professional.application.*;
import com.chikere.futureai.report.professional.application.ProfessionalReport;
import com.chikere.futureai.report.professional.application.WorkFocusCategory;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public final class OpenHtmlProfessionalReportPdfRenderer implements ProfessionalReportPdfRenderer {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.UK)
            .withZone(ZoneId.of("Europe/London"));

    @Override
    public RenderedProfessionalReportPdf render(ProfessionalReportPdfModel model) {
        try (var output = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder();
            builder.withHtmlContent(html(model), null);
            builder.toStream(output);
            builder.run();
            return new RenderedProfessionalReportPdf(output.toByteArray());
        }
        catch (Exception failure) {
            throw new ProfessionalReportPdfGenerationException("PDF renderer failed", failure);
        }
    }

    String html(ProfessionalReportPdfModel model) {
        ProfessionalReport report = model.report();
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
                .columns { width:100%; } .column { width:48%; display:inline-block; vertical-align:top; margin-right:1.5%; }
                .small { color:#64748b; font-size:8.5pt; } .source { color:#334155; font-size:9pt; } a { color:#075985; text-decoration:underline; }
                .page-break { page-break-before:always; } .section { page-break-inside:auto; }
                </style><title>Purchased professional report</title></head><body>
                """);
        out.append("<section class=\"cover\"><div class=\"eyebrow\">Purchased professional report</div><h1>")
                .append(escape(report.headline())).append("</h1><p class=\"subtitle\">")
                .append(escape(report.profession())).append("</p><div class=\"notice\"><strong>About this report</strong><p>")
                .append(escape(report.assessmentSummary().methodologyCaveat())).append("</p></div>");
        if (report.developmentFixture()) out.append("<div class=\"warning\"><strong>Development fixture report</strong><p>This content uses local fixture assessment, narrative, evidence and payment data. No live provider was contacted.</p></div>");
        out.append("<div class=\"meta\"><p>Purchased report date: ").append(DATE.format(model.purchasedAt()))
                .append("</p><p>Content version: ").append(escape(model.purchasedReportVersion()))
                .append("<br />PDF presentation: ").append(escape(model.pdfTemplateVersion())).append("</p></div></section>");

        heading(out, "Executive summary");
        out.append("<div class=\"card\"><h3>").append(escape(report.headline())).append("</h3><p>")
                .append(escape(report.assessmentSummary().interpretation())).append("</p><p><span class=\"pill\">Status:</span> ")
                .append(customerStatus(report.assessmentSummary().status())).append("</p></div><div class=\"warning\">")
                .append(escape(report.assessmentSummary().jobLossDisclaimer())).append("</div>");

        heading(out, "Your four measures");
        for (var measure : report.measures()) {
            out.append("<article class=\"card measure\"><h3>").append(measureName(measure.measure().name()))
                    .append("</h3><div class=\"score\">Score: ").append(String.format(Locale.ROOT, "%.2f", measure.indicator()))
                    .append(" / 10</div><p><span class=\"pill\">Band:</span> ").append(label(measure.band().name()))
                    .append("</p><p>")
                    .append(escape(measure.conciseExplanation())).append("</p>");
            measure.detailedExplanation().ifPresent(value -> out.append("<p>").append(escape(value)).append("</p>"));
            out.append("</article>");
        }

        heading(out, "Why you received this result");
        listBlock(out, "What increases AI Exposure", report.exposureDrivers(), "column");
        listBlock(out, "What reduces AI Exposure", report.protectiveFactors(), "column");

        // Each measure's detailed explanation is already in its card above; only the overall meaning is new here.
        heading(out, "What your results mean for your work");
        var narrative = report.narrative();
        if (narrative.available()) {
            narrative.whatThisMeans().ifPresent(value -> out.append("<div class=\"card\"><p>").append(escape(value))
                    .append("</p></div>"));
        } else out.append("<div class=\"card\">The purchased report does not contain a personalised narrative.</div>");

        heading(out, "What to focus on next");
        listBlock(out, "Practical actions", report.practicalActions(), "");
        for (var skill : report.skillPriorities().stream()
                .sorted(java.util.Comparator.comparing(ProfessionalReport.SkillDevelopmentPriority::priority)).toList())
            out.append("<article class=\"card\"><h3>")
                .append(escape(skill.title())).append("</h3><p class=\"small\">").append(label(skill.priority().name()))
                .append(" · ").append(label(skill.category().name())).append("</p><p>")
                .append(escape(skill.rationale())).append("</p></article>");

        // The responsibility-review card only repeats the visitor's own list, so it is not presented.
        var guidance = report.workFocus().areas().stream()
                .filter(focus -> focus.category() != WorkFocusCategory.USER_REVIEW_REQUIRED).toList();
        if (!guidance.isEmpty()) {
            heading(out, "Work guidance");
            for (var focus : guidance) {
                out.append("<article class=\"card\"><h3>").append(escape(focus.title())).append("</h3><p class=\"small\">")
                        .append(label(focus.category().name())).append("</p><p>").append(escape(focus.guidance()))
                        .append("</p></article>");
            }
        }
        heading(out, "About this guidance");
        out.append("<p>These suggestions are based on the assessment you completed. They are intended to help you ")
                .append("decide what to explore next, not to predict your career.</p>");

        if (!report.evidence().items().isEmpty()) {
            heading(out, "Career context");
            out.append("<p>Where reliable public information is available, we show it separately from your ")
                    .append("assessment scores.</p>");
            for (var item : report.evidence().items()) evidence(out, item);
        }

        heading(out, "About your assessment");
        out.append("<p>This report provides structured insights into characteristics of the work you described. ")
                .append("It is not a prediction of job loss or a guarantee of future career outcomes.</p>");
        if (report.evidence().items().isEmpty()) {
            out.append("<p class=\"small\">Verified external career information was not available for this assessment.</p>");
        }
        out.append("</body></html>");
        return out.toString();
    }

    /** Both READY statuses read as complete while confidence is redesigned; the stored status is unchanged. */
    private String customerStatus(AssessmentStatus status) {
        return status == AssessmentStatus.MORE_INFORMATION_REQUIRED ? label(status.name()) : "Assessment complete";
    }

    private void evidence(StringBuilder out, ProfessionalReport.EvidenceItem item) {
        out.append("<article class=\"card\"><div class=\"small\">").append(label(item.category())).append("</div><h3>")
                .append(escape(item.title())).append("</h3><p>").append(escape(item.summary())).append("</p>");
        item.metricValue().ifPresent(value -> out.append("<p><strong>").append(escape(item.metricName().orElseThrow()))
                .append(":</strong> ").append(number(value)).append(" ").append(escape(item.metricUnit().orElseThrow())).append("</p>"));
        out.append("<p class=\"source\"><strong>Source:</strong> ");
        if (item.sourceUrl().isPresent()) out.append("<a href=\"").append(escape(item.sourceUrl().orElseThrow().toString())).append("\">")
                .append(escape(item.sourceName())).append("</a>");
        else out.append(escape(item.sourceName()));
        item.publisher().ifPresent(value -> out.append(" · ").append(escape(value)));
        item.geography().ifPresent(value -> out.append("<br /><strong>Geography:</strong> ").append(escape(value)));
        out.append("<br /><strong>Date:</strong> ").append(item.publicationDate().map(Object::toString)
                .orElse("Retrieved " + item.retrievedAt().toString().substring(0, 10))).append("</p><p class=\"small\">")
                .append(escape(item.relevanceNote())).append("</p></article>");
    }

    private void listBlock(StringBuilder out, String title, java.util.List<String> values, String css) {
        out.append("<div class=\"card ").append(css).append("\"><h3>").append(title).append("</h3>"); list(out, values); out.append("</div>");
    }
    private void list(StringBuilder out, java.util.List<String> values) {
        out.append("<ul>"); for (String value : values) out.append("<li>").append(escape(value)).append("</li>"); out.append("</ul>");
    }
    private void heading(StringBuilder out, String title) { out.append("<section class=\"section\"><h2>").append(title).append("</h2></section>"); }
    private String measureName(String name) { return switch (name) { case "AI_EXPOSURE" -> "AI Exposure"; case "HUMAN_ADVANTAGE" -> "Human Advantage"; case "AI_COMPLEMENTARITY" -> "AI Complementarity"; case "ROLE_ADAPTABILITY" -> "Role Adaptability"; default -> label(name); }; }
    private String label(String value) { String text = value.toLowerCase(Locale.ROOT).replace('_', ' '); return escape(text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1)); }
    private String number(BigDecimal value) { return escape(value.stripTrailingZeros().toPlainString()); }
    private String escape(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;"); }
}
