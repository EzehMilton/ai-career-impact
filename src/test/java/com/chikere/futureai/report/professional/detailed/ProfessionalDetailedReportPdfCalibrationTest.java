package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.payment.professional.application.PurchasedProfessionalReportSnapshot;
import com.chikere.futureai.pdf.professional.openhtml.OpenHtmlProfessionalDetailedReportPdfRenderer;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssembler;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssemblerTest;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * professional-report-pdf-v6 for the seven captured roles, with the deterministic (fallback) copy and once with a
 * validated narrative, and a stored professional-report-v3 still rendered exactly as professional-report-pdf-v5.
 * Structural assertions only for v6, no byte comparison. A Markdown preview of each role's report is written to
 * target/detailed-report-preview for review.
 */
class ProfessionalDetailedReportPdfCalibrationTest {

    private static final List<String> HEADINGS = List.of("Executive summary", "Your four measures",
            "What drives your AI Exposure", "Your work, responsibility by responsibility",
            "Best opportunities for AI assistance", "Where your human contribution matters most", "Skills to strengthen",
            "Responsible AI use", "Your 90-day plan", "Your AI adaptation strategy",
            "What this assessment does and does not mean", "Next steps");

    private final OpenHtmlProfessionalDetailedReportPdfRenderer renderer = new OpenHtmlProfessionalDetailedReportPdfRenderer();
    private final ProfessionalDetailedReportViewMapper views = new ProfessionalDetailedReportViewMapper();

    @Test
    void everyCalibrationRoleRendersACompleteCustomerFacingPdf() throws Exception {
        Path preview = Path.of("target/detailed-report-preview");
        Files.createDirectories(preview);
        List<String> pages = new ArrayList<>();
        for (String run : DetailedReportFixtures.ROLES.keySet()) {
            var chain = DetailedReportFixtures.chain(run);
            var view = views.toView(DetailedReportFixtures.snapshot(chain, DetailedReportFixtures.fallback(chain)));
            byte[] bytes = renderer.render(view);
            Files.write(preview.resolve(run + ".pdf"), bytes);
            Files.writeString(preview.resolve(run + ".md"), markdown(view));
            assertThat(bytes).as(run).startsWith("%PDF-".getBytes());
            try (var document = Loader.loadPDF(bytes)) {
                pages.add(run + "=" + document.getNumberOfPages());
                assertThat(document.getNumberOfPages()).as(run).isBetween(5, 12);
                String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
                assertThat(text).as(run).contains(HEADINGS.toArray(String[]::new))
                        .contains("Professional AI Career Impact Report", DetailedReportFixtures.ROLES.get(run),
                                "Days 1–30", "Days 31–60", "Days 61–90", "professional-report-v4", "professional-report-pdf-v6");
                for (var card : view.report().responsibilities().cards()) {
                    assertThat(text).as(run).contains(card.text());
                }
                // Each stored classification sentence is shown once, not once per card that shares it.
                for (var note : view.categoryNotes()) {
                    assertThat(text.split(java.util.regex.Pattern.quote(note.explanation()), -1)).as(run).hasSize(2);
                }
                for (var measure : view.measures()) {
                    assertThat(text).as(run).contains(measure.score() + " / 10");
                }
                for (var phase : view.report().plan().phases()) {
                    phase.actions().forEach(action -> assertThat(text).as(run)
                            .contains(action.title().substring(0, Math.min(40, action.title().length()))));
                }
                assertThat(text).as(run).doesNotContainPattern("\\b[A-Z][A-Z0-9]*_[A-Z0-9_]+\\b")
                        .doesNotContain("probabilit", "distribution", "confidence", "calibrat", "described clearly",
                                "close call", "threshold", "[0.", "automation risk", "safe from AI");
            }
        }
        Files.writeString(preview.resolve("pages.txt"), String.join("\n", pages));
    }

    @Test
    void anAvailableNarrativeAppearsBesideTheDeterministicContent() throws Exception {
        var chain = DetailedReportFixtures.chain("customer-service-adviser-8");
        var narrative = DetailedReportFixtures.validNarrative(chain.context());
        var view = views.toView(DetailedReportFixtures.snapshot(chain, DetailedReportFixtures.available(chain, narrative)));
        try (var document = Loader.loadPDF(renderer.render(view))) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text).contains("Your work combines information tasks", "This is a sensible place to try AI",
                    "Investigate account problems", "What this could look like", "Organising your existing notes",
                    "A sensible first experiment", "Also apply, from Responsible AI use: Human review; Approved tools and "
                            + "confidentiality", "Your AI adaptation strategy")
                    .doesNotContain("Use only tools your organisation has approved for this kind of information.");
            // The approved-tools condition is stated once, in Responsible AI use, not on every opportunity.
            assertThat(text.split("Follow your employer's and profession's policies", -1)).hasSize(2);
        }
    }

    @Test
    void aStoredV3ReportIsStillRenderedExactlyAsTheV5PdfRenderedIt() throws Exception {
        var snapshot = DetailedReportFixtures.historicalV3Snapshot();
        var view = views.toView(snapshot);
        assertThat(view.reportVersion()).isEqualTo("professional-report-v3");
        assertThat(view.pdfVersion()).isEqualTo("professional-report-pdf-v5");
        assertThat(view.report().adaptationStrategy()).isNull();
        assertThat(view.report().opportunities().items()).allSatisfy(item -> {
            assertThat(item.practicalAiUses()).isEmpty();
            assertThat(item.appliedSafeguards()).isEmpty();
        });
        // Byte-identical to the HTML the pre-v4 renderer produced for this stored report.
        assertThat(renderer.html(view)).isEqualTo(DetailedReportFixtures.historical(
                "professional-report-pdf-v5-customer-service-adviser.html"));
        try (var document = Loader.loadPDF(renderer.render(view))) {
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
            assertThat(text).contains("professional-report-v3", "professional-report-pdf-v5")
                    .doesNotContain("Your AI adaptation strategy", "What this could look like");
        }
    }

    @Test
    void scoresUseTheBoundarySafeDisplaySoAModerateJustBelowSevenNeverReadsSevenPointZero() throws Exception {
        var chain = DetailedReportFixtures.chain("java-software-developer-8");
        var source = ProfessionalReportAssemblerTest.source(false, false, false,
                ProfessionalReportAssemblerTest.JAVA_ROLE_ADAPTABILITY_BOUNDARY);
        var boundaryReport = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        var role = new PurchasedProfessionalReportSnapshot(chain.role().entitlementId(), chain.analysis().assessmentId(),
                boundaryReport.reportVersion(), Instant.parse("2026-10-27T09:05:00Z"), boundaryReport);
        var boundary = new DetailedReportFixtures.Chain(role, chain.analysis(), chain.guidance());
        var view = views.toView(DetailedReportFixtures.snapshot(boundary, DetailedReportFixtures.fallback(boundary)));
        assertThat(view.measures()).anySatisfy(measure -> {
            assertThat(measure.name()).isEqualTo("Role Adaptability");
            assertThat(measure.score()).isEqualTo("6.99");
            assertThat(measure.band()).isEqualTo("Moderate");
        });
        try (var document = Loader.loadPDF(renderer.render(view))) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("6.99 / 10").doesNotContain("7.00 / 10");
        }
    }

    /** The structural preview reviewed for Stage 4F calibration. */
    static String markdown(ProfessionalDetailedReportView view) {
        var r = view.report();
        StringBuilder out = new StringBuilder("# ").append(r.cover().profession()).append("\n\n## Executive summary\n");
        r.executiveSummary().forEach(p -> out.append("\n").append(p).append("\n"));
        out.append("\n## Measures\n");
        view.measures().forEach(m -> out.append("- ").append(m.name()).append(": ").append(m.score()).append(" ")
                .append(m.band()).append("\n"));
        out.append("\n## Responsibilities\n");
        if (r.responsibilities().oversightNote() != null) {
            out.append("_").append(r.responsibilities().oversightNote()).append("_\n");
        }
        r.responsibilities().cards().forEach(c -> out.append("- ").append(c.text()).append(" — ").append(c.categoryLabel())
                .append(c.showOversight() ? " — needs close human oversight" : "").append(" — ")
                .append(String.join(", ", c.signals())).append("\n  ").append(c.explanation()).append("\n"));
        out.append("\n## Opportunities\n");
        r.opportunities().items().forEach(o -> out.append(o.rank()).append(". ").append(o.responsibility()).append(" (")
                .append(o.categoryLabel()).append(")\n   ").append(o.whyCandidate()).append("\n   ").append(o.howAiMayAssist())
                .append("\n   Conditions: ").append(String.join(" | ", o.conditions())).append("\n"));
        out.append("\n## Human value\n");
        r.humanValue().items().forEach(h -> out.append("- ").append(h.responsibility()).append(" — ").append(h.reason())
                .append(": ").append(h.reasonExplanation()).append("\n"));
        out.append("\n## Skills\n");
        r.skills().items().forEach(s -> out.append(s.rank()).append(". ").append(s.label()).append(" — ").append(s.reason())
                .append("\n"));
        out.append("\n## Safeguards\n");
        r.safeguards().items().forEach(s -> out.append("- ").append(s.essential() ? "[Essential] " : "").append(s.title())
                .append(": ").append(s.guidance()).append("\n"));
        out.append("\n## 90-day plan\n");
        r.plan().phases().forEach(p -> {
            out.append("### ").append(p.heading()).append(": ").append(p.purpose()).append("\n");
            p.actions().forEach(a -> out.append("- ").append(a.title()).append("\n"));
        });
        out.append("\n## Next steps\n");
        r.nextSteps().forEach(n -> out.append("- ").append(n).append("\n"));
        return out.toString();
    }
}
