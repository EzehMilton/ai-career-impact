package com.chikere.futureai.pdf.alevel.application;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.payment.alevel.application.ALevelEntitlementStatus;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentProvider;
import com.chikere.futureai.payment.alevel.application.ALevelPurchaseStore;
import com.chikere.futureai.payment.alevel.application.ALevelReportEntitlement;
import com.chikere.futureai.payment.alevel.application.PurchasedALevelReportSnapshot;
import com.chikere.futureai.pdf.alevel.openhtml.OpenHtmlALevelReportPdfRenderer;
import com.chikere.futureai.report.alevel.application.ALevelReport;
import com.chikere.futureai.report.alevel.application.ALevelReportAssembler;
import com.chikere.futureai.report.alevel.application.ALevelReportSource;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

class ALevelReportPdfServiceTest {

    private static final UUID ASSESSMENT = UUID.fromString("2f3a4b5c-6d7e-4f8a-8b9c-0d1e2f3a4b5c");
    private static final UUID ENTITLEMENT = UUID.fromString("3a4b5c6d-7e8f-4a9b-8c0d-1e2f3a4b5c6d");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-01T12:00:00Z"), ZoneOffset.UTC);

    private final ALevelPurchaseStore purchaseStore = mock(ALevelPurchaseStore.class);
    private final ALevelReportPdfStore pdfStore = mock(ALevelReportPdfStore.class);

    @Test
    void withoutAnActiveEntitlementNothingIsLoadedOrRendered() {
        var renderer = mock(ALevelReportPdfRenderer.class);
        for (ALevelEntitlementStatus status : ALevelEntitlementStatus.values()) {
            if (status == ALevelEntitlementStatus.ACTIVE) continue;
            when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                    .thenReturn(Optional.of(entitlement(status)));
            assertThatThrownBy(() -> service(renderer).getOrCreate(ASSESSMENT))
                    .isInstanceOf(ALevelReportPdfAccessDeniedException.class);
        }
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(renderer).getOrCreate(ASSESSMENT))
                .isInstanceOf(ALevelReportPdfAccessDeniedException.class);
        verifyNoInteractions(renderer);
        verify(purchaseStore, never()).findSnapshotByAssessmentId(any());
    }

    @Test
    void rendersOnceFromTheFrozenSnapshotAndPersistsAnImmutableArtifact() {
        var renderer = new OpenHtmlALevelReportPdfRenderer();
        var snapshot = frozenSnapshot();
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(ALevelEntitlementStatus.ACTIVE)));
        when(pdfStore.findByEntitlementId(ENTITLEMENT)).thenReturn(Optional.empty());
        when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.of(snapshot));
        when(pdfStore.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PurchasedALevelReportPdf artifact = service(renderer).getOrCreate(ASSESSMENT);

        assertThat(new String(artifact.bytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(artifact.pdfTemplateVersion()).isEqualTo("a-level-report-pdf-v1");
        assertThat(artifact.purchasedReportVersion()).isEqualTo("a-level-report-v1");
        assertThat(artifact.generatedAt()).isEqualTo(Instant.parse("2026-10-01T12:00:00Z"));
        assertThat(artifact.filename())
                .isEqualTo("ai-career-impact-mathematics-economics-geography-a-level-report.pdf");
        assertThat(artifact.sha256()).matches("[0-9a-f]{64}");
        assertThat(artifact.fileSize()).isEqualTo(artifact.bytes().length);
        verify(pdfStore, times(1)).saveIfAbsent(any());
    }

    @Test
    void anExistingArtifactIsReturnedWithoutLoadingTheSnapshotOrRenderingAgain() {
        var renderer = mock(ALevelReportPdfRenderer.class);
        byte[] bytes = "%PDF-persisted".getBytes(StandardCharsets.US_ASCII);
        var existing = new PurchasedALevelReportPdf(ENTITLEMENT, ASSESSMENT, "a-level-report-v1",
                "a-level-report-pdf-v1", Instant.parse("2026-09-01T00:00:00Z"), "existing.pdf",
                bytes.length, "a".repeat(64), bytes);
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(ALevelEntitlementStatus.ACTIVE)));
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
        var renderer = mock(ALevelReportPdfRenderer.class);
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(ALevelEntitlementStatus.ACTIVE)));
        when(pdfStore.findByEntitlementId(ENTITLEMENT)).thenReturn(Optional.empty());
        when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.of(frozenSnapshot()));

        when(renderer.render(any())).thenReturn(new RenderedALevelReportPdf("<html/>".getBytes()));
        assertThatThrownBy(() -> service(renderer).getOrCreate(ASSESSMENT))
                .isInstanceOf(ALevelReportPdfGenerationException.class)
                .hasMessage("renderer output is not a PDF");

        when(renderer.render(any())).thenThrow(new IllegalStateException("renderer blew up"));
        assertThatThrownBy(() -> service(renderer).getOrCreate(ASSESSMENT))
                .isInstanceOf(ALevelReportPdfGenerationException.class)
                .hasMessageNotContaining("blew up");

        verify(pdfStore, never()).saveIfAbsent(any());
    }

    @Test
    void aMismatchedSnapshotIsRefused() {
        var renderer = mock(ALevelReportPdfRenderer.class);
        var mismatched = new PurchasedALevelReportSnapshot(UUID.randomUUID(), ASSESSMENT,
                "a-level-report-v1", Instant.parse("2026-10-01T12:00:00Z"), paidReport());
        when(purchaseStore.findEntitlementByAssessmentId(ASSESSMENT))
                .thenReturn(Optional.of(entitlement(ALevelEntitlementStatus.ACTIVE)));
        when(pdfStore.findByEntitlementId(ENTITLEMENT)).thenReturn(Optional.empty());
        when(purchaseStore.findSnapshotByAssessmentId(ASSESSMENT)).thenReturn(Optional.of(mismatched));

        assertThatThrownBy(() -> service(renderer).getOrCreate(ASSESSMENT))
                .isInstanceOf(ALevelReportPdfGenerationException.class)
                .hasMessageContaining("does not match entitlement");
        verifyNoInteractions(renderer);
    }

    @Test
    void filenamesAreSanitisedAndBounded() {
        assertThat(ALevelReportPdfService.safeFilename(List.of("Mathematics", "Further Mathematics")))
                .isEqualTo("ai-career-impact-mathematics-further-mathematics-a-level-report.pdf");
        assertThat(ALevelReportPdfService.safeFilename(List.of("../../etc", "passwd")))
                .isEqualTo("ai-career-impact-etc-passwd-a-level-report.pdf");
        assertThat(ALevelReportPdfService.safeFilename(List.of("///", "***")))
                .isEqualTo("ai-career-impact-subjects-a-level-report.pdf");
        assertThat(ALevelReportPdfService.safeFilename(List.of("a".repeat(200))).length())
                .isLessThanOrEqualTo(110);
    }

    private ALevelReportPdfService service(ALevelReportPdfRenderer renderer) {
        return new ALevelReportPdfService(purchaseStore, pdfStore, renderer,
                new ALevelReportPdfModelMapper(), 5_242_880L, CLOCK);
    }

    private ALevelReportEntitlement entitlement(ALevelEntitlementStatus status) {
        return new ALevelReportEntitlement(ENTITLEMENT, ASSESSMENT, ALevelReportTier.PAID, status,
                ALevelPaymentProvider.LOCAL_FIXTURE, "cs_test",
                URI.create("http://localhost:8080/checkout"), "price_a_level", Optional.empty(),
                status == ALevelEntitlementStatus.ACTIVE
                        ? Optional.of("a-level-report-v1") : Optional.empty(),
                Instant.parse("2026-10-01T11:59:00Z"),
                status == ALevelEntitlementStatus.ACTIVE
                        ? Optional.of(Instant.parse("2026-10-01T12:00:00Z")) : Optional.empty());
    }

    private PurchasedALevelReportSnapshot frozenSnapshot() {
        return new PurchasedALevelReportSnapshot(ENTITLEMENT, ASSESSMENT, "a-level-report-v1",
                Instant.parse("2026-10-01T12:00:00Z"), paidReport());
    }

    private ALevelReport paidReport() {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of(), List.of(), List.of("Economics"), List.of(), Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureALevelJudgmentProvider("maths_economics_geography").judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        var assessment = new StoredALevelAssessment(ASSESSMENT, Instant.parse("2026-10-01T11:58:00Z"),
                state, judgments, new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        return new ALevelReportAssembler().assemble(new ALevelReportSource(assessment,
                Optional.empty(), Optional.empty()), ALevelReportTier.PAID);
    }
}
