package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeOutcome;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeStatus;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeValidator;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeVersions;
import com.chikere.futureai.pdf.university.openhtml.OpenHtmlUniversityDetailedReportPdfRenderer;
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
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two hand-reviewed courses, Computer Science and Nursing, through the real frozen policies, the v2 validator, the
 * v3 assembler and the v3 PDF. No provider: the narratives are the hand-written calibration drafts. Renders the PDFs,
 * their text and a contact sheet of each to target/university-report-v3-preview for manual inspection.
 */
class UniversityReportV3CalibrationTest {

    private static final Path PREVIEW = Path.of("target", "university-report-v3-preview");
    private static final Pattern PROGRAMMING = Pattern.compile("\\b(?:code|coding|debug\\w*|SQL|programming)\\b");

    @Test
    void bothCalibrationDraftsPassEveryRule() {
        var validator = new UniversityPaidNarrativeValidator();
        var cs = UniversityCalibrationChains.chain(UniversityCalibrationChains.COMPUTER_SCIENCE);
        var nursing = UniversityCalibrationChains.chain(UniversityCalibrationChains.NURSING);
        var csNarrative = UniversityCalibrationNarratives.computerScience(cs.context());
        var nursingNarrative = UniversityCalibrationNarratives.nursing(nursing.context());
        assertThat(validator.validate(csNarrative, cs.context())).isSameAs(csNarrative);
        assertThat(validator.validate(nursingNarrative, nursing.context())).isSameAs(nursingNarrative);
    }

    @Test
    void theTwoCoursesReadAsDifferentDisciplinesNotOneTemplateWithModuleNamesSwapped() {
        var cs = report(UniversityCalibrationChains.COMPUTER_SCIENCE);
        var nursing = report(UniversityCalibrationChains.NURSING);
        Set<String> csLines = practicalLines(cs);
        Set<String> nursingLines = practicalLines(nursing);
        assertThat(csLines).doesNotContainAnyElementsOf(nursingLines);
        Set<String> csWords = words(csLines);
        Set<String> nursingWords = words(nursingLines);
        // Jaccard similarity of their content words: what they share is study vocabulary (explain, check, compare).
        long shared = csWords.stream().filter(nursingWords::contains).count();
        assertThat((double) shared / (csWords.size() + nursingWords.size() - shared)).isLessThan(0.2);
        // Each course's practical lines carry its own discipline, and none of the other's.
        String csText = String.join(" ", csLines).toLowerCase(Locale.ROOT);
        String nursingText = String.join(" ", nursingLines).toLowerCase(Locale.ROOT);
        assertThat(csText).contains("schema", "algorithm", "model", "test").doesNotContain("patient", "physiolog",
                "drug", "placement");
        assertThat(nursingText).contains("patient", "physiolog", "drug", "placement").doesNotContain("schema",
                "algorithm", "sql");
        // Every module's AI-learning uses are its own: no two modules in either course share a use.
        for (var report : List.of(cs, nursing)) {
            var uses = report.practical().moduleLearning().stream().flatMap(module -> module.uses().stream()).toList();
            assertThat(new HashSet<>(uses)).hasSameSizeAs(uses);
            assertThat(report.practical().moduleLearning()).allSatisfy(module -> {
                assertThat(module.uses()).hasSizeBetween(2, 4);
                assertThat(module.independent()).isNotEmpty();
                assertThat(module.experiment()).isNotBlank();
                assertThat(module.explanation()).isNotEqualTo(module.guidance());
            });
        }
        // The v1 template no longer reaches the customer as advice: the same sentence was given to every module.
        assertThat(cs.practical().moduleLearning()).extracting(UniversityDetailedReport.ModuleLearning::explanation)
                .noneMatch(text -> text.contains("explaining concepts, generating practice problems"));
    }

    @Test
    void databaseSystemsDoesNotReadLikeMachineLearningAndProgrammingGuidanceIsConcrete() {
        var cs = report(UniversityCalibrationChains.COMPUTER_SCIENCE);
        var byTitle = cs.practical().moduleLearning().stream().collect(Collectors.toMap(
                UniversityDetailedReport.ModuleLearning::title, module -> module));
        var databases = byTitle.get("Database Systems");
        var learning = byTitle.get("Machine Learning");
        var programming = byTitle.get("Programming and Software Development");
        assertThat(overlap(words(Set.copyOf(databases.uses())), words(Set.copyOf(learning.uses())))).isLessThan(0.3);
        assertThat(String.join(" ", databases.uses())).contains("SQL", "schema");
        assertThat(String.join(" ", learning.uses())).contains("overfitting", "metric");
        assertThat(String.join(" ", programming.independent())).contains("Tracing", "Debugging");
        // Programming vocabulary fits this course, so the fixed technical wording keeps it.
        assertThat(cs.safeguards()).anySatisfy(entry -> assertThat(entry.text()).contains("code"));
    }

    @Test
    void nursingUsesNoProgrammingVocabularyAndKeepsProfessionalJudgementHumanOwned() throws Exception {
        var nursing = report(UniversityCalibrationChains.NURSING);
        String everything = String.join("\n", customerText(nursing));
        assertThat(PROGRAMMING.matcher(everything).find()).as("programming vocabulary in Nursing").isFalse();
        // The frozen guidance names code for every technical module; v3 words it for the course instead.
        assertThat(nursing.safeguards()).anySatisfy(entry -> assertThat(entry.text())
                .contains("calculations or technical reasoning"));
        assertThat(nursing.practical().technicalSummary()).contains("calculations or explanations")
                .doesNotContain("code");
        var pharmacology = nursing.practical().moduleLearning().stream()
                .filter(module -> module.title().startsWith("Pharmacology")).findFirst().orElseThrow();
        assertThat(String.join(" ", pharmacology.independent())).contains("never treating AI output as authoritative");
        assertThat(nursing.practical().capabilityFocus()).extracting(UniversityDetailedReport.CapabilityFocus::title)
                .contains("Clinical Placements", "Clinical Skills and Simulation", "Communication and Compassionate Care");
        assertThat(nursing.safeguards()).extracting(UniversityDetailedReport.Entry::title)
                .contains("Professional and client-facing standards", "Your own work in assessed modules");
        var pdfText = renderPdf("nursing", nursing);
        assertThat(PROGRAMMING.matcher(pdfText).find()).isFalse();
    }

    @Test
    void theDeterministicReportIsIdenticalWithAndWithoutTheNarrative() {
        for (String course : List.of(UniversityCalibrationChains.COMPUTER_SCIENCE, UniversityCalibrationChains.NURSING)) {
            var personalised = report(course);
            var chain = UniversityCalibrationChains.chain(course);
            var fallback = new UniversityDetailedReportAssembler().assemble(chain.assessment(), chain.modules(),
                    chain.guidance(), outcome(UniversityPaidNarrativeStatus.FALLBACK, Optional.empty()));
            assertThat(personalised.profile()).isEqualTo(fallback.profile());
            assertThat(personalised.modules()).isEqualTo(fallback.modules());
            assertThat(personalised.aiLearning()).isEqualTo(fallback.aiLearning());
            assertThat(personalised.ownCapability()).isEqualTo(fallback.ownCapability());
            assertThat(personalised.technicalFoundations()).isEqualTo(fallback.technicalFoundations());
            assertThat(personalised.safeguards()).isEqualTo(fallback.safeguards());
            assertThat(personalised.plan()).extracting(UniversityDetailedReport.Phase::actions)
                    .isEqualTo(fallback.plan().stream().map(UniversityDetailedReport.Phase::actions).toList());
            assertThat(personalised.skills()).extracting(UniversityDetailedReport.Skill::title,
                    UniversityDetailedReport.Skill::explanation).isEqualTo(fallback.skills().stream()
                    .map(skill -> org.assertj.core.groups.Tuple.tuple(skill.title(), skill.explanation())).toList());
            assertThat(personalised.practical().moduleLearning()).extracting(UniversityDetailedReport.ModuleLearning::title)
                    .isEqualTo(fallback.practical().moduleLearning().stream()
                            .map(UniversityDetailedReport.ModuleLearning::title).toList());
            assertThat(fallback.practical().studyStrategy().personalised()).isFalse();
            assertThat(fallback.practical().studyStrategy().verify()).isNotEmpty();
        }
    }

    @Test
    void bothV3PdfsRenderEverySectionInOrderAndArePreviewed() throws Exception {
        Files.createDirectories(PREVIEW);
        for (String course : List.of(UniversityCalibrationChains.COMPUTER_SCIENCE, UniversityCalibrationChains.NURSING)) {
            var report = report(course);
            String text = renderPdf(course, report).replaceAll("\\s+", " ");
            assertThat(text).containsSubsequence("Your course profile", "What the measures mean", "Your modules",
                    "Where AI can help your learning", "How AI could help",
                    "What you should still be able to do yourself", "A sensible study experiment",
                    "Where your own capability matters most", "Why your capability matters here",
                    "Skills to strengthen", "In this course, that means", "Safeguards", "Your action plan",
                    "How to study in an AI-shaped university", "Use AI", "Learn without AI", "Verify",
                    "Demonstrate your own capability", "Build evidence", "Perspective and limitations");
            // No near-empty page: no heading or bullet tail stranded on a page of its own.
            try (var document = Loader.loadPDF(Files.readAllBytes(PREVIEW.resolve(course + ".pdf")))) {
                for (int page = 2; page <= document.getNumberOfPages(); page++) {
                    var stripper = new PDFTextStripper();
                    stripper.setStartPage(page);
                    stripper.setEndPage(page);
                    assertThat(stripper.getText(document).strip().length()).as(course + " page " + page)
                            .isGreaterThan(400);
                }
            }
            // Each classification's explanation is given once, not on every card.
            for (var note : report.practical().classificationNotes()) {
                assertThat(text.split(Pattern.quote(note.text()), -1)).as(note.text()).hasSize(2);
            }
        }
    }

    private static String renderPdf(String course, UniversityDetailedReport report) throws Exception {
        Files.createDirectories(PREVIEW);
        byte[] pdf = new OpenHtmlUniversityDetailedReportPdfRenderer().render(report);
        Files.write(PREVIEW.resolve(course + ".pdf"), pdf);
        try (var document = Loader.loadPDF(pdf)) {
            String text = new PDFTextStripper().getText(document);
            Files.writeString(PREVIEW.resolve(course + ".txt"), text);
            writeContactSheet(new PDFRenderer(document), document.getNumberOfPages(),
                    PREVIEW.resolve(course + "-pages.png"));
            return text;
        }
    }

    private static void writeContactSheet(PDFRenderer pages, int count, Path target) throws java.io.IOException {
        int columns = 4;
        int rows = (count + columns - 1) / columns;
        int width = 331;
        int height = 468;
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

    static UniversityDetailedReport report(String course) {
        return UniversityCalibrationChains.report(course);
    }

    static UniversityPaidNarrativeOutcome outcome(UniversityPaidNarrativeStatus status,
            Optional<UniversityPaidNarrative> narrative) {
        return UniversityCalibrationChains.outcome(status, narrative);
    }

    private static Set<String> practicalLines(UniversityDetailedReport report) {
        Set<String> lines = new HashSet<>();
        report.practical().moduleLearning().forEach(module -> {
            lines.addAll(module.uses());
            lines.addAll(module.independent());
            lines.add(module.experiment());
        });
        report.practical().capabilityFocus().forEach(focus -> lines.add(focus.independentPractice()));
        report.skills().forEach(skill -> lines.addAll(skill.inThisCourse()));
        var strategy = report.practical().studyStrategy();
        Stream.of(strategy.useAi(), strategy.learnWithoutAi(), strategy.verify(), strategy.demonstrateOwnCapability(),
                strategy.buildEvidence()).forEach(lines::addAll);
        return lines;
    }

    /** Every customer-facing text of the report, deterministic and narrative alike. */
    private static List<String> customerText(UniversityDetailedReport report) {
        List<String> texts = new java.util.ArrayList<>(practicalLines(report));
        var p = report.perspectives();
        texts.addAll(List.of(p.overall(), p.transferable(), p.aiLearning(), p.humanPractical(),
                p.actionPlanIntroduction(), p.coursePattern(), p.closing()));
        report.practical().moduleLearning().forEach(module -> texts.addAll(List.of(module.guidance(),
                module.explanation())));
        report.practical().capabilityFocus().forEach(focus -> texts.addAll(List.of(focus.guidance(),
                focus.explanation())));
        texts.add(report.practical().technicalSummary());
        Stream.of(report.aiLearning(), report.ownCapability(), report.technicalFoundations(), report.safeguards())
                .flatMap(List::stream).forEach(entry -> texts.add(entry.text()));
        report.skills().forEach(skill -> texts.add(skill.explanation()));
        report.plan().forEach(phase -> phase.steps().forEach(step -> {
            texts.add(step.text());
            if (step.note() != null) {
                texts.add(step.note());
            }
        }));
        return texts.stream().filter(java.util.Objects::nonNull).toList();
    }

    private static Set<String> words(Set<String> lines) {
        return lines.stream().flatMap(line -> Stream.of(line.toLowerCase(Locale.ROOT).split("[^a-z]+")))
                .filter(word -> word.length() > 4).collect(Collectors.toSet());
    }

    private static double overlap(Set<String> a, Set<String> b) {
        long shared = a.stream().filter(b::contains).count();
        return (double) shared / Math.min(a.size(), b.size());
    }
}
