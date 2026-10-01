package com.chikere.futureai.pdf.university.openhtml;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.evidence.university.application.EvidenceSource;
import com.chikere.futureai.evidence.university.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.university.application.EvidenceStatus;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceBundle;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceCategory;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceItem;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfModel;
import com.chikere.futureai.narrative.university.application.NarrativeSource;
import com.chikere.futureai.narrative.university.application.NarrativeStatus;
import com.chikere.futureai.narrative.university.application.StoredUniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrative;
import com.chikere.futureai.report.university.application.UniversityReport;
import com.chikere.futureai.report.university.application.UniversityReportAssembler;
import com.chikere.futureai.report.university.application.UniversityReportSource;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHtmlUniversityReportPdfRendererTest {

    private static final UUID ID = UUID.fromString("0c1d2e3f-4a5b-4c7d-8e9f-0a1b2c3d4e5f");
    private final OpenHtmlUniversityReportPdfRenderer renderer = new OpenHtmlUniversityReportPdfRenderer();

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
    void theRenderedDocumentCarriesBothVersionsAndTheUniversityMeasures() {
        String html = renderer.html(model());

        assertThat(html).contains("university-report-v1", "university-report-pdf-v1",
                "Future Readiness", "AI Complementarity", "Human Differentiation",
                "Skill Transferability", "Career Adaptability",
                "Development fixture report", "not a guarantee of employment",
                "different constructs on different scales");
        assertThat(html).doesNotContain("AI Exposure describes", "Role Adaptability", "Human Advantage");
    }

    private UniversityReportPdfModel model() {
        return new UniversityReportPdfModel("university-report-pdf-v1", "university-report-v1",
                Instant.parse("2026-10-01T12:00:00Z"), paidReport());
    }

    private UniversityReport paidReport() {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of("Software engineering"),
                Optional.of("A UK university"), Optional.of("United Kingdom"));
        var judgments = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        var assessment = new StoredUniversityAssessment(ID, Instant.parse("2026-10-01T11:58:00Z"), state,
                judgments, new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        Instant time = Instant.parse("2026-10-01T12:02:00Z");
        var narrative = new UniversityNarrative("A practical reading", "<script>injected</script>",
                "Saved meaning", "Saved readiness", "Saved strengths", "Saved development",
                "Saved complementarity", "Saved transferability", "Saved adaptability",
                List.of("Action one", "Action two", "Action three"), "Saved caution");
        var item = new UniversityEvidenceItem(UniversityEvidenceCategory.GRADUATE_OUTCOMES,
                "Evidence title", "Evidence summary", "Local fixture evidence", Optional.empty(),
                Optional.of(URI.create("https://example.test/evidence")), Optional.empty(),
                Optional.of("United Kingdom"), Optional.empty(), time, Optional.of("A metric"),
                Optional.of(new BigDecimal("1200")), Optional.of("graduates"),
                EvidenceSourceQuality.DEVELOPMENT_FIXTURE, "Context only");
        return new UniversityReportAssembler().assemble(new UniversityReportSource(assessment,
                Optional.of(new StoredUniversityNarrative(ID, NarrativeStatus.AVAILABLE,
                        Optional.of(narrative), NarrativeSource.LOCAL_FIXTURE, "local-fixture",
                        Optional.empty(), "university-narrative-v1", time, Optional.of(time))),
                Optional.of(new UniversityEvidenceBundle(ID, "university-evidence-v1",
                        EvidenceSource.LOCAL_FIXTURE, "local-fixture", EvidenceStatus.AVAILABLE,
                        time, List.of(item)))), UniversityReportTier.PAID);
    }
}
