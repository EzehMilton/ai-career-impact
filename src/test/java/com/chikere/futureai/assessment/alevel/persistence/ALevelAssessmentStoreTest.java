package com.chikere.futureai.assessment.alevel.persistence;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.alevel.application.*;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.evidence.alevel.application.*;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.*;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator;
import com.chikere.futureai.payment.alevel.application.*;
import com.chikere.futureai.pdf.alevel.application.*;
import com.chikere.futureai.report.alevel.application.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
class ALevelAssessmentStoreTest {

    @MockitoBean ProfessionalJudgmentProvider unusedProfessionalProvider;
    @MockitoBean ProfessionalNarrativeGenerator unusedProfessionalNarrativeGenerator;
    @MockitoBean UniversityJudgmentProvider unusedUniversityProvider;
    @MockitoBean UniversityNarrativeGenerator unusedUniversityNarrativeGenerator;
    @MockitoBean ALevelJudgmentProvider unusedALevelProvider;
    @MockitoBean ALevelNarrativeGenerator unusedALevelNarrativeGenerator;

    @Autowired ALevelAssessmentStore store;
    @Autowired ALevelNarrativeStore narrativeStore;
    @Autowired ALevelEvidenceStore evidenceStore;
    @Autowired ALevelPurchaseStore purchaseStore;
    @Autowired ALevelReportPdfStore pdfStore;
    @Autowired ProfessionalAssessmentStore professionalStore;
    @Autowired UniversityAssessmentStore universityStore;
    @Autowired com.chikere.futureai.payment.professional.application.ProfessionalPurchaseStore
            professionalPurchaseStore;
    @Autowired com.chikere.futureai.payment.university.application.UniversityPurchaseStore
            universityPurchaseStore;
    @Autowired Flyway flyway;

    @Test
    void allMigrationsAreAppliedAndALevelTablesAreSeparate() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("21");
        assertThat(flyway.info().applied()).extracting(info -> info.getVersion().getVersion())
                .containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12",
                        "13", "14", "15", "16", "17", "18", "19", "20", "21");
    }

    @Test
    void migratedPostgresqlRoundTripPreservesTheCompleteALevelSnapshot() {
        var state = new ALevelAssessmentState(List.of("maths", "Economics", "geog"),
                Map.of("Mathematics", "A", "Economics", "B"), List.of("Problem solving", "Essays"),
                List.of("Economics", "Philosophy, Politics and Economics"), List.of("Data analysis"),
                Optional.of("United Kingdom"), Optional.of("Considering a gap year."));
        var fixture = new LocalFixtureALevelJudgmentProvider("maths_economics_geography").judge(state);
        // Exercise every optional judgment field, including a probability distribution.
        var modified = new ArrayList<>(fixture.judgments());
        var score = modified.get(1);
        modified.set(1, new DimensionJudgment(score.dimensionId(), score.name(), score.numericValue(),
                Optional.of("substantial"), Optional.of(AssessmentConfidence.MODERATE),
                OptionalDouble.of(0.73), Map.of("0", 0.2, "1", 0.3, "2", 0.5)));
        var judgments = new AssessmentJudgmentSet(fixture.journey(), fixture.schemaVersion(), modified);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        var snapshot = new StoredALevelAssessment(UUID.randomUUID(),
                Instant.parse("2026-10-01T12:00:00Z"), state, judgments,
                new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.of("jev-latest"));

        store.save(snapshot);
        var loaded = store.findById(snapshot.id()).orElseThrow();

        assertThat(loaded).isEqualTo(snapshot);
        assertThat(loaded.state().selectedSubjects())
                .containsExactly("Mathematics", "Economics", "Geography");
        // A jsonb array preserves order, so the subject list above is order-asserted. A jsonb object
        // does not, so grades are asserted by content; display order follows the subject list.
        assertThat(loaded.state().predictedGrades())
                .containsOnly(Map.entry("Mathematics", "A"), Map.entry("Economics", "B"));
        assertThat(loaded.state().preferredDegreeAreas())
                .containsExactly("Economics", "Philosophy, Politics and Economics");
        assertThat(loaded.state().additionalContext()).contains("Considering a gap year.");
        assertThat(loaded.judgments().judgments()).hasSize(11);
        assertThat(loaded.judgments().judgments().getFirst().confidenceValue()).isEmpty();
        assertThat(loaded.judgments().judgments().get(1).probabilityDistribution()).containsEntry("1", 0.3);
        assertThat(loaded.result().judgmentSchemaVersion()).isEqualTo("a-level-v1");
        assertThat(loaded.result().decisionPolicyVersion()).isEqualTo("a-level-decision-v1");
        assertThat(loaded.result().source()).isEqualTo(AssessmentSource.LOCAL_FIXTURE);
        assertThat(loaded.modelIdentifier()).contains("jev-latest");
        assertThat(loaded.result().decision().measures()).hasSize(6);
        assertThat(loaded.result().decision().topStrengths()).isNotEmpty();
        assertThat(loaded.result().decision().topDevelopmentOpportunities()).isNotEmpty();
        assertThat(store.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void roundTripPreservesNarrativeLifecycleContentAndProvenance() {
        UUID id = savedAssessment("english_history_politics");
        var narrative = new ALevelNarrative("Measured reading", "Saved summary", "Saved meaning",
                "Saved optionality", "Saved strengths", "Saved pathways", "Saved analytical",
                "Saved human", "Saved transferability", "Saved adaptability", "Saved development",
                List.of("Review the modules", "Build AI-tool skill", "Research course requirements"),
                "Saved caution");
        Instant attempted = Instant.parse("2026-10-01T12:01:00Z");
        Instant generated = Instant.parse("2026-10-01T12:01:05Z");
        var stored = new StoredALevelNarrative(id, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                NarrativeSource.LOCAL_FIXTURE, "local-fixture", Optional.of("configured-alias"),
                "a-level-narrative-v1", attempted, Optional.of(generated));

        narrativeStore.save(stored);

        assertThat(narrativeStore.findByAssessmentId(id)).contains(stored);
        assertThat(narrativeStore.findByAssessmentId(id).orElseThrow().narrative().orElseThrow()
                .practicalActions()).containsExactlyElementsOf(narrative.practicalActions());
    }

    @Test
    void roundTripPreservesEvidenceLifecycleAndAllItemMetadata() {
        UUID id = savedAssessment("biology_chemistry_mathematics");
        Instant retrieved = Instant.parse("2026-10-01T12:02:00Z");
        var item = new ALevelEvidenceItem(ALevelEvidenceCategory.DEGREE_PREREQUISITES,
                "Published subject requirement", "A factual saved summary with its own source.",
                "Official admissions source", Optional.of("course/med-2026"),
                Optional.of(URI.create("https://example.ac.uk/entry?course=1&year=2026")),
                Optional.of("Official publisher"), Optional.of("United Kingdom"),
                Optional.of(LocalDate.parse("2026-04-01")), retrieved,
                Optional.of("Courses listing this requirement"), Optional.of(new java.math.BigDecimal("31")),
                Optional.of("courses"), EvidenceSourceQuality.OFFICIAL,
                "Factual context only; it does not change the assessment.");
        var bundle = new ALevelEvidenceBundle(id, "a-level-evidence-v1", EvidenceSource.LIVE,
                "official-provider", EvidenceStatus.PARTIAL, retrieved, List.of(item));

        evidenceStore.save(bundle);

        assertThat(evidenceStore.findByAssessmentId(id)).contains(bundle);
        assertThat(evidenceStore.findByAssessmentId(id).orElseThrow().items().getFirst().sourceUrl())
                .contains(URI.create("https://example.ac.uk/entry?course=1&year=2026"));
    }

    @Test
    void anExpiredCheckoutIsReplacedInPlaceAndOnlyTheReplacementSessionCanThenActivate() {
        UUID assessmentId = savedAssessment("mixed_balanced");
        var report = new ALevelReportAssembler().assemble(new ALevelReportSource(
                store.findById(assessmentId).orElseThrow(), Optional.empty(), Optional.empty()),
                ALevelReportTier.PAID);
        UUID entitlementId = UUID.randomUUID();
        purchaseStore.savePending(new ALevelReportEntitlement(entitlementId, assessmentId, ALevelReportTier.PAID,
                ALevelEntitlementStatus.PENDING, ALevelPaymentProvider.STRIPE, "cs_expired_a_level",
                URI.create("https://checkout.stripe.test/expired"), "price_configured", Optional.empty(),
                Optional.empty(), Instant.parse("2026-10-01T12:01:00Z"), Optional.empty()));
        var replacement = new ALevelCheckoutSession("cs_retry_a_level", URI.create("https://checkout.stripe.test/retry"),
                ALevelPaymentProvider.STRIPE, Optional.empty());
        var other = new ALevelCheckoutSession("cs_other_a_level", URI.create("https://checkout.stripe.test/other"),
                ALevelPaymentProvider.STRIPE, Optional.empty());
        Instant retriedAt = Instant.parse("2026-10-02T09:00:00Z");

        purchaseStore.replaceExpiredCheckout(entitlementId, "cs_expired_a_level", replacement, "price_configured", retriedAt);
        // A concurrent retry that shared the idempotency key received the same session: harmless.
        purchaseStore.replaceExpiredCheckout(entitlementId, "cs_expired_a_level", replacement, "price_configured", retriedAt);
        // A retry that still believes the expired session is current must not overwrite the replacement.
        assertThatThrownBy(() -> purchaseStore.replaceExpiredCheckout(entitlementId, "cs_expired_a_level", other,
                "price_configured", retriedAt)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("the pending checkout changed before it could be replaced");

        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId)).get().satisfies(retried -> {
            assertThat(retried.entitlementId()).isEqualTo(entitlementId);
            assertThat(retried.status()).isEqualTo(ALevelEntitlementStatus.PENDING);
            assertThat(retried.providerCheckoutSessionId()).isEqualTo("cs_retry_a_level");
            assertThat(retried.checkoutUrl()).isEqualTo(URI.create("https://checkout.stripe.test/retry"));
            assertThat(retried.createdAt()).isEqualTo(retriedAt);
        });

        var snapshot = new PurchasedALevelReportSnapshot(entitlementId, assessmentId, report.reportVersion(),
                Instant.parse("2026-10-02T09:05:00Z"), report);
        var expiredEvent = new ALevelPaymentEvent("evt_expired_a_level", ALevelPaymentProvider.STRIPE, "cs_expired_a_level",
                entitlementId, assessmentId, Optional.of("pi_expired"), Instant.parse("2026-10-02T09:04:00Z"));
        var retriedEvent = new ALevelPaymentEvent("evt_retry_a_level", ALevelPaymentProvider.STRIPE, "cs_retry_a_level",
                entitlementId, assessmentId, Optional.of("pi_retry"), Instant.parse("2026-10-02T09:04:00Z"));
        assertThatThrownBy(() -> purchaseStore.confirmAndFreeze(expiredEvent, snapshot))
                .isInstanceOf(InvalidPaymentNotificationException.class);
        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId).orElseThrow().status())
                .isEqualTo(ALevelEntitlementStatus.PENDING);

        assertThat(purchaseStore.confirmAndFreeze(retriedEvent, snapshot))
                .isEqualTo(ALevelPurchaseStore.PurchaseConfirmationResult.ACTIVATED);
        assertThat(purchaseStore.confirmAndFreeze(retriedEvent, snapshot))
                .isEqualTo(ALevelPurchaseStore.PurchaseConfirmationResult.ALREADY_PROCESSED);
        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId).orElseThrow().status())
                .isEqualTo(ALevelEntitlementStatus.ACTIVE);
        // An active purchase can never be pointed at another checkout.
        assertThatThrownBy(() -> purchaseStore.replaceExpiredCheckout(entitlementId, "cs_retry_a_level", other,
                "price_configured", retriedAt)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("the pending checkout changed before it could be replaced");
    }

    @Test
    void roundTripFreezesTheEntitlementPurchasedReportProcessedEventAndImmutablePdf() {
        UUID assessmentId = savedAssessment("mixed_balanced");
        UUID entitlementId = UUID.randomUUID();
        var report = new ALevelReportAssembler().assemble(new ALevelReportSource(
                store.findById(assessmentId).orElseThrow(), Optional.empty(), Optional.empty()),
                ALevelReportTier.PAID);
        var pending = new ALevelReportEntitlement(entitlementId, assessmentId, ALevelReportTier.PAID,
                ALevelEntitlementStatus.PENDING, ALevelPaymentProvider.STRIPE, "cs_test_a_level",
                URI.create("https://checkout.stripe.test/a-level"), "price_a_level",
                Optional.empty(), Optional.empty(), Instant.parse("2026-10-01T12:01:00Z"),
                Optional.empty());
        purchaseStore.savePending(pending);
        var event = new ALevelPaymentEvent("evt_a_level", ALevelPaymentProvider.STRIPE,
                "cs_test_a_level", entitlementId, assessmentId, Optional.of("pi_a_level"),
                Instant.parse("2026-10-01T12:02:00Z"));
        var snapshot = new PurchasedALevelReportSnapshot(entitlementId, assessmentId,
                report.reportVersion(), Instant.parse("2026-10-01T12:02:01Z"), report);

        assertThat(purchaseStore.findSnapshotByAssessmentId(assessmentId)).isEmpty();
        assertThat(purchaseStore.confirmAndFreeze(event, snapshot))
                .isEqualTo(ALevelPurchaseStore.PurchaseConfirmationResult.ACTIVATED);

        assertThat(purchaseStore.findSnapshotByAssessmentId(assessmentId)).contains(snapshot);
        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId)).get().satisfies(active -> {
            assertThat(active.status()).isEqualTo(ALevelEntitlementStatus.ACTIVE);
            assertThat(active.providerPaymentReference()).contains("pi_a_level");
            assertThat(active.purchasedReportVersion()).contains("a-level-report-v1");
            assertThat(active.confirmedAt()).contains(Instant.parse("2026-10-01T12:02:01Z"));
        });
        assertThat(purchaseStore.isEventProcessed(ALevelPaymentProvider.STRIPE, "evt_a_level")).isTrue();
        // A redelivered Stripe event is idempotent and never rebuilds the purchased report.
        assertThat(purchaseStore.confirmAndFreeze(event, snapshot))
                .isEqualTo(ALevelPurchaseStore.PurchaseConfirmationResult.ALREADY_PROCESSED);
        assertThat(purchaseStore.findSnapshotByAssessmentId(assessmentId)).contains(snapshot);

        byte[] pdfBytes = "%PDF-1.7 persisted a-level fixture".getBytes(StandardCharsets.US_ASCII);
        var pdf = new PurchasedALevelReportPdf(entitlementId, assessmentId, report.reportVersion(),
                ALevelReportPdfVersions.PDF_TEMPLATE, Instant.parse("2026-10-01T12:03:00Z"),
                "ai-career-impact-mixed-a-level-report.pdf", pdfBytes.length, "e".repeat(64), pdfBytes);
        var storedPdf = pdfStore.saveIfAbsent(pdf);

        assertThat(storedPdf).usingRecursiveComparison().isEqualTo(pdf);
        assertThat(pdfStore.findByEntitlementId(entitlementId)).get().satisfies(reloaded -> {
            assertThat(reloaded.bytes()).isEqualTo(pdfBytes);
            assertThat(reloaded.sha256()).isEqualTo("e".repeat(64));
            assertThat(reloaded.purchasedReportVersion()).isEqualTo("a-level-report-v1");
            assertThat(reloaded.pdfTemplateVersion()).isEqualTo("a-level-report-pdf-v1");
        });
        // The artifact is immutable: a losing concurrent candidate never overwrites it.
        byte[] losing = "%PDF-different-candidate".getBytes(StandardCharsets.US_ASCII);
        assertThat(pdfStore.saveIfAbsent(new PurchasedALevelReportPdf(entitlementId, assessmentId,
                report.reportVersion(), ALevelReportPdfVersions.PDF_TEMPLATE,
                Instant.parse("2026-10-01T12:04:00Z"), "different.pdf", losing.length,
                "f".repeat(64), losing)).bytes()).isEqualTo(pdfBytes);
    }

    @Test
    void noJourneysEntitlementUnlocksAnotherJourney() {
        UUID aLevelId = savedAssessment("art_design_media");
        UUID professionalId = savedProfessionalAssessment();
        UUID universityId = savedUniversityAssessment();

        professionalPurchaseStore.savePending(new com.chikere.futureai.payment.professional.application
                .ProfessionalReportEntitlement(UUID.randomUUID(), professionalId,
                com.chikere.futureai.report.professional.application.ProfessionalReportTier.PAID,
                com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus.PENDING,
                com.chikere.futureai.payment.professional.application.ProfessionalPaymentProvider.STRIPE,
                "cs_professional_isolation", URI.create("https://checkout.stripe.test/professional"),
                "price_professional", Optional.empty(), Optional.empty(),
                Instant.parse("2026-10-01T12:00:00Z"), Optional.empty()));
        universityPurchaseStore.savePending(new com.chikere.futureai.payment.university.application
                .UniversityReportEntitlement(UUID.randomUUID(), universityId,
                com.chikere.futureai.report.university.application.UniversityReportTier.PAID,
                com.chikere.futureai.payment.university.application.UniversityEntitlementStatus.PENDING,
                com.chikere.futureai.payment.university.application.UniversityPaymentProvider.STRIPE,
                "cs_university_isolation", URI.create("https://checkout.stripe.test/university"),
                "price_university", Optional.empty(), Optional.empty(),
                Instant.parse("2026-10-01T12:00:00Z"), Optional.empty()));

        // Each journey only sees its own entitlement, keyed by its own assessment table.
        assertThat(professionalPurchaseStore.findEntitlementByAssessmentId(professionalId)).isPresent();
        assertThat(universityPurchaseStore.findEntitlementByAssessmentId(universityId)).isPresent();
        assertThat(purchaseStore.findEntitlementByAssessmentId(aLevelId)).isEmpty();
        assertThat(purchaseStore.findEntitlementByAssessmentId(professionalId)).isEmpty();
        assertThat(purchaseStore.findEntitlementByAssessmentId(universityId)).isEmpty();
        assertThat(professionalPurchaseStore.findEntitlementByAssessmentId(aLevelId)).isEmpty();
        assertThat(universityPurchaseStore.findEntitlementByAssessmentId(aLevelId)).isEmpty();
        assertThat(purchaseStore.findSnapshotByAssessmentId(professionalId)).isEmpty();
        assertThat(purchaseStore.findSnapshotByAssessmentId(universityId)).isEmpty();
    }

    @Test
    void anALevelEntitlementCannotBeCreatedForAnotherJourneysAssessmentId() {
        UUID universityId = savedUniversityAssessment();
        var entitlement = new ALevelReportEntitlement(UUID.randomUUID(), universityId,
                ALevelReportTier.PAID, ALevelEntitlementStatus.PENDING, ALevelPaymentProvider.STRIPE,
                "cs_wrong_journey", URI.create("https://checkout.stripe.test/wrong"), "price_a_level",
                Optional.empty(), Optional.empty(), Instant.parse("2026-10-01T12:00:00Z"),
                Optional.empty());

        // The foreign key points at a_level_assessment, so another journey's UUID cannot be used.
        assertThatThrownBy(() -> purchaseStore.savePending(entitlement))
                .isInstanceOf(RuntimeException.class);
    }

    private UUID savedAssessment(String fixtureId) {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureALevelJudgmentProvider(fixtureId).judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        UUID id = UUID.randomUUID();
        store.save(new StoredALevelAssessment(id, Instant.parse("2026-10-01T12:00:00Z"), state,
                judgments, new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
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

    private UUID savedUniversityAssessment() {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of(),
                Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        UUID id = UUID.randomUUID();
        universityStore.save(new StoredUniversityAssessment(id, Instant.parse("2026-10-01T12:00:00Z"),
                state, judgments, new UniversityAssessmentResult(decision,
                com.chikere.futureai.assessment.university.application.AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty()));
        return id;
    }
}
