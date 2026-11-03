package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeValidator;
import com.chikere.futureai.pdf.professional.openhtml.OpenHtmlProfessionalDetailedReportPdfRenderer;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeUseConstraint;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Quality calibration of professional-paid-narrative-v2 and professional-report-v4 on the captured Registered Nurse
 * and Management Accountant chains, fully offline: the hand-written fixtures pass the real validator, are assembled
 * by the real assembler and rendered by the real HTML/PDF code. Each report, its text and its page images are written
 * to target/detailed-report-v4-preview for review.
 */
class ProfessionalPaidNarrativeCalibrationTest {

    private static final Path PREVIEW = Path.of("target/detailed-report-v4-preview");
    private final ProfessionalPaidNarrativeValidator validator = new ProfessionalPaidNarrativeValidator();
    private final ProfessionalDetailedReportViewMapper views = new ProfessionalDetailedReportViewMapper();
    private final OpenHtmlProfessionalDetailedReportPdfRenderer renderer = new OpenHtmlProfessionalDetailedReportPdfRenderer();

    @Test
    void bothCalibrationNarrativesPassTheV2ContractUnchanged() {
        for (String run : List.of(PaidNarrativeCalibrationFixtures.NURSE, PaidNarrativeCalibrationFixtures.ACCOUNTANT)) {
            var context = DetailedReportFixtures.chain(run).context();
            var narrative = PaidNarrativeCalibrationFixtures.forRun(run).orElseThrow();
            assertThat(validator.validate(narrative, context)).as(run).isSameAs(narrative);
        }
    }

    @Test
    void theTwoReportsReadAsReportsForDifferentOccupations() {
        var nurse = report(PaidNarrativeCalibrationFixtures.NURSE);
        var accountant = report(PaidNarrativeCalibrationFixtures.ACCOUNTANT);
        Set<String> nurseLines = practicalLines(nurse);
        Set<String> accountantLines = practicalLines(accountant);
        assertThat(nurseLines).isNotEmpty().doesNotContainAnyElementsOf(accountantLines);
        // Word overlap of the practical content, ignoring short function words, stays low.
        assertThat(overlap(words(nurseLines), words(accountantLines))).isLessThan(0.2);
        assertThat(String.join(" ", nurseLines)).contains("handover", "observations", "deterioration");
        assertThat(String.join(" ", accountantLines)).contains("variance", "month-end", "budget holders");
    }

    @Test
    void everyOpportunityHasItsOwnConcreteContentRatherThanTheCategorySentence() {
        for (String run : List.of(PaidNarrativeCalibrationFixtures.NURSE, PaidNarrativeCalibrationFixtures.ACCOUNTANT)) {
            var report = report(run);
            var items = report.opportunities().items();
            assertThat(items).as(run).isNotEmpty().allSatisfy(item -> {
                assertThat(item.practicalAiUses()).hasSizeBetween(2, 4);
                assertThat(item.humanOwnership()).hasSizeBetween(1, 3);
                assertThat(item.firstExperiment()).isNotBlank();
                assertThat(item.practicalAiUses()).noneMatch(use -> use.contains("preparation, drafting, checking or "
                        + "information handling"));
            });
            // No two opportunities share their practical uses.
            assertThat(items.stream().map(ProfessionalDetailedReport.Opportunity::practicalAiUses).distinct())
                    .as(run).hasSize(items.size());
            assertThat(report.skills().items()).as(run).allSatisfy(skill -> assertThat(skill.inYourWork()).isNotEmpty());
            assertThat(report.humanValue().items()).as(run).allSatisfy(area -> assertThat(area.strengthen()).isNotBlank());
            assertThat(report.adaptationStrategy().personalised()).isTrue();
        }
    }

    @Test
    void theNurseExamplesStayOnDocumentationPreparationAndChecking() {
        var report = report(PaidNarrativeCalibrationFixtures.NURSE);
        Pattern unsafe = Pattern.compile("(?i)\\b(?:diagnos\\w*|prescrib\\w*|dos(?:e|es|age|ing)|triag\\w*|treatment)\\b");
        report.opportunities().items().forEach(item -> Stream.concat(item.practicalAiUses().stream(),
                Stream.of(item.firstExperiment())).forEach(example -> assertThat(example).doesNotContainPattern(unsafe)));
        // Ownership states what stays clinical and human.
        assertThat(String.join(" ", report.opportunities().items().getFirst().humanOwnership()))
                .contains("recognising signs of deterioration", "escalating");
    }

    @Test
    void theDeterministicFindingsAreIdenticalWithAndWithoutTheNarrative() {
        for (String run : List.of(PaidNarrativeCalibrationFixtures.NURSE, PaidNarrativeCalibrationFixtures.ACCOUNTANT)) {
            var chain = DetailedReportFixtures.chain(run);
            var with = report(run);
            var without = new ProfessionalDetailedReportAssembler().assemble(chain.role(), chain.analysis(),
                    chain.guidance(), DetailedReportFixtures.fallback(chain));
            assertThat(with.measures()).isEqualTo(without.measures());
            assertThat(with.factors()).isEqualTo(without.factors());
            assertThat(with.responsibilities().cards()).isEqualTo(without.responsibilities().cards());
            assertThat(with.safeguards()).isEqualTo(without.safeguards());
            assertThat(with.opportunities().items()).extracting(ProfessionalDetailedReport.Opportunity::key)
                    .isEqualTo(without.opportunities().items().stream().map(ProfessionalDetailedReport.Opportunity::key).toList());
            assertThat(with.opportunities().items()).extracting(ProfessionalDetailedReport.Opportunity::conditions)
                    .isEqualTo(without.opportunities().items().stream().map(ProfessionalDetailedReport.Opportunity::conditions).toList());
            assertThat(with.skills().items()).extracting(ProfessionalDetailedReport.Skill::label)
                    .isEqualTo(without.skills().items().stream().map(ProfessionalDetailedReport.Skill::label).toList());
            assertThat(with.plan().phases()).extracting(ProfessionalDetailedReport.Phase::actions)
                    .extracting(actions -> actions.stream().map(ProfessionalDetailedReport.Action::title).toList())
                    .isEqualTo(without.plan().phases().stream().map(phase -> phase.actions().stream()
                            .map(ProfessionalDetailedReport.Action::title).toList()).toList());
        }
    }

    @Test
    void bothReportsRenderOfflineAsV6PdfsWithEverySectionAndUniversalSafeguardsStatedOnce() throws Exception {
        Files.createDirectories(PREVIEW);
        for (String run : List.of(PaidNarrativeCalibrationFixtures.NURSE, PaidNarrativeCalibrationFixtures.ACCOUNTANT)) {
            var chain = DetailedReportFixtures.chain(run);
            var narrative = PaidNarrativeCalibrationFixtures.forRun(run).orElseThrow();
            var snapshot = DetailedReportFixtures.snapshot(chain, DetailedReportFixtures.available(chain, narrative));
            var view = views.toView(snapshot);
            assertThat(view.pdfVersion()).isEqualTo(ProfessionalDetailedReportVersions.PDF_V6);
            byte[] pdf = renderer.render(view);
            Files.write(PREVIEW.resolve(run + ".pdf"), pdf);
            Files.writeString(PREVIEW.resolve(run + ".html"), renderer.html(view));
            try (var document = Loader.loadPDF(pdf)) {
                String text = new PDFTextStripper().getText(document);
                Files.writeString(PREVIEW.resolve(run + ".txt"), text);
                writeContactSheet(new PDFRenderer(document), document.getNumberOfPages(),
                        PREVIEW.resolve(run + "-pages.png"));
                assertThat(document.getNumberOfPages()).as(run).isBetween(8, 20);
                String flat = text.replaceAll("\\s+", " ");
                assertThat(flat).as(run).containsSubsequence("Executive summary", "Your four measures",
                        "What drives your AI Exposure", "Your work, responsibility by responsibility",
                        "Best opportunities for AI assistance", "What this could look like", "What stays yours",
                        "A sensible first experiment", "Where your human contribution matters most", "Why you matter here",
                        "What to deliberately strengthen", "Skills to strengthen", "In your work, this means",
                        "Responsible AI use", "Your 90-day plan", "Days 1–30", "Days 31–60", "Days 61–90",
                        "Your AI adaptation strategy", "Protect", "Learn", "Experiment", "Strengthen", "Monitor",
                        "What this assessment does and does not mean", "Next steps");
                assertThat(flat).contains("professional-report-v4", "professional-report-pdf-v6");
                // A universal condition the Responsible AI section states is not repeated on every opportunity.
                for (SafeUseConstraint constraint : SafeUseConstraint.values()) {
                    assertThat(occurrences(flat, constraint.guidance())).as(run + " " + constraint).isLessThanOrEqualTo(1);
                }
                for (var page = 1; page < document.getNumberOfPages(); page++) {
                    var stripper = new PDFTextStripper();
                    stripper.setStartPage(page + 1);
                    stripper.setEndPage(page + 1);
                    String pageText = stripper.getText(document).replaceAll("AI Career Impact · \\d+ / \\d+", "").strip();
                    // No near-empty page: every page after the cover carries real content.
                    assertThat(pageText.length()).as(run + " page " + (page + 1)).isGreaterThan(400);
                }
            }
        }
    }

    /** Every page side by side, five to a row, for reviewing pagination at a glance. */
    private static void writeContactSheet(PDFRenderer pages, int count, Path target) throws java.io.IOException {
        var first = pages.renderImageWithDPI(0, 40);
        int width = first.getWidth();
        int height = first.getHeight();
        int columns = 5;
        int rows = (count + columns - 1) / columns;
        var sheet = new java.awt.image.BufferedImage(columns * (width + 8), rows * (height + 8),
                java.awt.image.BufferedImage.TYPE_INT_RGB);
        var graphics = sheet.createGraphics();
        graphics.setColor(java.awt.Color.DARK_GRAY);
        graphics.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
        for (int page = 0; page < count; page++) {
            graphics.drawImage(pages.renderImageWithDPI(page, 40), (page % columns) * (width + 8) + 4,
                    (page / columns) * (height + 8) + 4, null);
        }
        graphics.dispose();
        ImageIO.write(sheet, "png", target.toFile());
    }

    private static ProfessionalDetailedReport report(String run) {
        var chain = DetailedReportFixtures.chain(run);
        var narrative = PaidNarrativeCalibrationFixtures.forRun(run).orElseThrow();
        return new ProfessionalDetailedReportAssembler().assemble(chain.role(), chain.analysis(), chain.guidance(),
                DetailedReportFixtures.available(chain, narrative));
    }

    private static Set<String> practicalLines(ProfessionalDetailedReport report) {
        Set<String> lines = new HashSet<>();
        report.opportunities().items().forEach(item -> {
            lines.addAll(item.practicalAiUses());
            lines.addAll(item.humanOwnership());
            lines.add(item.firstExperiment());
        });
        report.skills().items().forEach(skill -> lines.addAll(skill.inYourWork()));
        return lines;
    }

    private static Set<String> words(Set<String> lines) {
        Set<String> words = new HashSet<>();
        lines.forEach(line -> Stream.of(line.toLowerCase(Locale.ROOT).split("[^a-z-]+"))
                .filter(word -> word.length() > 4).forEach(words::add));
        return words;
    }

    private static double overlap(Set<String> a, Set<String> b) {
        Set<String> shared = new HashSet<>(a);
        shared.retainAll(b);
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return all.isEmpty() ? 0 : (double) shared.size() / all.size();
    }

    private static int occurrences(String text, String sentence) {
        int count = 0;
        for (int index = text.indexOf(sentence); index >= 0; index = text.indexOf(sentence, index + 1)) {
            count++;
        }
        return count;
    }
}
