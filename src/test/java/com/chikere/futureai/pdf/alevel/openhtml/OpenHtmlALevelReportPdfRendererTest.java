package com.chikere.futureai.pdf.alevel.openhtml;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceBundle;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceCategory;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceItem;
import com.chikere.futureai.evidence.alevel.application.EvidenceSource;
import com.chikere.futureai.evidence.alevel.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.alevel.application.EvidenceStatus;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrative;
import com.chikere.futureai.narrative.alevel.application.NarrativeSource;
import com.chikere.futureai.narrative.alevel.application.NarrativeStatus;
import com.chikere.futureai.narrative.alevel.application.StoredALevelNarrative;
import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfModel;
import com.chikere.futureai.report.alevel.application.ALevelReport;
import com.chikere.futureai.report.alevel.application.ALevelReportAssembler;
import com.chikere.futureai.report.alevel.application.ALevelReportSource;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHtmlALevelReportPdfRendererTest {

    private static final UUID ID = UUID.fromString("4b5c6d7e-8f9a-4b0c-8d1e-2f3a4b5c6d7e");
    private final OpenHtmlALevelReportPdfRenderer renderer = new OpenHtmlALevelReportPdfRenderer();

    @Test
    void rendersAValidPdfDeterministicallyFromThePurchasedSnapshotOnly() {
        var model = model();

        byte[] first = renderer.render(model).bytes();
        byte[] second = renderer.render(model).bytes();

        assertThat(new String(first, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(first.length).isEqualTo(second.length);
        assertThat(first.length).isGreaterThan(1_000);
    }

    @Test
    void theTemplateHasNoNetworkInputsAndEscapesPurchasedContent() {
        String html = renderer.html(model());

        assertThat(html).doesNotContain("<img", "http://cdn", "https://cdn", "@import",
                "<script", "<iframe", "<link");
        // The only HTTP(S) reference is a persisted evidence hyperlink, which is never fetched.
        assertThat(html).contains("<a href=\"https://example.test/evidence\"");
        assertThat(html).contains("&lt;script&gt;injected&lt;/script&gt;")
                .doesNotContain("<script>injected</script>");
    }

    @Test
    void theRenderedDocumentCarriesBothVersionsAndTheALevelMeasures() {
        String html = renderer.html(model());

        assertThat(html).contains("a-level-report-v1", "a-level-report-pdf-v1",
                "Future Optionality", "Degree Pathway Breadth", "Analytical Foundation",
                "Human and Creative Strength", "Skill Transferability", "AI-Era Adaptability",
                "Development fixture report", "not a prediction of university admission",
                "different constructs on different scales",
                "Mathematics, Economics, Geography", "predicted A");
        assertThat(html).doesNotContain("Role Adaptability", "Human Advantage", "Human Differentiation",
                "future-proof", "wrong subject");
        // The other journeys' headline names appear exactly once each, inside the cross-journey caveat.
        assertThat(html.split("Future Readiness", -1)).hasSize(2);
        assertThat(html.split("AI Exposure", -1)).hasSize(2);
        assertThat(html).contains("A-level Future Optionality, university Future Readiness and "
                + "professional AI Exposure are different constructs on different scales");
    }

    private ALevelReportPdfModel model() {
        return new ALevelReportPdfModel("a-level-report-pdf-v1", "a-level-report-v1",
                Instant.parse("2026-10-01T12:00:00Z"), paidReport());
    }

    private ALevelReport paidReport() {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of("Mathematics", "A"), List.of("Problem solving"), List.of("Economics"),
                List.of("Data analysis"), Optional.of("United Kingdom"), Optional.empty());
        var judgments = new LocalFixtureALevelJudgmentProvider("maths_economics_geography").judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        var assessment = new StoredALevelAssessment(ID, Instant.parse("2026-10-01T11:58:00Z"), state,
                judgments, new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        Instant time = Instant.parse("2026-10-01T12:02:00Z");
        var narrative = new ALevelNarrative("A practical reading", "<script>injected</script>",
                "Saved meaning", "Saved optionality", "Saved strengths", "Saved pathways",
                "Saved analytical", "Saved human", "Saved transferability", "Saved adaptability",
                "Saved development", List.of("Action one", "Action two", "Action three"), "Saved caution");
        var item = new ALevelEvidenceItem(ALevelEvidenceCategory.DEGREE_PREREQUISITES,
                "Evidence title", "Evidence summary", "Local fixture evidence", Optional.empty(),
                Optional.of(URI.create("https://example.test/evidence")), Optional.empty(),
                Optional.of("United Kingdom"), Optional.empty(), time, Optional.empty(),
                Optional.empty(), Optional.empty(), EvidenceSourceQuality.DEVELOPMENT_FIXTURE,
                "Context only");
        return new ALevelReportAssembler().assemble(new ALevelReportSource(assessment,
                Optional.of(new StoredALevelNarrative(ID, NarrativeStatus.AVAILABLE,
                        Optional.of(narrative), NarrativeSource.LOCAL_FIXTURE, "local-fixture",
                        Optional.empty(), "a-level-narrative-v1", time, Optional.of(time))),
                Optional.of(new ALevelEvidenceBundle(ID, "a-level-evidence-v1",
                        EvidenceSource.LOCAL_FIXTURE, "local-fixture", EvidenceStatus.AVAILABLE,
                        time, List.of(item)))), ALevelReportTier.PAID);
    }
}
