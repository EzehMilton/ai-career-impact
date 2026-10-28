package com.chikere.futureai.assessment.university.persistence;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.assessment.university.application.*;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.evidence.university.application.*;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.*;
import com.chikere.futureai.payment.university.application.*;
import com.chikere.futureai.pdf.university.application.*;
import com.chikere.futureai.report.university.application.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UniversityAssessmentStoreTest {

    @MockitoBean ProfessionalJudgmentProvider unusedProfessionalProvider;
    @MockitoBean ProfessionalNarrativeGenerator unusedProfessionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider unusedUniversityProvider;
    @MockitoBean UniversityNarrativeGenerator unusedUniversityNarrativeGenerator;
    @MockitoBean com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider
            unusedALevelProvider;
    @MockitoBean com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator
            unusedALevelNarrativeGenerator;

    @Autowired UniversityAssessmentStore store;
    @Autowired UniversityNarrativeStore narrativeStore;
    @Autowired UniversityEvidenceStore evidenceStore;
    @Autowired UniversityPurchaseStore purchaseStore;
    @Autowired UniversityReportPdfStore pdfStore;
    @Autowired ProfessionalAssessmentStore professionalStore;
    @Autowired com.chikere.futureai.payment.professional.application.ProfessionalPurchaseStore
            professionalPurchaseStore;
    @Autowired Flyway flyway;

    @Test
    void allMigrationsAreAppliedAndUniversityTablesAreSeparateFromProfessionalOnes() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("23");
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12",
                        "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23");
    }

    @Test
    void migratedPostgresqlRoundTripPreservesTheCompleteUniversitySnapshot() {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a substantial final-year project.",
                List.of("Algorithms", "Databases"), List.of("Programming", "Problem solving"),
                List.of("Software engineering"), Optional.of("A UK university"),
                Optional.of("United Kingdom"));
        var fixture = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state);
        // Exercise every optional judgment field, including a probability distribution.
        var modified = new ArrayList<>(fixture.judgments());
        var score = modified.get(1);
        modified.set(1, new DimensionJudgment(score.dimensionId(), score.name(), score.numericValue(),
                Optional.of("core and sustained"), Optional.of(AssessmentConfidence.MODERATE),
                OptionalDouble.of(0.73), Map.of("0", 0.2, "1", 0.3, "2", 0.5)));
        var judgments = new AssessmentJudgmentSet(fixture.journey(), fixture.schemaVersion(), modified);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        var snapshot = new StoredUniversityAssessment(UUID.randomUUID(),
                Instant.parse("2026-10-01T12:00:00Z"), state, judgments,
                new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.of("jev-latest"));

        store.save(snapshot);
        var loaded = store.findById(snapshot.id()).orElseThrow();

        assertThat(loaded).isEqualTo(snapshot);
        assertThat(loaded.judgments().judgments()).hasSize(10);
        assertThat(loaded.judgments().judgments().getFirst().confidenceValue()).isEmpty();
        assertThat(loaded.judgments().judgments().get(1).probabilityDistribution()).containsEntry("1", 0.3);
        assertThat(loaded.result().judgmentSchemaVersion()).isEqualTo("university-v1");
        assertThat(loaded.result().decisionPolicyVersion()).isEqualTo("university-decision-v1");
        assertThat(loaded.result().source()).isEqualTo(AssessmentSource.LOCAL_FIXTURE);
        assertThat(loaded.modelIdentifier()).contains("jev-latest");
        assertThat(loaded.result().decision().measures()).hasSize(5);
        assertThat(loaded.result().decision().topStrengths()).isNotEmpty();
        assertThat(loaded.result().decision().topDevelopmentOpportunities()).isNotEmpty();
        assertThat(loaded.state().institution()).contains("A UK university");
        assertThat(store.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void aUniversityDecisionV2AssessmentRoundTripsItsCourseProfileWithNoV1Measures() {
        var v2 = com.chikere.futureai.coursemodule.university.analysis.ModuleAnalysisTestData
                .courseProfileAssessment(UUID.randomUUID(), 3);

        store.save(v2);
        var loaded = store.findById(v2.id()).orElseThrow();

        assertThat(loaded).isEqualTo(v2);
        assertThat(loaded.result().usesCourseProfile()).isTrue();
        assertThat(loaded.result().decisionPolicyVersion()).isEqualTo("university-decision-v2");
        assertThat(loaded.result().profile()).isEqualTo(v2.result().profile());
        assertThat(loaded.result().profile().measures()).hasSize(3);
        assertThat(loaded.result().legacyDecision()).isEmpty();
        assertThatThrownBy(() -> loaded.result().decision()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void roundTripPreservesNarrativeLifecycleContentAndProvenance() {
        UUID id = savedAssessment("history");
        var narrative = new UniversityNarrative("Measured reading", "Saved summary", "Saved meaning",
                "Saved readiness", "Saved strengths", "Saved development", "Saved complementarity",
                "Saved transferability", "Saved adaptability",
                List.of("Review the modules", "Build AI-tool skill", "Seek applied experience"),
                "Saved caution");
        Instant attempted = Instant.parse("2026-10-01T12:01:00Z");
        Instant generated = Instant.parse("2026-10-01T12:01:05Z");
        var stored = new StoredUniversityNarrative(id, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                NarrativeSource.LOCAL_FIXTURE, "local-fixture", Optional.of("configured-alias"),
                "university-narrative-v1", attempted, Optional.of(generated));

        narrativeStore.save(stored);

        assertThat(narrativeStore.findByAssessmentId(id)).contains(stored);
        assertThat(narrativeStore.findByAssessmentId(id).orElseThrow().narrative().orElseThrow()
                .practicalActions()).containsExactlyElementsOf(narrative.practicalActions());
    }

    @Test
    void roundTripPreservesEvidenceLifecycleAndAllItemMetadata() {
        UUID id = savedAssessment("economics");
        Instant retrieved = Instant.parse("2026-10-01T12:02:00Z");
        var item = new UniversityEvidenceItem(UniversityEvidenceCategory.GRADUATE_OUTCOMES,
                "Published outcome context", "A factual saved summary.", "Official dataset",
                Optional.of("table-7/cs"), Optional.of(URI.create("https://example.gov.uk/data?a=1&b=2")),
                Optional.of("Official publisher"), Optional.of("United Kingdom"),
                Optional.of(LocalDate.parse("2026-04-01")), retrieved,
                Optional.of("Graduates in further study"), Optional.of(new BigDecimal("1234.50")),
                Optional.of("graduates"), EvidenceSourceQuality.OFFICIAL,
                "Related course context, separate from scoring.");
        var bundle = new UniversityEvidenceBundle(id, "university-evidence-v1", EvidenceSource.LIVE,
                "official-provider", EvidenceStatus.PARTIAL, retrieved, List.of(item));

        evidenceStore.save(bundle);

        assertThat(evidenceStore.findByAssessmentId(id)).contains(bundle);
        assertThat(evidenceStore.findByAssessmentId(id).orElseThrow().items().getFirst().sourceUrl())
                .contains(URI.create("https://example.gov.uk/data?a=1&b=2"));
    }

    @Test
    void anExpiredCheckoutIsReplacedInPlaceAndOnlyTheReplacementSessionCanThenActivate() {
        UUID assessmentId = savedAssessment("nursing");
        var report = new UniversityReportAssembler().assemble(new UniversityReportSource(
                store.findById(assessmentId).orElseThrow(), Optional.empty(), Optional.empty()),
                UniversityReportTier.PAID);
        UUID entitlementId = UUID.randomUUID();
        purchaseStore.savePending(new UniversityReportEntitlement(entitlementId, assessmentId, UniversityReportTier.PAID,
                UniversityEntitlementStatus.PENDING, UniversityPaymentProvider.STRIPE, "cs_expired_university",
                URI.create("https://checkout.stripe.test/expired"), "price_configured", Optional.empty(),
                Optional.empty(), Instant.parse("2026-10-01T12:01:00Z"), Optional.empty()));
        var replacement = new UniversityCheckoutSession("cs_retry_university", URI.create("https://checkout.stripe.test/retry"),
                UniversityPaymentProvider.STRIPE, Optional.empty());
        var other = new UniversityCheckoutSession("cs_other_university", URI.create("https://checkout.stripe.test/other"),
                UniversityPaymentProvider.STRIPE, Optional.empty());
        Instant retriedAt = Instant.parse("2026-10-02T09:00:00Z");

        purchaseStore.replaceExpiredCheckout(entitlementId, "cs_expired_university", replacement, "price_configured", retriedAt);
        // A concurrent retry that shared the idempotency key received the same session: harmless.
        purchaseStore.replaceExpiredCheckout(entitlementId, "cs_expired_university", replacement, "price_configured", retriedAt);
        // A retry that still believes the expired session is current must not overwrite the replacement.
        assertThatThrownBy(() -> purchaseStore.replaceExpiredCheckout(entitlementId, "cs_expired_university", other,
                "price_configured", retriedAt)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("the pending checkout changed before it could be replaced");

        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId)).get().satisfies(retried -> {
            assertThat(retried.entitlementId()).isEqualTo(entitlementId);
            assertThat(retried.status()).isEqualTo(UniversityEntitlementStatus.PENDING);
            assertThat(retried.providerCheckoutSessionId()).isEqualTo("cs_retry_university");
            assertThat(retried.checkoutUrl()).isEqualTo(URI.create("https://checkout.stripe.test/retry"));
            assertThat(retried.createdAt()).isEqualTo(retriedAt);
        });

        var snapshot = new PurchasedUniversityReportSnapshot(entitlementId, assessmentId, report.reportVersion(),
                Instant.parse("2026-10-02T09:05:00Z"), report);
        var expiredEvent = new UniversityPaymentEvent("evt_expired_university", UniversityPaymentProvider.STRIPE, "cs_expired_university",
                entitlementId, assessmentId, Optional.of("pi_expired"), Instant.parse("2026-10-02T09:04:00Z"));
        var retriedEvent = new UniversityPaymentEvent("evt_retry_university", UniversityPaymentProvider.STRIPE, "cs_retry_university",
                entitlementId, assessmentId, Optional.of("pi_retry"), Instant.parse("2026-10-02T09:04:00Z"));
        assertThatThrownBy(() -> purchaseStore.confirmAndFreeze(expiredEvent, snapshot))
                .isInstanceOf(InvalidPaymentNotificationException.class);
        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId).orElseThrow().status())
                .isEqualTo(UniversityEntitlementStatus.PENDING);

        assertThat(purchaseStore.confirmAndFreeze(retriedEvent, snapshot))
                .isEqualTo(UniversityPurchaseStore.PurchaseConfirmationResult.ACTIVATED);
        assertThat(purchaseStore.confirmAndFreeze(retriedEvent, snapshot))
                .isEqualTo(UniversityPurchaseStore.PurchaseConfirmationResult.ALREADY_PROCESSED);
        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId).orElseThrow().status())
                .isEqualTo(UniversityEntitlementStatus.ACTIVE);
        // An active purchase can never be pointed at another checkout.
        assertThatThrownBy(() -> purchaseStore.replaceExpiredCheckout(entitlementId, "cs_retry_university", other,
                "price_configured", retriedAt)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("the pending checkout changed before it could be replaced");
    }

    @Test
    void roundTripFreezesTheEntitlementPurchasedReportProcessedEventAndImmutablePdf() {
        UUID assessmentId = savedAssessment("nursing");
        UUID entitlementId = UUID.randomUUID();
        var report = new UniversityReportAssembler().assemble(new UniversityReportSource(
                store.findById(assessmentId).orElseThrow(), Optional.empty(), Optional.empty()),
                UniversityReportTier.PAID);
        var pending = new UniversityReportEntitlement(entitlementId, assessmentId,
                UniversityReportTier.PAID, UniversityEntitlementStatus.PENDING,
                UniversityPaymentProvider.STRIPE, "cs_test_university",
                URI.create("https://checkout.stripe.test/university"), "price_university",
                Optional.empty(), Optional.empty(), Instant.parse("2026-10-01T12:01:00Z"),
                Optional.empty());
        purchaseStore.savePending(pending);
        var event = new UniversityPaymentEvent("evt_university", UniversityPaymentProvider.STRIPE,
                "cs_test_university", entitlementId, assessmentId, Optional.of("pi_university"),
                Instant.parse("2026-10-01T12:02:00Z"));
        var snapshot = new PurchasedUniversityReportSnapshot(entitlementId, assessmentId,
                report.reportVersion(), Instant.parse("2026-10-01T12:02:01Z"), report);

        assertThat(purchaseStore.findSnapshotByAssessmentId(assessmentId)).isEmpty();
        assertThat(purchaseStore.confirmAndFreeze(event, snapshot))
                .isEqualTo(UniversityPurchaseStore.PurchaseConfirmationResult.ACTIVATED);

        assertThat(purchaseStore.findSnapshotByAssessmentId(assessmentId)).contains(snapshot);
        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId)).get().satisfies(active -> {
            assertThat(active.status()).isEqualTo(UniversityEntitlementStatus.ACTIVE);
            assertThat(active.providerPaymentReference()).contains("pi_university");
            assertThat(active.purchasedReportVersion()).contains("university-report-v1");
            assertThat(active.confirmedAt()).contains(Instant.parse("2026-10-01T12:02:01Z"));
        });
        assertThat(purchaseStore.isEventProcessed(UniversityPaymentProvider.STRIPE, "evt_university"))
                .isTrue();
        // A redelivered Stripe event is idempotent and never rebuilds the purchased report.
        assertThat(purchaseStore.confirmAndFreeze(event, snapshot))
                .isEqualTo(UniversityPurchaseStore.PurchaseConfirmationResult.ALREADY_PROCESSED);
        assertThat(purchaseStore.findSnapshotByAssessmentId(assessmentId)).contains(snapshot);

        byte[] pdfBytes = "%PDF-1.7 persisted university fixture".getBytes(StandardCharsets.US_ASCII);
        var pdf = new PurchasedUniversityReportPdf(entitlementId, assessmentId, report.reportVersion(),
                UniversityReportPdfVersions.PDF_TEMPLATE, Instant.parse("2026-10-01T12:03:00Z"),
                "ai-career-impact-nursing-course-report.pdf", pdfBytes.length, "c".repeat(64), pdfBytes);
        var storedPdf = pdfStore.saveIfAbsent(pdf);

        assertThat(storedPdf).usingRecursiveComparison().isEqualTo(pdf);
        assertThat(pdfStore.findByEntitlementId(entitlementId)).get().satisfies(reloaded -> {
            assertThat(reloaded.bytes()).isEqualTo(pdfBytes);
            assertThat(reloaded.sha256()).isEqualTo("c".repeat(64));
            assertThat(reloaded.purchasedReportVersion()).isEqualTo("university-report-v1");
            assertThat(reloaded.pdfTemplateVersion()).isEqualTo("university-report-pdf-v1");
        });
        // The artifact is immutable: a losing concurrent candidate never overwrites it.
        byte[] losing = "%PDF-different-candidate".getBytes(StandardCharsets.US_ASCII);
        assertThat(pdfStore.saveIfAbsent(new PurchasedUniversityReportPdf(entitlementId, assessmentId,
                report.reportVersion(), UniversityReportPdfVersions.PDF_TEMPLATE,
                Instant.parse("2026-10-01T12:04:00Z"), "different.pdf", losing.length,
                "d".repeat(64), losing)).bytes()).isEqualTo(pdfBytes);
    }

    @Test
    void aProfessionalEntitlementGrantsNoUniversityAccessAndViceVersa() {
        UUID universityId = savedAssessment("graphic_design");
        UUID professionalId = savedProfessionalAssessment();

        // A professional purchase exists for the professional assessment only.
        var professionalEntitlement = new com.chikere.futureai.payment.professional.application
                .ProfessionalReportEntitlement(UUID.randomUUID(), professionalId,
                com.chikere.futureai.report.professional.application.ProfessionalReportTier.PAID,
                com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus.PENDING,
                com.chikere.futureai.payment.professional.application.ProfessionalPaymentProvider.STRIPE,
                "cs_test_professional_isolation", URI.create("https://checkout.stripe.test/professional"),
                "price_professional", Optional.empty(), Optional.empty(),
                Instant.parse("2026-10-01T12:00:00Z"), Optional.empty());
        professionalPurchaseStore.savePending(professionalEntitlement);

        assertThat(professionalPurchaseStore.findEntitlementByAssessmentId(professionalId)).isPresent();
        assertThat(purchaseStore.findEntitlementByAssessmentId(professionalId)).isEmpty();
        assertThat(purchaseStore.findEntitlementByAssessmentId(universityId)).isEmpty();
        assertThat(purchaseStore.findSnapshotByAssessmentId(professionalId)).isEmpty();
        // Entitlement identifiers live in separate tables and never collide across journeys.
        assertThat(purchaseStore.findEntitlementById(professionalEntitlement.entitlementId())).isEmpty();
    }

    @Test
    void aUniversityEntitlementCannotBeCreatedForAProfessionalAssessmentId() {
        UUID professionalId = savedProfessionalAssessment();
        var entitlement = new UniversityReportEntitlement(UUID.randomUUID(), professionalId,
                UniversityReportTier.PAID, UniversityEntitlementStatus.PENDING,
                UniversityPaymentProvider.STRIPE, "cs_test_wrong_journey",
                URI.create("https://checkout.stripe.test/wrong"), "price_university",
                Optional.empty(), Optional.empty(), Instant.parse("2026-10-01T12:00:00Z"),
                Optional.empty());

        // The foreign key points at university_assessment, so a professional UUID cannot be used.
        assertThatThrownBy(() -> purchaseStore.savePending(entitlement))
                .isInstanceOf(RuntimeException.class);
    }

    private UUID savedAssessment(String fixtureId) {
        var state = new UniversityAssessmentState("A course",
                "A course description with enough detail to assess properly.",
                List.of("A module"), List.of("A skill"), List.of("An area"),
                Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureUniversityJudgmentProvider(fixtureId).judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        UUID id = UUID.randomUUID();
        store.save(new StoredUniversityAssessment(id, Instant.parse("2026-10-01T12:00:00Z"), state,
                judgments, new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty()));
        return id;
    }

    private UUID savedProfessionalAssessment() {
        var state = new ProfessionalAssessmentState("Software Architect", "Designs complex systems.",
                List.of("Design platforms"), List.of("Architecture"), OptionalInt.of(12));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect")
                .judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        UUID id = UUID.randomUUID();
        professionalStore.save(new StoredProfessionalAssessment(id,
                Instant.parse("2026-10-01T12:00:00Z"), state, judgments,
                new ProfessionalAssessmentResult(decision,
                        com.chikere.futureai.assessment.professional.application.AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty()));
        return id;
    }
}
