package com.chikere.futureai.pdf.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.payment.university.application.PurchasedUniversityReportSnapshot;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityPaymentProvider;
import com.chikere.futureai.payment.university.application.UniversityPurchaseStore;
import com.chikere.futureai.payment.university.application.UniversityReportEntitlement;
import com.chikere.futureai.pdf.university.openhtml.OpenHtmlUniversityReportPdfRenderer;
import com.chikere.futureai.report.university.application.UniversityReport;
import com.chikere.futureai.report.university.application.UniversityReportAssembler;
import com.chikere.futureai.report.university.application.UniversityReportSource;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UniversityReportPdfServiceTest {

    private static final UUID ASSESSMENT = UUID.fromString("8a9b0c1d-2e3f-4a5b-8c6d-7e8f9a0b1c2d");
    private static final UUID ENTITLEMENT = UUID.fromString("9b0c1d2e-3f4a-4b6c-8d7e-8f9a0b1c2d3e");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    private final UniversityPurchaseStore purchaseStore = mock(UniversityPurchaseStore.class);
    private final UniversityReportPdfStore pdfStore = mock(UniversityReportPdfStore.class);

    @Test
    void withoutAnActiveEntitlementNothingIsLoadedOrRendered() {
        var renderer = mock(UniversityReportPdfRenderer.class);
        for (UniversityEntitlementStatus status : UniversityEntitlementStatus.values()) {
            if (status == UniversityEntitlementStatus.ACTIVE) continue;
            when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                    .thenReturn(Optional.of(entitlement(status)));
            assertThatThrownBy(() -> service(renderer).getOrCreate(ASSESSMENT))
                    .isInstanceOf(UniversityReportPdfAccessDeniedException.class);
        }
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(renderer).getOrCreate(ASSESSMENT))
                .isInstanceOf(UniversityReportPdfAccessDeniedException.class);
        verifyNoInteractions(renderer);
        verify(purchaseStore, never()).findSnapshotByAssessmentId(any());
    }

    @Test
    void rendersOnceFromTheFrozenSnapshotAndPersistsAnImmutableArtifact() {
        var renderer = new OpenHtmlUniversityReportPdfRenderer();
        var snapshot = frozenSnapshot();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.ACTIVE)));
        when(pdfStore.findByEntitlementId(ENTITLEMENT)).thenReturn(Optional.empty());
        when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.of(snapshot));
        when(pdfStore.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PurchasedUniversityReportPdf artifact = service(renderer).getOrCreate(ASSESSMENT);

        assertThat(new String(artifact.bytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(artifact.pdfTemplateVersion()).isEqualTo("university-report-pdf-v1");
        assertThat(artifact.purchasedReportVersion()).isEqualTo("university-report-v1");
        assertThat(artifact.generatedAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(artifact.filename())
                .isEqualTo("ai-career-impact-computer-science-course-report.pdf");
        assertThat(artifact.sha256()).matches("[0-9a-f]{64}");
        assertThat(artifact.fileSize()).isEqualTo(artifact.bytes().length);
        verify(pdfStore, times(1)).saveIfAbsent(any());
    }

    @Test
    void anExistingArtifactIsReturnedWithoutLoadingTheSnapshotOrRenderingAgain() {
        var renderer = mock(UniversityReportPdfRenderer.class);
        byte[] bytes = "%PDF-persisted".getBytes(StandardCharsets.US_ASCII);
        var existing = new PurchasedUniversityReportPdf(ENTITLEMENT, ASSESSMENT, "university-report-v1",
                "university-report-pdf-v1", Instant.parse("2026-09-01T00:00:00Z"), "existing.pdf",
                bytes.length, "a".repeat(64), bytes);
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.ACTIVE)));
        when(pdfStore.findByEntitlementId(ENTITLEMENT)).thenReturn(Optional.of(existing));

        var service = service(renderer);

        assertThat(service.getOrCreate(ASSESSMENT)).isSameAs(existing);
        assertThat(service.getOrCreate(ASSESSMENT).bytes()).isEqualTo(bytes);
        verifyNoInteractions(renderer);
        verify(purchaseStore, never()).findSnapshotByAssessmentId(any());
        verify(pdfStore, never()).saveIfAbsent(any());
    }

    @Test
    void invalidRendererOutputFailsSafelyWithoutPersistingAnything() {
        var renderer = mock(UniversityReportPdfRenderer.class);
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.ACTIVE)));
        when(pdfStore.findByEntitlementId(ENTITLEMENT)).thenReturn(Optional.empty());
        when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.of(frozenSnapshot()));

        when(renderer.render(any())).thenReturn(new RenderedUniversityReportPdf("<html/>".getBytes()));
        assertThatThrownBy(() -> service(renderer).getOrCreate(ASSESSMENT))
                .isInstanceOf(UniversityReportPdfGenerationException.class)
                .hasMessage("renderer output is not a PDF");

        when(renderer.render(any())).thenThrow(new IllegalStateException("renderer blew up"));
        assertThatThrownBy(() -> service(renderer).getOrCreate(ASSESSMENT))
                .isInstanceOf(UniversityReportPdfGenerationException.class)
                .hasMessageNotContaining("blew up");

        verify(pdfStore, never()).saveIfAbsent(any());
    }

    @Test
    void aMismatchedSnapshotIsRefused() {
        var renderer = mock(UniversityReportPdfRenderer.class);
        var mismatched = new PurchasedUniversityReportSnapshot(UUID.randomUUID(), ASSESSMENT,
                "university-report-v1", Instant.parse("2026-10-01T12:00:00Z"), paidReport());
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(UniversityEntitlementStatus.ACTIVE)));
        when(pdfStore.findByEntitlementId(ENTITLEMENT)).thenReturn(Optional.empty());
        when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.of(mismatched));

        assertThatThrownBy(() -> service(renderer).getOrCreate(ASSESSMENT))
                .isInstanceOf(UniversityReportPdfGenerationException.class)
                .hasMessageContaining("does not match entitlement");
        verifyNoInteractions(renderer);
    }

    @Test
    void filenamesAreSanitisedAndBounded() {
        assertThat(UniversityReportPdfService.safeFilename("BSc (Hons) Computer Science!!"))
                .isEqualTo("ai-career-impact-bsc-hons-computer-science-course-report.pdf");
        assertThat(UniversityReportPdfService.safeFilename("../../etc/passwd"))
                .isEqualTo("ai-career-impact-etc-passwd-course-report.pdf");
        assertThat(UniversityReportPdfService.safeFilename("///"))
                .isEqualTo("ai-career-impact-course-course-report.pdf");
        assertThat(UniversityReportPdfService.safeFilename("a".repeat(200)).length())
                .isLessThanOrEqualTo(100);
    }

    private UniversityReportPdfService service(UniversityReportPdfRenderer renderer) {
        return new UniversityReportPdfService(purchaseStore, pdfStore, renderer,
                new UniversityReportPdfModelMapper(), 5_242_880L, CLOCK);
    }

    private UniversityReportEntitlement entitlement(UniversityEntitlementStatus status) {
        return new UniversityReportEntitlement(ENTITLEMENT, ASSESSMENT, UniversityReportTier.PAID, status,
                UniversityPaymentProvider.LOCAL_FIXTURE, "cs_test",
                URI.create("http://localhost:8080/checkout"), "price_configured", Optional.empty(),
                status == UniversityEntitlementStatus.ACTIVE
                        ? Optional.of("university-report-v1") : Optional.empty(),
                Instant.parse("2026-10-01T11:59:00Z"),
                status == UniversityEntitlementStatus.ACTIVE
                        ? Optional.of(Instant.parse("2026-10-01T12:00:00Z")) : Optional.empty());
    }

    private PurchasedUniversityReportSnapshot frozenSnapshot() {
        return new PurchasedUniversityReportSnapshot(ENTITLEMENT, ASSESSMENT, "university-report-v1",
                Instant.parse("2026-10-01T12:00:00Z"), paidReport());
    }

    private UniversityReport paidReport() {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of("Software engineering"),
                Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        var assessment = new StoredUniversityAssessment(ASSESSMENT, Instant.parse("2026-10-01T11:58:00Z"),
                state, judgments, new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        return new UniversityReportAssembler().assemble(new UniversityReportSource(assessment,
                Optional.empty(), Optional.empty()), UniversityReportTier.PAID);
    }
}
