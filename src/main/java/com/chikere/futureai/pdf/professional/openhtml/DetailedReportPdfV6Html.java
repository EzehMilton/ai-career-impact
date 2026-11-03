package com.chikere.futureai.pdf.professional.openhtml;

import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReport;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportView;

import java.util.ArrayList;
import java.util.List;

/**
 * professional-report-pdf-v6: the professional-report-v4 content from the same view model as the web page.
 * Customer-facing content only. Pagination: no section forces a page break except after the cover, so a short block
 * can never be stranded on an otherwise empty page; a section heading always travels with its first block; each
 * opportunity flows as small unbreakable parts (heading and explanation, uses and ownership, experiment, conditions)
 * instead of one page-sized card; responsibility cards sit two to a row; the short safeguard and strategy sections are
 * one unbreakable block each; the closing paragraph stays with Next steps; paragraphs keep at least two lines
 * together. Body text stays at 10pt.
 */
final class DetailedReportPdfV6Html {

    private static final String STYLE = """
            @page { size:A4; margin:18mm 16mm 20mm; @bottom-center { content:"AI Career Impact · " counter(page) " / " counter(pages); color:#64748b; font-size:8pt; } }
            * { box-sizing:border-box; } body { background:#fff; font-family:Arial, Helvetica, sans-serif; color:#172033; font-size:10pt; line-height:1.5; margin:0; }
            h1 { font-size:26pt; line-height:1.15; color:#102a43; margin:6mm 0 4mm; } h2 { color:#123b5d; font-size:16pt; border-bottom:1px solid #cbd5e1; padding-bottom:2mm; margin:0 0 4mm; }
            h3 { color:#174b70; font-size:11.5pt; margin:0 0 2mm; } h4 { font-size:10pt; margin:3mm 0 1mm; color:#334155; }
            p { margin:0 0 3mm; } ul, ol { margin:1mm 0 3mm; padding-left:6mm; } li { margin-bottom:1.2mm; }
            .cover { page-break-after:always; padding-top:30mm; } .eyebrow { color:#0f766e; font-weight:bold; text-transform:uppercase; letter-spacing:.8pt; font-size:9pt; }
            .subtitle { font-size:13pt; color:#475569; } .meta { margin-top:24mm; color:#64748b; font-size:9pt; }
            .section { margin-top:8mm; }
            .keep { page-break-inside:avoid; } .intro { color:#475569; }
            .card { border:1px solid #d8e0e8; border-radius:4px; padding:3.5mm 4mm; margin:0 0 3.5mm; page-break-inside:avoid; }
            .measure { width:48%; display:inline-block; vertical-align:top; margin-right:1.5%; min-height:34mm; }
            .half { width:48.5%; display:inline-block; vertical-align:top; margin-right:1%; }
                        .pair { width:48.5%; display:inline-block; vertical-align:top; margin-right:1%; }
            .pair ul { margin-bottom:1mm; }
            .legend { margin:0 0 3mm; } .legend p { margin:0 0 1.5mm; font-size:9pt; color:#334155; }
            .opportunity-head { border-top:2px solid #0f766e; padding-top:3mm; margin-top:5mm; page-break-inside:avoid; }
            .part { page-break-inside:avoid; }
            .phase-start { margin-top:3mm; } .phase ol { margin:0 0 0; } .phase-end { margin-bottom:5mm; }
            .score { font-size:18pt; font-weight:bold; color:#0f766e; margin:1mm 0; } .band { font-weight:bold; color:#334155; }
            .column { width:48%; display:inline-block; vertical-align:top; margin-right:1.5%; }
            .label { display:block; font-size:8.5pt; font-weight:bold; color:#0f766e; text-transform:uppercase; letter-spacing:.4pt; margin-bottom:1mm; }
            .signals { color:#475569; font-size:9pt; } .oversight { color:#9a3412; font-size:9pt; font-weight:bold; }
            .note { background:#f1f5f9; border-left:3px solid #64748b; padding:2.5mm 3.5mm; margin:2mm 0 0; }
            .explanation { color:#1f2937; } .conditions { font-size:9pt; color:#334155; }
            .how { font-size:9pt; color:#475569; }
            .experiment { background:#f0fdfa; border-left:3px solid #0f766e; padding:2.5mm 3.5mm; margin:2mm 0 2mm; page-break-inside:avoid; }
            .experiment h4 { margin-top:0; }
            .applies { font-size:9pt; color:#475569; margin:2mm 0 0; }
            .strengthen { border-top:1px solid #e2e8f0; padding-top:2mm; margin-top:2mm; }
            .safeguard { padding:2mm 0; border-bottom:1px solid #e2e8f0; } .safeguard:last-child { border-bottom:0; }
            .safeguard h3 { margin:0 0 1mm; } .safeguard p { margin:0; }
            .essential { display:inline-block; color:#9a3412; font-weight:bold; font-size:8.5pt; text-transform:uppercase; margin-left:2mm; }
            .strategy { width:48%; display:inline-block; vertical-align:top; margin-right:1.5%; }
            .strategy h3 { color:#0f766e; } .strategy ul { margin-bottom:0; }
            .phase { border-left:3px solid #0f766e; padding-left:4mm; }
            .small { color:#64748b; font-size:8.5pt; }
            """;

    private DetailedReportPdfV6Html() {
    }

    static String html(ProfessionalDetailedReportView view) {
        ProfessionalDetailedReport report = view.report();
        StringBuilder out = new StringBuilder(64_000);
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

        var summary = new Section("Executive summary", null);
        report.executiveSummary().forEach(paragraph -> summary.add("<p>" + escape(paragraph) + "</p>"));
        summary.write(out);

        var measures = new Section("Your four measures", "Each measure describes a different characteristic of the work "
                + "you described. A higher score is not automatically better or worse.");
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

        var drivers = new Section("What drives your AI Exposure", null);
        drivers.add(factors("What increases AI Exposure", report.factors().raising(), report.factors().raisingEmpty())
                + factors("What reduces AI Exposure", report.factors().lowering(), report.factors().loweringEmpty()));
        drivers.write(out);

        var responsibilities = report.responsibilities();
        var cards = new Section("Your work, responsibility by responsibility", responsibilities.introduction());
        if (responsibilities.narrative() != null) {
            cards.add("<p>" + escape(responsibilities.narrative()) + "</p>");
        }
        if (responsibilities.oversightNote() != null) {
            cards.add("<p class=\"note\">" + escape(responsibilities.oversightNote()) + "</p>");
        }
        // Each classification sentence once, then compact cards that carry only their label and signals.
        StringBuilder legend = new StringBuilder("<div class=\"legend\">");
        view.categoryNotes().forEach(note -> legend.append("<p><strong>").append(escape(note.label()))
                .append(":</strong> ").append(escape(note.explanation())).append("</p>"));
        cards.add(legend.append("</div>").toString());
        for (var card : responsibilities.cards()) {
            StringBuilder block = new StringBuilder("<div class=\"card half\"><div class=\"label\">")
                    .append(escape(card.categoryLabel())).append("</div><h3>").append(escape(card.text())).append("</h3>");
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
        var best = new Section("Best opportunities for AI assistance", opportunities.introduction());
        for (var item : opportunities.items()) {
            // Small unbreakable parts rather than one page-sized card: the section heading travels only with the first
            // opportunity's heading and explanation, and an opportunity may continue on the next page between parts.
            best.add("<div class=\"opportunity-head\"><div class=\"label\">" + item.rank() + " · "
                    + escape(item.categoryLabel()) + "</div><h3>" + escape(item.responsibility())
                    + "</h3><p class=\"explanation\">"
                    + escape(item.explanation() != null ? item.explanation() : item.whyCandidate())
                    + "</p><p class=\"how\"><strong>How AI may assist:</strong> " + escape(item.howAiMayAssist())
                    + "</p></div>");
            if (!item.practicalAiUses().isEmpty() || !item.humanOwnership().isEmpty()) {
                StringBuilder pairs = new StringBuilder("<div class=\"part\">");
                if (!item.practicalAiUses().isEmpty()) {
                    pairs.append("<div class=\"pair\"><h4>What this could look like</h4>").append(list(item.practicalAiUses()))
                            .append("</div>");
                }
                if (!item.humanOwnership().isEmpty()) {
                    pairs.append("<div class=\"pair\"><h4>What stays yours</h4>").append(list(item.humanOwnership()))
                            .append("</div>");
                }
                best.add(pairs.append("</div>").toString());
            }
            if (item.firstExperiment() != null) {
                best.add("<div class=\"experiment\"><h4>A sensible first experiment</h4><p>"
                        + escape(item.firstExperiment()) + "</p></div>");
            }
            if (!item.conditions().isEmpty() || !item.appliedSafeguards().isEmpty()) {
                StringBuilder conditions = new StringBuilder("<div class=\"part\">");
                if (!item.conditions().isEmpty()) {
                    conditions.append("<h4>Conditions for this responsibility</h4><ul class=\"conditions\">");
                    item.conditions().forEach(condition -> conditions.append("<li>").append(escape(condition)).append("</li>"));
                    conditions.append("</ul>");
                }
                if (!item.appliedSafeguards().isEmpty()) {
                    conditions.append("<p class=\"applies\">Also apply, from Responsible AI use: ")
                            .append(escape(String.join("; ", item.appliedSafeguards()))).append(".</p>");
                }
                best.add(conditions.append("</div>").toString());
            }
        }
        best.write(out);

        var humanValue = report.humanValue();
        var human = new Section("Where your human contribution matters most", humanValue.introduction());
        for (var item : humanValue.items()) {
            // The personalised "why you matter here" takes the place of the generic reason sentence when present.
            human.add("<div class=\"card\"><div class=\"label\">" + escape(item.reason()) + "</div><h3>"
                    + escape(item.responsibility()) + "</h3>"
                    + (item.explanation() == null ? "<p>" + escape(item.reasonExplanation()) + "</p>"
                            : "<h4>Why you matter here</h4><p class=\"explanation\">" + escape(item.explanation()) + "</p>")
                    + (item.strengthen() == null ? "" : "<div class=\"strengthen\"><h4>What to deliberately strengthen</h4><p>"
                            + escape(item.strengthen()) + "</p></div>")
                    + "</div>");
        }
        human.write(out);

        var skills = report.skills();
        var skillSection = new Section("Skills to strengthen", skills.introduction());
        for (var skill : skills.items()) {
            // The personalised explanation takes the place of the generic reason sentence when present.
            skillSection.add("<div class=\"card\"><h3>" + skill.rank() + ". " + escape(skill.label()) + "</h3><p>"
                    + escape(skill.explanation() == null ? skill.reason() : skill.explanation()) + "</p>"
                    + (skill.inYourWork().isEmpty() ? "" : "<h4>In your work, this means…</h4>" + list(skill.inYourWork()))
                    + (skill.supportingResponsibilities().isEmpty() ? "" : "<p class=\"signals\">Linked to: "
                            + escape(linked(skill.supportingResponsibilities(), responsibilities.cards().size())) + "</p>")
                    + "</div>");
        }
        skillSection.write(out);

        // At most four short safeguards: one bordered block, never split, so none is left alone on a page.
        var safeguards = report.safeguards();
        var responsible = new Section("Responsible AI use", safeguards.introduction());
        StringBuilder rows = new StringBuilder("<div class=\"card\">");
        for (var safeguard : safeguards.items()) {
            rows.append("<div class=\"safeguard\"><h3>").append(escape(safeguard.title()))
                    .append(safeguard.essential() ? "<span class=\"essential\">Essential</span>" : "")
                    .append("</h3><p>").append(escape(safeguard.guidance())).append("</p></div>");
        }
        responsible.add(rows.append("</div>").toString());
        responsible.write(out);

        var plan = report.plan();
        var planSection = new Section("Your 90-day plan", plan.introduction());
        if (plan.narrative() != null) {
            planSection.add("<p>" + escape(plan.narrative()) + "</p>");
        }
        for (var phase : plan.phases()) {
            // The phase heading travels with its first action; later actions follow one by one, sharing the rule.
            var actions = phase.actions();
            for (int index = 0; index < actions.size(); index++) {
                var action = actions.get(index);
                StringBuilder block = new StringBuilder("<div class=\"phase keep")
                        .append(index == 0 ? " phase-start" : "").append(index == actions.size() - 1 ? " phase-end" : "")
                        .append("\">");
                if (index == 0) {
                    block.append("<h3>").append(escape(phase.heading())).append(": ").append(escape(phase.purpose()))
                            .append("</h3>");
                }
                block.append("<ol start=\"").append(index + 1).append("\"><li><strong>").append(escape(action.title()))
                        .append("</strong><br />").append(escape(action.rationale()));
                if (!action.reviewNote().isBlank()) {
                    block.append("<br /><span class=\"small\">").append(escape(action.reviewNote())).append("</span>");
                }
                if (action.note() != null) {
                    block.append("<br /><span class=\"explanation\">").append(escape(action.note())).append("</span>");
                }
                planSection.add(block.append("</li></ol></div>").toString());
            }
        }
        planSection.write(out);

        if (report.adaptationStrategy() != null) {
            var strategy = report.adaptationStrategy();
            var strategySection = new Section("Your AI adaptation strategy", "Five short headings drawn from your "
                    + "findings. They describe what to do and watch, not what will happen.");
            StringBuilder columns = new StringBuilder("<div class=\"keep\">");
            column(columns, "Protect", strategy.protect());
            column(columns, "Learn", strategy.learn());
            column(columns, "Experiment", strategy.experiment());
            column(columns, "Strengthen", strategy.strengthen());
            column(columns, "Monitor", strategy.monitor());
            strategySection.add(columns.append("</div>").toString());
            strategySection.write(out);
        }

        var limits = new Section("What this assessment does and does not mean", null);
        limits.add(list(report.limitations()));
        limits.write(out);

        // One block: the closing paragraph is never left alone on a final page.
        var next = new Section("Next steps", null);
        next.add(list(report.nextSteps()) + (report.closingPerspective() == null ? ""
                : "<p>" + escape(report.closingPerspective()) + "</p>"));
        next.write(out);
        out.append("</body></html>");
        return out.toString();
    }

    /** A skill linked to every analysed responsibility says so, rather than listing them all again. */
    static String linked(List<String> responsibilities, int analysed) {
        return responsibilities.size() == analysed && analysed > 2 ? "every responsibility you listed"
                : String.join("; ", responsibilities);
    }

    private static void column(StringBuilder out, String heading, List<String> items) {
        if (!items.isEmpty()) {
            out.append("<div class=\"card strategy\"><h3>").append(escape(heading)).append("</h3>").append(list(items))
                    .append("</div>");
        }
    }

    /**
     * One report section. Its heading and introduction are written with its first block inside one unbreakable
     * group, so a heading is never left alone at the foot of a page; later blocks follow without a forced break.
     */
    private static final class Section {
        private final String title;
        private final String introduction;
        private final List<String> blocks = new ArrayList<>();

        Section(String title, String introduction) {
            this.title = title;
            this.introduction = introduction;
        }

        void add(String block) {
            blocks.add(block);
        }

        void write(StringBuilder out) {
            out.append("<section class=\"section\"><div class=\"keep\"><h2>").append(escape(title)).append("</h2>");
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
