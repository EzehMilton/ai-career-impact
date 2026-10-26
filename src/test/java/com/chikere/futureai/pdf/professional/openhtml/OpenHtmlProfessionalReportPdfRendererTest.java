package com.chikere.futureai.pdf.professional.openhtml;

import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2;
import com.chikere.futureai.decision.professional.ProfessionalMaterialityCalibrationCases;
import com.chikere.futureai.pdf.professional.application.ProfessionalReportPdfModel;
import com.chikere.futureai.pdf.professional.application.ProfessionalReportPdfVersions;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssembler;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssemblerTest;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHtmlProfessionalReportPdfRendererTest {

    private final OpenHtmlProfessionalReportPdfRenderer renderer = new OpenHtmlProfessionalReportPdfRenderer();

    @Test
    void rendersAValidPdf() {
        byte[] pdf = renderer.render(model()).bytes();

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void thePdfShowsAPlainStatusAndScoresWithoutConfidenceOrTheGeneratedCaution() {
        String html = renderer.html(model());

        assertThat(html).contains("<span class=\"pill\">Status:</span> Assessment complete",
                "<div class=\"score\">Score: ", "<span class=\"pill\">Band:</span> Moderate",
                "What increases AI Exposure", "What reduces AI Exposure",
                "How much scope there is for AI assistance while meaningful human contribution remains.");
        assertThat(html).doesNotContain("Assessment confidence", "Confidence:", "Caution and limitations",
                "<p>Caution</p>", "Ready with caution", "Key protective factors", "Band:</span> MODERATE");
        // Percentages appear in the stylesheet; none may appear as visible text.
        assertThat(html.replaceAll("(?s)<style>.*?</style>", "")).doesNotContainPattern(">[^<]*\\d+%[^<]*<");
    }

    @Test
    void thePdfDoesNotRepeatExplanationsOrPresentTheVisitorsOwnResponsibilitiesOrInternalWording() {
        String html = renderer.html(model());

        assertThat(html.split("Detailed exposure", -1)).hasSize(2);
        assertThat(html).containsSubsequence("What to focus on next", "AI-tool fluency", "Domain expertise",
                "Process automation");
        assertThat(html).contains("About this guidance", "Career context",
                "Where reliable public information is available, we show it separately from your assessment scores.");
        assertThat(html).doesNotContain("Design systems", "did not judge each responsibility",
                "does not assign task-level scores", "deterministic presentation guidance",
                "does not validate or determine", "Methodology and limitations", "Occupation classification",
                "Judgement schema", "Decision policy:", "Full personalised interpretation",
                "Verified external career information was not available");
    }

    @Test
    void withoutVerifiedEvidenceThePdfSaysSoInOneQuietSentence() {
        var source = ProfessionalReportAssemblerTest.source(true, false, true);
        var report = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        String html = renderer.html(new ProfessionalReportPdfModel(ProfessionalReportPdfVersions.PDF_TEMPLATE,
                report.reportVersion(), Instant.parse("2026-10-01T09:00:00Z"), report));

        assertThat(html).contains("Verified external career information was not available for this assessment.");
        assertThat(html).doesNotContain("Career context", "External career context is currently unavailable");
    }

    @Test
    void anEmptyFactorSideRendersAnExplanatorySentenceInsteadOfAnEmptyList() {
        String noDrivers = html(ProfessionalMaterialityCalibrationCases.registeredNurse());
        assertThat(noDrivers).contains("<h3>What increases AI Exposure</h3><p>No strong exposure drivers were identified.</p>")
                .contains("<h3>What reduces AI Exposure</h3><ul><li>")
                .doesNotContain("No strong factors reducing AI Exposure were identified.", "<ul></ul>");

        String noReducers = html(ProfessionalMaterialityCalibrationCases.managementAccountant());
        assertThat(noReducers).contains("<h3>What increases AI Exposure</h3><ul><li>",
                "<h3>What reduces AI Exposure</h3><p>No strong factors reducing AI Exposure were identified.</p>")
                .doesNotContain("No strong exposure drivers were identified.", "<ul></ul>");

        String neither = html(ProfessionalMaterialityCalibrationCases.raw(Map.of("task_repeatability", 0.01,
                "human_trust_dependency", -0.012)));
        assertThat(neither).contains("No strong exposure drivers were identified.",
                "No strong factors reducing AI Exposure were identified.").doesNotContain("<ul></ul>");

        // With both sides populated neither sentence appears.
        assertThat(renderer.html(model())).doesNotContain("No strong exposure drivers were identified.",
                "No strong factors reducing AI Exposure were identified.");
        assertThat(ProfessionalReportPdfVersions.PDF_TEMPLATE).isEqualTo("professional-report-pdf-v4");
    }

    @Test
    void theLiveJavaBoundaryScoreReadsSixPointNineNineInTheNewPdf() {
        String html = html(ProfessionalReportAssemblerTest.JAVA_ROLE_ADAPTABILITY_BOUNDARY);

        assertThat(html.substring(html.indexOf("Role Adaptability"))).containsSubsequence("Score: 6.99 / 10",
                "Band:</span> Moderate");
        assertThat(html).doesNotContain("Score: 7.00 / 10");
        assertThat(ProfessionalReportPdfVersions.PDF_TEMPLATE).isEqualTo("professional-report-pdf-v4");
    }

    private String html(Map<String, Double> values) {
        var source = ProfessionalReportAssemblerTest.source(true, true, true, values, new ProfessionalDecisionPolicyV2());
        var report = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        assertThat(new String(renderer.render(new ProfessionalReportPdfModel(ProfessionalReportPdfVersions.PDF_TEMPLATE,
                report.reportVersion(), Instant.parse("2026-10-26T09:00:00Z"), report)).bytes(), 0, 5,
                StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        return renderer.html(new ProfessionalReportPdfModel(ProfessionalReportPdfVersions.PDF_TEMPLATE,
                report.reportVersion(), Instant.parse("2026-10-26T09:00:00Z"), report));
    }

    /** A purchased report whose stored status is READY_WITH_CAUTION and whose narrative carries a caution. */
    private static ProfessionalReportPdfModel model() {
        var source = ProfessionalReportAssemblerTest.source(true, true, true, Map.of("information_sufficient", 0.7));
        assertThat(source.assessment().result().decision().assessmentDecision().status())
                .isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        var report = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        assertThat(report.narrative().cautionOrLimitations()).contains("Caution");
        return new ProfessionalReportPdfModel(ProfessionalReportPdfVersions.PDF_TEMPLATE, report.reportVersion(),
                Instant.parse("2026-10-01T09:00:00Z"), report);
    }
}
