package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.pdf.professional.application.*;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProfessionalReportPdfServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void activeFirstRequestRendersFrozenSnapshotOnceAndPersistsValidatedArtifact() {
        var fixture = fixture(ProfessionalEntitlementStatus.ACTIVE);
        byte[] pdf = "%PDF-1.7\nfixture-pdf".getBytes(StandardCharsets.US_ASCII);
        when(fixture.renderer.render(any())).thenReturn(new RenderedProfessionalReportPdf(pdf));
        when(fixture.pdfStore.saveIfAbsent(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var artifact = fixture.service.getOrCreate(fixture.assessmentId);

        assertThat(artifact.bytes()).isEqualTo(pdf);
        assertThat(artifact.sha256()).hasSize(64);
        assertThat(artifact.purchasedReportVersion()).isEqualTo("professional-report-v2");
        assertThat(artifact.pdfTemplateVersion()).isEqualTo("professional-report-pdf-v3");
        assertThat(artifact.filename()).isEqualTo("ai-career-impact-software-architect-report.pdf");
        verify(fixture.renderer).render(argThat(model -> model.report().equals(fixture.snapshot.report())
                && model.purchasedReportVersion().equals("professional-report-v2")));
        verify(fixture.pdfStore).saveIfAbsent(any());
    }

    @Test
    void existingArtifactReturnsExactBytesWithoutSnapshotLoadOrRenderer() {
        var fixture = fixture(ProfessionalEntitlementStatus.ACTIVE);
        var existing = artifact(fixture, "%PDF-existing".getBytes(StandardCharsets.US_ASCII));
        when(fixture.pdfStore.findByEntitlementId(fixture.entitlementId)).thenReturn(Optional.of(existing));

        assertThat(fixture.service.getOrCreate(fixture.assessmentId)).isSameAs(existing);
        verify(fixture.purchaseStore, never()).findSnapshotByAssessmentId(any());
        verifyNoInteractions(fixture.renderer);
    }

    @Test
    void nonActiveStatusesNeverRenderOrLoadPurchasedSnapshot() {
        for (var status : new ProfessionalEntitlementStatus[]{ProfessionalEntitlementStatus.PENDING,
                ProfessionalEntitlementStatus.FAILED, ProfessionalEntitlementStatus.REFUNDED,
                ProfessionalEntitlementStatus.REVOKED}) {
            var fixture = fixture(status);
            assertThatThrownBy(() -> fixture.service.getOrCreate(fixture.assessmentId))
                    .isInstanceOf(ProfessionalReportPdfAccessDeniedException.class);
            verify(fixture.purchaseStore, never()).findSnapshotByAssessmentId(any());
            verifyNoInteractions(fixture.renderer);
        }
        var purchaseStore = mock(ProfessionalPurchaseStore.class);
        var pdfStore = mock(ProfessionalReportPdfStore.class);
        var renderer = mock(ProfessionalReportPdfRenderer.class);
        var service = service(purchaseStore, pdfStore, renderer, 1024 * 1024);
        assertThatThrownBy(() -> service.getOrCreate(UUID.randomUUID()))
                .isInstanceOf(ProfessionalReportPdfAccessDeniedException.class);
    }

    @Test
    void emptyNonPdfAndOversizedOutputAreRejectedWithoutPersistence() {
        for (byte[] invalid : new byte[][]{new byte[0], "not-pdf".getBytes(StandardCharsets.US_ASCII),
                ("%PDF-" + "x".repeat(1100)).getBytes(StandardCharsets.US_ASCII)}) {
            var fixture = fixture(ProfessionalEntitlementStatus.ACTIVE);
            when(fixture.renderer.render(any())).thenReturn(new RenderedProfessionalReportPdf(invalid));
            var service = service(fixture.purchaseStore, fixture.pdfStore, fixture.renderer, 1024);
            assertThatThrownBy(() -> service.getOrCreate(fixture.assessmentId))
                    .isInstanceOf(ProfessionalReportPdfGenerationException.class);
            verify(fixture.pdfStore, never()).saveIfAbsent(any());
        }
    }

    @Test
    void raceReturnsPersistedWinnerRatherThanLosingCandidate() {
        var fixture = fixture(ProfessionalEntitlementStatus.ACTIVE);
        var winner = artifact(fixture, "%PDF-winner".getBytes(StandardCharsets.US_ASCII));
        when(fixture.renderer.render(any())).thenReturn(new RenderedProfessionalReportPdf(
                "%PDF-loser".getBytes(StandardCharsets.US_ASCII)));
        when(fixture.pdfStore.saveIfAbsent(any())).thenReturn(winner);
        assertThat(fixture.service.getOrCreate(fixture.assessmentId).bytes()).isEqualTo(winner.bytes());
    }

    private Fixture fixture(ProfessionalEntitlementStatus status) {
        var source = ProfessionalReportAssemblerTest.source(true, true, true);
        UUID assessmentId = source.assessment().id(); UUID entitlementId = UUID.randomUUID();
        var report = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        var snapshot = new PurchasedProfessionalReportSnapshot(entitlementId, assessmentId,
                report.reportVersion(), NOW.minusSeconds(60), report);
        var entitlement = new ProfessionalReportEntitlement(entitlementId, assessmentId,
                ProfessionalReportTier.PAID, status, ProfessionalPaymentProvider.LOCAL_FIXTURE,
                "local-checkout", URI.create("http://localhost/checkout"), "local-fixture",
                Optional.empty(), status == ProfessionalEntitlementStatus.ACTIVE
                    ? Optional.of(report.reportVersion()) : Optional.empty(), NOW.minusSeconds(120),
                status == ProfessionalEntitlementStatus.ACTIVE ? Optional.of(NOW.minusSeconds(60)) : Optional.empty());
        var purchaseStore = mock(ProfessionalPurchaseStore.class); var pdfStore = mock(ProfessionalReportPdfStore.class);
        var renderer = mock(ProfessionalReportPdfRenderer.class);
        when(purchaseStore.findEntitlementByAssessmentId(assessmentId)).thenReturn(Optional.of(entitlement));
        when(purchaseStore.findSnapshotByAssessmentId(assessmentId)).thenReturn(Optional.of(snapshot));
        when(pdfStore.findByEntitlementId(entitlementId)).thenReturn(Optional.empty());
        return new Fixture(assessmentId, entitlementId, snapshot, purchaseStore, pdfStore, renderer,
                service(purchaseStore, pdfStore, renderer, 1024 * 1024));
    }

    private ProfessionalReportPdfService service(ProfessionalPurchaseStore purchaseStore,
            ProfessionalReportPdfStore pdfStore, ProfessionalReportPdfRenderer renderer, long max) {
        return new ProfessionalReportPdfService(purchaseStore, pdfStore, renderer,
                new ProfessionalReportPdfModelMapper(), max, CLOCK);
    }

    private PurchasedProfessionalReportPdf artifact(Fixture fixture, byte[] bytes) {
        return new PurchasedProfessionalReportPdf(fixture.entitlementId, fixture.assessmentId,
                "professional-report-v1", "professional-report-pdf-v1", NOW,
                "ai-career-impact-report.pdf", bytes.length, "0".repeat(64), bytes);
    }

    private record Fixture(UUID assessmentId, UUID entitlementId, PurchasedProfessionalReportSnapshot snapshot,
            ProfessionalPurchaseStore purchaseStore, ProfessionalReportPdfStore pdfStore,
            ProfessionalReportPdfRenderer renderer, ProfessionalReportPdfService service) { }
}
