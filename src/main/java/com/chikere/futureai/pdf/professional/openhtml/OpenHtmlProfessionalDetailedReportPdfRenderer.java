package com.chikere.futureai.pdf.professional.openhtml;

import com.chikere.futureai.pdf.professional.application.ProfessionalReportPdfGenerationException;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReport;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportPdfRenderer;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportVersions;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportView;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * The detailed report PDF, from the same view model as the web page, in the PDF version the stored report records:
 * professional-report-pdf-v5 for a stored professional-report-v3 (unchanged, below) and professional-report-pdf-v6
 * for professional-report-v4 ({@link DetailedReportPdfV6Html}). Customer-facing content only: no rule identifiers,
 * raw judgments or internal confidence.
 *
 * <p>v5: each section heading is kept with its first block, and cards are never split across pages.</p>
 */
@Component
public final class OpenHtmlProfessionalDetailedReportPdfRenderer implements ProfessionalDetailedReportPdfRenderer {

    private static final String STYLE = """
            @page { size:A4; margin:18mm 16mm 20mm; @bottom-center { content:"AI Career Impact · " counter(page) " / " counter(pages); color:#64748b; font-size:8pt; } }
            * { box-sizing:border-box; } body { background:#fff; font-family:Arial, Helvetica, sans-serif; color:#172033; font-size:10pt; line-height:1.5; margin:0; }
            h1 { font-size:26pt; line-height:1.15; color:#102a43; margin:6mm 0 4mm; } h2 { color:#123b5d; font-size:16pt; border-bottom:1px solid #cbd5e1; padding-bottom:2mm; margin:0 0 4mm; page-break-after:avoid; }
            h3 { color:#174b70; font-size:11.5pt; margin:0 0 2mm; page-break-after:avoid; } h4 { font-size:10pt; margin:3mm 0 1mm; color:#334155; }
            p { margin:0 0 3mm; } ul, ol { margin:1mm 0 3mm; padding-left:6mm; } li { margin-bottom:1.2mm; }
            .cover { page-break-after:always; padding-top:30mm; } .eyebrow { color:#0f766e; font-weight:bold; text-transform:uppercase; letter-spacing:.8pt; font-size:9pt; }
            .subtitle { font-size:13pt; color:#475569; } .meta { margin-top:24mm; color:#64748b; font-size:9pt; }
            .section { margin-top:8mm; } .new-page { page-break-before:always; }
            .keep { page-break-inside:avoid; } .intro { color:#475569; }
            .card { border:1px solid #d8e0e8; border-radius:4px; padding:3.5mm 4mm; margin:0 0 3.5mm; page-break-inside:avoid; }
            .measure { width:48%; display:inline-block; vertical-align:top; margin-right:1.5%; min-height:38mm; }
            .score { font-size:18pt; font-weight:bold; color:#0f766e; margin:1mm 0; } .band { font-weight:bold; color:#334155; }
            .column { width:48%; display:inline-block; vertical-align:top; margin-right:1.5%; }
            .label { display:block; font-size:8.5pt; font-weight:bold; color:#0f766e; text-transform:uppercase; letter-spacing:.4pt; margin-bottom:1mm; }
            .signals { color:#475569; font-size:9pt; } .oversight { color:#9a3412; font-size:9pt; font-weight:bold; }
            .note { background:#f1f5f9; border-left:3px solid #64748b; padding:2.5mm 3.5mm; margin:2mm 0 0; }
            .explanation { color:#1f2937; } .conditions { font-size:9pt; color:#334155; }
            .essential { display:block; color:#9a3412; font-weight:bold; font-size:8.5pt; text-transform:uppercase; }
            .phase { border-left:3px solid #0f766e; padding-left:4mm; margin-bottom:5mm; }
            .small { color:#64748b; font-size:8.5pt; }
            """;

    @Override
    public byte[] render(ProfessionalDetailedReportView view) {
        try (var output = new ByteArrayOutputStream()) {
            var builder = new PdfRendererBuilder();
            builder.withHtmlContent(html(view), null);
            builder.toStream(output);
            builder.run();
            return output.toByteArray();
        }
        catch (Exception failure) {
            throw new ProfessionalReportPdfGenerationException("detailed PDF renderer failed", failure);
        }
    }

    public String html(ProfessionalDetailedReportView view) {
        return ProfessionalDetailedReportVersions.PDF_V5.equals(view.pdfVersion()) ? htmlV5(view)
                : DetailedReportPdfV6Html.html(view);
    }

    /** professional-report-pdf-v5, kept exactly as it rendered stored professional-report-v3 reports. */
    private String htmlV5(ProfessionalDetailedReportView view) {
        ProfessionalDetailedReport report = view.report();
        StringBuilder out = new StringBuilder(48_000);
        out.append("<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"en-GB\"><head><meta charset=\"UTF-8\" /><style>")
                .append(STYLE).append("</style><title>").append(escape(report.cover().title())).append("</title></head><body>");

        out.append("<section class=\"cover\"><div class=\"eyebrow\">Your detailed report</div><h1>")
                .append(escape(report.cover().title())).append("</h1><p class=\"subtitle\">")
                .append(escape(report.cover().profession())).append("</p><p>").append(escape(report.cover().subtitle()))
                .append("</p>");
        if (report.developmentFixture()) {
            out.append("<p class=\"note\">Development report: local fixture data, no live provider was used.</p>");
        }
        out.append("<div class=\"meta\"><p>Assessment date: ").append(escape(view.assessedOn()))
                .append("<br />Report version: ").append(escape(view.reportVersion()))
                .append(" · PDF version: ").append(escape(view.pdfVersion())).append("</p></div></section>");

        var summary = new Section("Executive summary", null, false);
        report.executiveSummary().forEach(paragraph -> summary.add("<p>" + escape(paragraph) + "</p>"));
        summary.write(out);

        var measures = new Section("Your four measures", "Each measure describes a different characteristic of the work "
                + "you described. A higher score is not automatically better or worse.", false);
        StringBuilder grid = new StringBuilder();
        for (var measure : view.measures()) {
            grid.append("<div class=\"card measure\"><h3>").append(escape(measure.name())).append("</h3><div class=\"score\">")
                    .append(escape(measure.score())).append(" / 10</div><p class=\"band\">")
                    .append(escape(measure.band())).append("</p><p>").append(escape(measure.explanation())).append("</p></div>");
        }
        measures.add(grid.toString());
        if (report.scoresTogether() != null) {
            measures.add("<div class=\"card\"><h3>What the measures mean together</h3><p>"
                    + escape(report.scoresTogether()) + "</p></div>");
        }
        measures.write(out);

        var drivers = new Section("What drives your AI Exposure", null, false);
        drivers.add(factors("What increases AI Exposure", report.factors().raising(), report.factors().raisingEmpty())
                + factors("What reduces AI Exposure", report.factors().lowering(), report.factors().loweringEmpty()));
        drivers.write(out);

        var responsibilities = report.responsibilities();
        var cards = new Section("Your work, responsibility by responsibility", responsibilities.introduction(), false);
        if (responsibilities.narrative() != null) {
            cards.add("<p>" + escape(responsibilities.narrative()) + "</p>");
        }
        if (responsibilities.oversightNote() != null) {
            cards.add("<p class=\"note\">" + escape(responsibilities.oversightNote()) + "</p>");
        }
        for (var card : responsibilities.cards()) {
            StringBuilder block = new StringBuilder("<div class=\"card\"><div class=\"label\">")
                    .append(escape(card.categoryLabel())).append("</div><h3>").append(escape(card.text())).append("</h3><p>")
                    .append(escape(card.explanation())).append("</p>");
            if (!card.signals().isEmpty()) {
                block.append("<p class=\"signals\">").append(escape(String.join(" · ", card.signals()))).append("</p>");
            }
            if (card.showOversight()) {
                block.append("<p class=\"oversight\">Needs close human oversight</p>");
            }
            cards.add(block.append("</div>").toString());
        }
        cards.write(out);

        var opportunities = report.opportunities();
        var best = new Section("Best opportunities for AI assistance", opportunities.introduction(), false);
        for (var item : opportunities.items()) {
            StringBuilder block = new StringBuilder("<div class=\"card\"><div class=\"label\">").append(item.rank())
                    .append(" · ").append(escape(item.categoryLabel())).append("</div><h3>")
                    .append(escape(item.responsibility())).append("</h3><p>").append(escape(item.whyCandidate()))
                    .append("</p><h4>How AI may assist</h4><p>").append(escape(item.howAiMayAssist())).append("</p>");
            if (item.explanation() != null) {
                block.append("<p class=\"explanation\">").append(escape(item.explanation())).append("</p>");
            }
            block.append("<h4>Conditions</h4><ul class=\"conditions\">");
            item.conditions().forEach(condition -> block.append("<li>").append(escape(condition)).append("</li>"));
            best.add(block.append("</ul></div>").toString());
        }
        best.write(out);

        var humanValue = report.humanValue();
        var human = new Section("Where your human contribution matters most", humanValue.introduction(), false);
        for (var item : humanValue.items()) {
            human.add("<div class=\"card\"><div class=\"label\">" + escape(item.reason()) + "</div><h3>"
                    + escape(item.responsibility()) + "</h3><p>" + escape(item.reasonExplanation()) + "</p>"
                    + (item.explanation() == null ? "" : "<p class=\"explanation\">" + escape(item.explanation()) + "</p>")
                    + "</div>");
        }
        human.write(out);

        var skills = report.skills();
        var skillSection = new Section("Skills to strengthen", skills.introduction(), false);
        if (skills.narrative() != null) {
            skillSection.add("<p>" + escape(skills.narrative()) + "</p>");
        }
        for (var skill : skills.items()) {
            skillSection.add("<div class=\"card\"><h3>" + skill.rank() + ". " + escape(skill.label()) + "</h3><p>"
                    + escape(skill.reason()) + "</p>"
                    + (skill.explanation() == null ? "" : "<p class=\"explanation\">" + escape(skill.explanation()) + "</p>")
                    + (skill.supportingResponsibilities().isEmpty() ? "" : "<p class=\"signals\">Linked to: "
                            + escape(String.join("; ", skill.supportingResponsibilities())) + "</p>")
                    + "</div>");
        }
        skillSection.write(out);

        var safeguards = report.safeguards();
        var responsible = new Section("Responsible AI use", safeguards.introduction(), false);
        for (var safeguard : safeguards.items()) {
            responsible.add("<div class=\"card\">" + (safeguard.essential() ? "<div class=\"essential\">Essential</div>" : "")
                    + "<h3>" + escape(safeguard.title()) + "</h3><p>" + escape(safeguard.guidance()) + "</p></div>");
        }
        responsible.write(out);

        var plan = report.plan();
        var planSection = new Section("Your 90-day plan", plan.introduction(), true);
        if (plan.narrative() != null) {
            planSection.add("<p>" + escape(plan.narrative()) + "</p>");
        }
        for (var phase : plan.phases()) {
            StringBuilder block = new StringBuilder("<div class=\"phase\"><h3>").append(escape(phase.heading()))
                    .append(": ").append(escape(phase.purpose())).append("</h3><ol>");
            for (var action : phase.actions()) {
                block.append("<li class=\"keep\"><strong>").append(escape(action.title())).append("</strong><br />")
                        .append(escape(action.rationale()));
                if (!action.reviewNote().isBlank()) {
                    block.append("<br /><span class=\"small\">").append(escape(action.reviewNote())).append("</span>");
                }
                if (action.note() != null) {
                    block.append("<br /><span class=\"explanation\">").append(escape(action.note())).append("</span>");
                }
                block.append("</li>");
            }
            planSection.add(block.append("</ol></div>").toString());
        }
        planSection.write(out);

        var limits = new Section("What this assessment does and does not mean", null, false);
        limits.add(list(report.limitations()));
        limits.write(out);

        var next = new Section("Next steps", null, false);
        next.add(list(report.nextSteps()));
        if (report.closingPerspective() != null) {
            next.add("<p>" + escape(report.closingPerspective()) + "</p>");
        }
        next.write(out);
        out.append("</body></html>");
        return out.toString();
    }

    /**
     * One report section. Its heading and introduction are written together with its first block inside one
     * unbreakable group, so a heading is never left alone at the foot of a page; later blocks break between cards.
     */
    private static final class Section {
        private final String title;
        private final String introduction;
        private final boolean newPage;
        private final List<String> blocks = new java.util.ArrayList<>();

        Section(String title, String introduction, boolean newPage) {
            this.title = title;
            this.introduction = introduction;
            this.newPage = newPage;
        }

        void add(String block) {
            blocks.add(block);
        }

        void write(StringBuilder out) {
            out.append("<section class=\"section").append(newPage ? " new-page" : "").append("\"><div class=\"keep\"><h2>")
                    .append(escape(title)).append("</h2>");
            if (introduction != null) {
                out.append("<p class=\"intro\">").append(escape(introduction)).append("</p>");
            }
            if (!blocks.isEmpty()) {
                out.append(blocks.getFirst());
            }
            out.append("</div>");
            blocks.stream().skip(1).forEach(out::append);
            out.append("</section>");
        }
    }

    private static String factors(String title, List<String> factors, String whenEmpty) {
        return "<div class=\"card column\"><h3>" + escape(title) + "</h3>"
                + (factors.isEmpty() ? "<p>" + escape(whenEmpty) + "</p>" : list(factors)) + "</div>";
    }

    private static String list(List<String> values) {
        StringBuilder out = new StringBuilder("<ul>");
        values.forEach(value -> out.append("<li>").append(escape(value)).append("</li>"));
        return out.append("</ul>").toString();
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
