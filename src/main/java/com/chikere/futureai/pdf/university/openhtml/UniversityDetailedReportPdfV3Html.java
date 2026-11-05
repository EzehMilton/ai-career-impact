package com.chikere.futureai.pdf.university.openhtml;

import com.chikere.futureai.report.university.application.UniversityCourseProfile;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code university-report-pdf-v3}: the university-report-v3 layout. Same palette and type as v2, with the practical
 * layer: per module, how AI could help, what you must still do yourself and a study experiment; per capability module,
 * why it matters and what to practise without AI; skills with what they mean in this course; plan actions with their
 * notes; and the AI-study strategy before the limitations. Only the cover forces a page break; each section heading is
 * kept on the page of its first block, so no heading is stranded at a page foot, and each card is kept whole.
 */
final class UniversityDetailedReportPdfV3Html {

    private UniversityDetailedReportPdfV3Html() {
    }

    static String html(UniversityDetailedReport report) {
        var practical = report.practical();
        var perspectives = report.perspectives();
        StringBuilder out = new StringBuilder(60_000);
        out.append("""
                <html xmlns="http://www.w3.org/1999/xhtml" lang="en"><head><meta charset="UTF-8" />
                <style>
                @page { size:A4; margin:18mm 16mm 18mm; @bottom-center { content:"AI Career Impact · " counter(page) " / " counter(pages); color:#64748b; font-size:8pt; } }
                * { box-sizing:border-box; } body { background:#fff; font-family:Arial, Helvetica, sans-serif; color:#172033; font-size:10pt; line-height:1.45; margin:0; }
                h1 { font-size:26pt; line-height:1.12; color:#102a43; margin:8mm 0 4mm; }
                h2 { color:#123b5d; font-size:16pt; border-bottom:1px solid #cbd5e1; padding-bottom:2mm; margin:7mm 0 3mm; page-break-after:avoid; }
                h3 { color:#174b70; font-size:11.5pt; margin:0 0 1.5mm; page-break-after:avoid; }
                h4 { color:#0f766e; font-size:9.5pt; margin:2.5mm 0 1mm; page-break-after:avoid; }
                p { margin:0 0 2.2mm; } ul { margin:1mm 0 2mm; padding-left:5.5mm; } li { margin-bottom:1mm; }
                .cover { page-break-after:always; padding-top:26mm; } .eyebrow { color:#0f766e; font-weight:bold; text-transform:uppercase; letter-spacing:.8pt; }
                .subtitle { font-size:14pt; color:#475569; }
                .notice { background:#ecfeff; border-left:3px solid #0f766e; padding:3mm 4mm; margin:3mm 0; page-break-inside:avoid; }
                .caution { background:#fff7ed; border-left:3px solid #c2410c; padding:3mm 4mm; margin:3mm 0; page-break-inside:avoid; }
                .card { border:1px solid #d8e0e8; border-radius:4px; padding:3.5mm 4mm; margin:0 0 3mm; page-break-inside:avoid; }
                .experiment { background:#f0fdfa; border-left:3px solid #0f766e; padding:2.5mm 3.5mm; margin:2mm 0 0; }
                .score { font-size:20pt; font-weight:bold; color:#0f766e; } .band { font-weight:bold; color:#334155; }
                .label { color:#0f766e; font-weight:bold; margin-bottom:1.5mm; } .small { color:#64748b; font-size:8.5pt; }
                .keep, .strategy, .part { page-break-inside:avoid; }
                .part { border-left:3px solid #d8e0e8; padding:0 0 0 4mm; margin:0; } .part-first { padding-top:1mm; } .part-last { margin-bottom:4mm; }
                .part ul { margin:0 0 1.5mm; } .strategy { border-left:3px solid #0f766e; padding:0.5mm 0 0.5mm 3.5mm; margin:0 0 2.5mm; }
                .note { color:#334155; font-size:9pt; display:block; margin-top:0.8mm; }
                table.two { width:100%; border-collapse:separate; border-spacing:0; } table.two td { width:50%; vertical-align:top; padding:0 1.5mm 0 0; }
                table.two td + td { padding:0 0 0 1.5mm; }
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

        // 2. Course profile
        out.append("<h2>Your course profile</h2>");
        for (UniversityCourseProfile.Measure measure : report.profile().measures()) {
            out.append("<div class=\"card\"><h3>").append(escape(measure.name())).append("</h3><p><span class=\"score\">")
                    .append(escape(measure.score())).append("</span> / 10 &#160; <span class=\"band\">")
                    .append(escape(band(measure.band()))).append("</span></p><p>").append(escape(measure.explanation()))
                    .append("</p>");
            inline(out, "What strengthens this measure", measure.strengthenedBy());
            inline(out, "What holds it back", measure.heldBackBy());
            if (!measure.confidenceNote().isBlank()) {
                out.append("<p class=\"small\">").append(escape(measure.confidenceNote())).append("</p>");
            }
            out.append("</div>");
        }

        // 3. What the measures mean
        out.append("<h2>What the measures mean</h2>");
        paragraphs(out, perspectives.overall());
        paragraphs(out, perspectives.transferable());

        // 4. Modules, two to a row
        out.append("<h2>Your modules</h2>");
        if (perspectives.coursePattern() != null) {
            paragraphs(out, perspectives.coursePattern());
        }
        out.append("<p class=\"small\">").append(escape(report.modules().selectionNote())).append("</p>");
        for (var note : practical.classificationNotes()) {
            out.append("<p><strong>").append(escape(note.title())).append(":</strong> ").append(escape(note.text()))
                    .append("</p>");
        }
        List<UniversityDetailedReport.ModuleCard> cards = report.modules().cards();
        for (int index = 0; index < cards.size(); index += 2) {
            out.append("<table class=\"two\"><tr>");
            for (int column = index; column < index + 2; column++) {
                out.append("<td>");
                if (column < cards.size()) {
                    var module = cards.get(column);
                    out.append("<div class=\"card\"><h3>").append(escape(module.title())).append("</h3><p class=\"label\">")
                            .append(escape(module.classification())).append("</p>");
                    module.notes().forEach(note -> out.append("<p class=\"small\">").append(escape(note)).append("</p>"));
                    out.append("</div>");
                }
                out.append("</td>");
            }
            out.append("</tr></table>");
        }

        // 5. Where AI can help your learning: each module in small parts, so a page fills before the next begins
        List<String> learning = new ArrayList<>();
        for (var module : practical.moduleLearning()) {
            StringBuilder head = new StringBuilder("<div class=\"part part-first\"><h3>").append(escape(module.title()))
                    .append("</h3><p class=\"label\">").append(escape(module.classification())).append("</p><p>")
                    .append(escape(module.explanation() != null ? module.explanation() : module.guidance())).append("</p>");
            list(head, "How AI could help", module.uses());
            learning.add(head.append("</div>").toString());
            if (!module.independent().isEmpty()) {
                StringBuilder own = new StringBuilder("<div class=\"part\">");
                list(own, "What you should still be able to do yourself", module.independent());
                learning.add(own.append("</div>").toString());
            }
            if (module.experiment() != null) {
                learning.add("<div class=\"part part-last\"><div class=\"experiment\"><h4>A sensible study experiment</h4><p>"
                        + escape(module.experiment()) + "</p></div></div>");
            }
        }
        section(out, "<h2>Where AI can help your learning</h2><div class=\"caution\"><p>"
                + escape(report.aiLearningCaution()) + "</p></div><p>" + escape(perspectives.aiLearning()) + "</p>",
                learning);

        // 6. Where your own capability matters most
        List<String> focusCards = new ArrayList<>();
        for (var focus : practical.capabilityFocus()) {
            StringBuilder card = new StringBuilder("<div class=\"card\"><h3>").append(escape(focus.title()))
                    .append("</h3><p>").append(escape(focus.guidance())).append("</p>");
            if (focus.explanation() != null) {
                card.append("<h4>Why your capability matters here</h4><p>").append(escape(focus.explanation()))
                        .append("</p>");
            }
            if (focus.independentPractice() != null) {
                card.append("<h4>What to be able to do without AI</h4><p>").append(escape(focus.independentPractice()))
                        .append("</p>");
            }
            focusCards.add(card.append("</div>").toString());
        }
        section(out, "<h2>Where your own capability matters most</h2><p>" + escape(perspectives.humanPractical())
                + "</p>", focusCards);

        // 7. Technical foundations, in one statement
        if (practical.technicalSummary() != null) {
            section(out, "<h2>Technical and analytical foundations</h2>", List.of("<p>"
                    + escape(practical.technicalSummary()) + "</p>"));
        }

        // 8. Skills
        List<String> skills = new ArrayList<>();
        for (var skill : report.skills()) {
            StringBuilder card = new StringBuilder("<div class=\"card\"><h3>").append(escape(skill.title()))
                    .append("</h3><p>").append(escape(skill.explanation())).append("</p>");
            list(card, "In this course, that means", skill.inThisCourse());
            skills.add(card.append("</div>").toString());
        }
        section(out, "<h2>Skills to strengthen</h2>", skills);

        // 9. Safeguards
        section(out, "<h2>Safeguards</h2>", report.safeguards().stream().map(entry -> "<div class=\"card\"><h3>"
                + escape(entry.title()) + "</h3><p>" + escape(entry.text()) + "</p></div>").toList());

        // 10. Action plan: each phase heading stays with its first action; actions flow one by one
        List<String> steps = new ArrayList<>();
        for (var phase : report.plan()) {
            for (int index = 0; index < phase.steps().size(); index++) {
                var step = phase.steps().get(index);
                StringBuilder block = new StringBuilder("<div class=\"part").append(index == 0 ? " part-first" : "")
                        .append(index == phase.steps().size() - 1 ? " part-last" : "").append("\">");
                if (index == 0) {
                    block.append("<h3>").append(escape(phase.title())).append("</h3>");
                }
                block.append("<ul><li>").append(escape(step.text()));
                if (step.note() != null) {
                    block.append("<span class=\"note\">").append(escape(step.note())).append("</span>");
                }
                steps.add(block.append("</li></ul></div>").toString());
            }
        }
        section(out, "<h2>Your action plan</h2><p>" + escape(perspectives.actionPlanIntroduction()) + "</p>", steps);

        // 11. How to study in an AI-shaped university: one small block per heading, so the section can flow
        var strategy = practical.studyStrategy();
        String[] headings = {"Use AI", "Learn without AI", "Verify", "Demonstrate your own capability",
                "Build evidence"};
        List<List<String>> lists = List.of(strategy.useAi(), strategy.learnWithoutAi(), strategy.verify(),
                strategy.demonstrateOwnCapability(), strategy.buildEvidence());
        List<String> blocks = new ArrayList<>();
        for (int index = 0; index < headings.length; index++) {
            StringBuilder block = new StringBuilder("<div class=\"strategy\">");
            list(block, headings[index], lists.get(index));
            blocks.add(block.append("</div>").toString());
        }
        section(out, "<h2>How to study in an AI-shaped university</h2>", blocks);

        // 12. Perspective and limitations
        // 12. Perspective and limitations: short, so kept whole rather than leaving a tail of bullets on a last page
        out.append("<div class=\"keep\"><h2>Perspective and limitations</h2>");
        if (perspectives.closing() != null) {
            paragraphs(out, perspectives.closing());
        }
        out.append("<ul>");
        report.limitations().forEach(item -> out.append("<li>").append(escape(item)).append("</li>"));
        out.append("</ul></div></body></html>");
        return out.toString();
    }

    /** A section heading kept on the page of its first block; the remaining blocks flow, each kept whole. */
    private static void section(StringBuilder out, String head, List<String> blocks) {
        out.append("<div class=\"keep\">").append(head);
        if (!blocks.isEmpty()) {
            out.append(blocks.getFirst());
        }
        out.append("</div>");
        blocks.stream().skip(1).forEach(out::append);
    }

    private static void inline(StringBuilder out, String heading, List<String> values) {
        if (values.isEmpty()) return;
        out.append("<p class=\"small\">").append(escape(heading)).append(": ")
                .append(escape(String.join("; ", values))).append("</p>");
    }

    private static void list(StringBuilder out, String heading, List<String> values) {
        if (values.isEmpty()) return;
        out.append("<h4>").append(escape(heading)).append("</h4><ul>");
        values.forEach(value -> out.append("<li>").append(escape(value)).append("</li>"));
        out.append("</ul>");
    }

    private static void paragraphs(StringBuilder out, String text) {
        for (String paragraph : text.split("\\n\\s*\\n")) {
            out.append("<p>").append(escape(paragraph.strip())).append("</p>");
        }
    }

    /** The band word alone ("Moderate"): the measure name is already the card heading. */
    private static String band(String band) {
        return band.charAt(0) + band.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}
