package com.chikere.futureai.assessment.professional.persistence;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import com.chikere.futureai.decision.professional.ProfessionalDecisionFactor;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicy;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2;
import com.chikere.futureai.decision.professional.ProfessionalMaterialityCalibrationCases;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.application.NarrativeSource;
import com.chikere.futureai.narrative.professional.application.NarrativeStatus;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrative;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeStore;
import com.chikere.futureai.narrative.professional.application.StoredProfessionalNarrative;
import com.chikere.futureai.evidence.professional.application.*;
import org.flywaydb.core.Flyway;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import com.chikere.futureai.occupation.professional.application.*;
import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.report.professional.application.*;
import com.chikere.futureai.pdf.professional.application.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProfessionalAssessmentStoreTest {

    @MockitoBean ProfessionalJudgmentProvider unusedLiveProvider;
    @MockitoBean ProfessionalNarrativeGenerator unusedNarrativeGenerator;
    @MockitoBean com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider
            unusedUniversityProvider;
    @MockitoBean com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator
            unusedUniversityNarrativeGenerator;
    @MockitoBean com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider
            unusedALevelProvider;
    @MockitoBean com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator
            unusedALevelNarrativeGenerator;
    @Autowired ProfessionalAssessmentStore store;
    @Autowired ProfessionalNarrativeStore narrativeStore;
    @Autowired ProfessionalEvidenceStore evidenceStore;
    @Autowired ProfessionalOccupationClassificationStore classificationStore;
    @Autowired ProfessionalPurchaseStore purchaseStore;
    @Autowired ProfessionalReportPdfStore pdfStore;
    @Autowired Flyway flyway;
    @Autowired DataSource dataSource;
    @Autowired ProfessionalDecisionPolicy decisionPolicy;

    @Test
    void newAssessmentsAreDecidedUnderV2WhileAStoredV1AssessmentKeepsItsSavedFactors() {
        assertThat(decisionPolicy).isInstanceOf(ProfessionalDecisionPolicyV2.class);
        assertThat(decisionPolicy.policyVersion()).isEqualTo("professional-decision-v2");

        // Saved under v1, which selected the immaterial +0.009 and +0.003 effects as raising AI Exposure.
        var state = new ProfessionalAssessmentState("Registered Nurse", "I care for patients on a busy ward.",
                List.of("Monitor patients"), List.of("Clinical observation"), OptionalInt.of(9));
        var judgments = ProfessionalMaterialityCalibrationCases.judgments(
                ProfessionalMaterialityCalibrationCases.registeredNurse());
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        var snapshot = new StoredProfessionalAssessment(UUID.randomUUID(), Instant.parse("2026-10-20T12:00:00Z"),
                state, judgments, new ProfessionalAssessmentResult(decision, AssessmentSource.JEV,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty(),
                ProfessionalAssessmentInputMetadata.manual());
        store.save(snapshot);

        var loaded = store.findById(snapshot.id()).orElseThrow();
        assertThat(loaded).isEqualTo(snapshot);
        assertThat(loaded.result().decisionPolicyVersion()).isEqualTo("professional-decision-v1");
        assertThat(loaded.result().decision().topExposureDrivers()).extracting(ProfessionalDecisionFactor::dimensionId)
                .containsExactly("task_repeatability", "original_judgment");
        assertThat(loaded.result().decision().assessmentDecision().keyDrivers())
                .containsExactly("task_repeatability", "original_judgment");
        // Its report is still composed from the saved v1 factors, not recomputed under v2.
        var report = new ProfessionalReportAssembler().assemble(new ProfessionalReportSource(loaded,
                Optional.empty(), Optional.empty(), Optional.empty()), ProfessionalReportTier.PAID);
        assertThat(report.exposureDrivers()).hasSize(2);
        assertThat(report.methodology().decisionPolicyVersion()).isEqualTo("professional-decision-v1");
        assertThat(decisionPolicy.decide(judgments).topExposureDrivers()).isEmpty();
    }

    @Test
    void migratedPostgresqlRoundTripPreservesCompleteSnapshot() {
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("18");
        var state = new ProfessionalAssessmentState("Software Architect",
                "I design software and make consequential architecture decisions.",
                List.of("Design platforms", "Resolve trade-offs"),
                List.of("System design", "Mentoring"), OptionalInt.of(14));
        var fixture = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var modified = new ArrayList<>(fixture.judgments());
        var score = modified.get(1);
        modified.set(1, new DimensionJudgment(score.dimensionId(), score.name(), score.numericValue(),
                Optional.of("some tasks repeat"), Optional.of(AssessmentConfidence.MODERATE),
                OptionalDouble.of(0.73), Map.of("0", 0.2, "1", 0.3, "2", 0.5)));
        var judgments = new AssessmentJudgmentSet(fixture.journey(), fixture.schemaVersion(), modified);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        var result = new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion());
        var snapshot = new StoredProfessionalAssessment(UUID.randomUUID(),
                Instant.parse("2026-09-30T12:00:00Z"), state, judgments, result, Optional.empty(),
                ProfessionalAssessmentInputMetadata.manual());

        store.save(snapshot);
        var loaded = store.findById(snapshot.id()).orElseThrow();

        assertThat(loaded).isEqualTo(snapshot);
        assertThat(loaded.judgments().judgments()).hasSize(9);
        assertThat(loaded.judgments().judgments().getFirst().confidenceValue()).isEmpty();
        assertThat(loaded.judgments().judgments().get(1).probabilityDistribution())
                .containsEntry("1", 0.3);
        assertThat(loaded.result().decision().measures()).hasSize(4);
        assertThat(loaded.result().decision().topExposureDrivers()).isNotEmpty();
        assertThat(loaded.inputMetadata()).isEqualTo(snapshot.inputMetadata());
        assertThat(store.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void manualAssessmentStoresNoExtractorVersion() {
        var state = new ProfessionalAssessmentState("Electrician", "Installs and tests electrical systems.",
                List.of("Install wiring"), List.of("Diagnostics"), OptionalInt.of(8));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("electrician").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        var result = new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion());
        UUID id = UUID.randomUUID();

        store.save(new StoredProfessionalAssessment(id, Instant.parse("2026-10-15T12:00:00Z"), state,
                judgments, result, Optional.empty(), ProfessionalAssessmentInputMetadata.manual()));

        assertThat(store.findById(id).orElseThrow().inputMetadata().extractorVersion()).isEmpty();
        assertThat(new JdbcTemplate(dataSource).queryForObject(
                "select cv_extractor_version from professional_assessment where id = ?", String.class, id))
                .isNull();
    }

    @Test
    void databaseRejectsAManualRowWithAnExtractorVersion() {
        var jdbc = new JdbcTemplate(dataSource);
        assertThatThrownBy(() -> insertRow(jdbc, "public", "MANUAL", null, "professional-cv-extractor-v2"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("professional_assessment_cv_metadata_consistent");
    }

    private static void insertRow(JdbcTemplate jdbc, String schema, String inputSource, String parserVersion,
            String extractorVersion) {
        boolean cv = inputSource.equals("CV_UPLOAD");
        jdbc.update("insert into " + schema + ".professional_assessment (" + REQUIRED_COLUMNS + ", input_source, "
                        + "cv_original_filename, cv_media_type, cv_parser_version, cv_extractor_version, "
                        + "cv_parsed_at) values (" + REQUIRED_VALUES + ", ?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), inputSource, cv ? "cv.pdf" : null, cv ? "application/pdf" : null,
                parserVersion, extractorVersion,
                cv ? java.sql.Timestamp.from(Instant.parse("2026-10-15T12:00:00Z")) : null);
    }

    private static final String REQUIRED_COLUMNS = "id, created_at, profession, work_description, "
            + "responsibilities, skills, source, judgment_schema_version, decision_policy_version, "
            + "judgment_journey, information_sufficiency, decision_journey, result_label, "
            + "assessment_confidence, assessment_status, key_drivers, decision_protective_factors";
    private static final String REQUIRED_VALUES = "?, now(), 'Software Engineer', 'Builds services', "
            + "'[]'::jsonb, '[]'::jsonb, 'LOCAL_FIXTURE', 'professional-v1', 'professional-policy-v1', "
            + "'PROFESSIONAL', 0.9, 'PROFESSIONAL', 'Moderate', 'MODERATE', 'COMPLETE', '[]'::jsonb, '[]'::jsonb";

    @Test
    void migratedPostgresqlRoundTripPreservesNarrativeLifecycleContentAndProvenance() {
        var state = new ProfessionalAssessmentState("Electrician", "Installs and tests electrical systems.",
                List.of("Install wiring"), List.of("Diagnostics"), OptionalInt.of(8));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("electrician").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        var result = new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion());
        UUID id = UUID.randomUUID();
        store.save(new StoredProfessionalAssessment(id, Instant.parse("2026-09-30T12:00:00Z"), state,
                judgments, result, Optional.empty()));
        var narrative = new ProfessionalNarrative("Measured change", "Saved summary", "Saved meaning",
                "Saved exposure", "Saved human advantage", "Saved complementarity", "Saved adaptability",
                List.of("Review repeatable work", "Strengthen human context", "Document accountability"),
                "Saved caution");
        Instant attempted = Instant.parse("2026-09-30T12:01:00Z");
        Instant generated = Instant.parse("2026-09-30T12:01:05Z");
        var stored = new StoredProfessionalNarrative(id, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                NarrativeSource.LOCAL_FIXTURE, "local-fixture", Optional.of("configured-alias"),
                "professional-narrative-v1", attempted, Optional.of(generated));

        narrativeStore.save(stored);

        assertThat(narrativeStore.findByAssessmentId(id)).contains(stored);
        assertThat(narrativeStore.findByAssessmentId(id).orElseThrow().narrative().orElseThrow()
                .practicalActions()).containsExactlyElementsOf(narrative.practicalActions());
    }

    @Test
    void migratedPostgresqlRoundTripPreservesEvidenceLifecycleAndAllItemMetadata() {
        var state = new ProfessionalAssessmentState("Software Architect", "Designs systems.",
                List.of("Design platforms"), List.of("Architecture"), OptionalInt.of(12));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        UUID id = UUID.randomUUID();
        store.save(new StoredProfessionalAssessment(id, Instant.parse("2026-09-30T12:00:00Z"), state,
                judgments, new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty()));
        Instant retrieved = Instant.parse("2026-09-30T12:02:00Z");
        var item = new ProfessionalEvidenceItem(ProfessionalEvidenceCategory.SALARY_CONTEXT,
                "Published pay context", "A factual saved summary.", "Official dataset",
                Optional.of("table-14/soc-2133"), Optional.of(URI.create("https://example.gov.uk/data?a=1&b=2")),
                Optional.of("Official publisher"), Optional.of("United Kingdom"),
                Optional.of(LocalDate.parse("2026-04-01")), retrieved,
                Optional.of("Median annual pay"), Optional.of(new BigDecimal("54321.50")),
                Optional.of("GBP per year"), EvidenceSourceQuality.OFFICIAL,
                "Related occupational context, separate from scoring.");
        var bundle = new ProfessionalEvidenceBundle(id, "professional-evidence-v1", EvidenceSource.LIVE,
                "official-provider", EvidenceStatus.PARTIAL, retrieved, List.of(item));

        evidenceStore.save(bundle);

        assertThat(evidenceStore.findByAssessmentId(id)).contains(bundle);
        assertThat(evidenceStore.findByAssessmentId(id).orElseThrow().items().getFirst().sourceUrl())
                .contains(URI.create("https://example.gov.uk/data?a=1&b=2"));
    }

    @Test
    void migratedPostgresqlRoundTripPreservesImmutableOccupationClassification() {
        var state = new ProfessionalAssessmentState("Software Architect", "Designs systems.",
                List.of("Design platforms"), List.of("Architecture"), OptionalInt.of(12));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        UUID id = UUID.randomUUID();
        store.save(new StoredProfessionalAssessment(id, Instant.parse("2026-09-30T12:00:00Z"), state,
                judgments, new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty()));
        var stored = new StoredProfessionalOccupationClassification(id,
                new ProfessionalOccupationClassification("IT business analysts, architects and systems designers",
                        OccupationClassificationSystem.SOC_2020, "2133",
                        OccupationClassificationSource.LOCAL_FIXTURE,
                        OccupationClassificationQuality.DEVELOPMENT_FIXTURE,
                        "local-fixture:senior_software_architect", "professional-occupation-classifier-v1"),
                Instant.parse("2026-09-30T12:01:00Z"));

        classificationStore.save(stored);

        assertThat(classificationStore.findByAssessmentId(id)).contains(stored);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> classificationStore.save(stored))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("occupation classification is an immutable historical snapshot");
    }

    @Test
    void anExpiredCheckoutIsReplacedInPlaceAndOnlyTheReplacementSessionCanThenActivate() {
        var state = new ProfessionalAssessmentState("Software Architect", "Designs systems.",
                List.of("Design platforms"), List.of("Architecture"), OptionalInt.of(12));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        UUID assessmentId = UUID.randomUUID();
        var assessment = new StoredProfessionalAssessment(assessmentId, Instant.parse("2026-09-30T12:00:00Z"),
                state, judgments, new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        store.save(assessment);
        var report = new ProfessionalReportAssembler().assemble(new ProfessionalReportSource(assessment,
                Optional.empty(), Optional.empty(), Optional.empty()), ProfessionalReportTier.PAID);
        UUID entitlementId = UUID.randomUUID();
        purchaseStore.savePending(new ProfessionalReportEntitlement(entitlementId, assessmentId, ProfessionalReportTier.PAID,
                ProfessionalEntitlementStatus.PENDING, ProfessionalPaymentProvider.STRIPE, "cs_expired_professional",
                URI.create("https://checkout.stripe.test/expired"), "price_configured", Optional.empty(),
                Optional.empty(), Instant.parse("2026-10-01T12:01:00Z"), Optional.empty()));
        var replacement = new ProfessionalCheckoutSession("cs_retry_professional", URI.create("https://checkout.stripe.test/retry"),
                ProfessionalPaymentProvider.STRIPE, Optional.empty());
        var other = new ProfessionalCheckoutSession("cs_other_professional", URI.create("https://checkout.stripe.test/other"),
                ProfessionalPaymentProvider.STRIPE, Optional.empty());
        Instant retriedAt = Instant.parse("2026-10-02T09:00:00Z");

        purchaseStore.replaceExpiredCheckout(entitlementId, "cs_expired_professional", replacement, "price_configured", retriedAt);
        // A concurrent retry that shared the idempotency key received the same session: harmless.
        purchaseStore.replaceExpiredCheckout(entitlementId, "cs_expired_professional", replacement, "price_configured", retriedAt);
        // A retry that still believes the expired session is current must not overwrite the replacement.
        assertThatThrownBy(() -> purchaseStore.replaceExpiredCheckout(entitlementId, "cs_expired_professional", other,
                "price_configured", retriedAt)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("the pending checkout changed before it could be replaced");

        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId)).get().satisfies(retried -> {
            assertThat(retried.entitlementId()).isEqualTo(entitlementId);
            assertThat(retried.status()).isEqualTo(ProfessionalEntitlementStatus.PENDING);
            assertThat(retried.providerCheckoutSessionId()).isEqualTo("cs_retry_professional");
            assertThat(retried.checkoutUrl()).isEqualTo(URI.create("https://checkout.stripe.test/retry"));
            assertThat(retried.createdAt()).isEqualTo(retriedAt);
        });

        var snapshot = new PurchasedProfessionalReportSnapshot(entitlementId, assessmentId, report.reportVersion(),
                Instant.parse("2026-10-02T09:05:00Z"), report);
        var expiredEvent = new ProfessionalPaymentEvent("evt_expired_professional", ProfessionalPaymentProvider.STRIPE, "cs_expired_professional",
                entitlementId, assessmentId, Optional.of("pi_expired"), Instant.parse("2026-10-02T09:04:00Z"));
        var retriedEvent = new ProfessionalPaymentEvent("evt_retry_professional", ProfessionalPaymentProvider.STRIPE, "cs_retry_professional",
                entitlementId, assessmentId, Optional.of("pi_retry"), Instant.parse("2026-10-02T09:04:00Z"));
        assertThatThrownBy(() -> purchaseStore.confirmAndFreeze(expiredEvent, snapshot))
                .isInstanceOf(InvalidPaymentNotificationException.class);
        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId).orElseThrow().status())
                .isEqualTo(ProfessionalEntitlementStatus.PENDING);

        assertThat(purchaseStore.confirmAndFreeze(retriedEvent, snapshot))
                .isEqualTo(ProfessionalPurchaseStore.PurchaseConfirmationResult.ACTIVATED);
        assertThat(purchaseStore.confirmAndFreeze(retriedEvent, snapshot))
                .isEqualTo(ProfessionalPurchaseStore.PurchaseConfirmationResult.ALREADY_PROCESSED);
        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId).orElseThrow().status())
                .isEqualTo(ProfessionalEntitlementStatus.ACTIVE);
        // An active purchase can never be pointed at another checkout.
        assertThatThrownBy(() -> purchaseStore.replaceExpiredCheckout(entitlementId, "cs_retry_professional", other,
                "price_configured", retriedAt)).isInstanceOf(RuntimeException.class)
                .hasMessageContaining("the pending checkout changed before it could be replaced");
    }

    @Test
    void migratedPostgresqlRoundTripPreservesEntitlementFrozenReportAndProcessedEvent() {
        var state = new ProfessionalAssessmentState("Software Architect", "Designs systems.",
                List.of("Design platforms"), List.of("Architecture"), OptionalInt.of(12));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        UUID assessmentId = UUID.randomUUID(); UUID entitlementId = UUID.randomUUID();
        var assessment = new StoredProfessionalAssessment(assessmentId, Instant.parse("2026-09-30T12:00:00Z"),
                state, judgments, new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        store.save(assessment);
        var report = new ProfessionalReportAssembler().assemble(new ProfessionalReportSource(assessment,
                Optional.empty(), Optional.empty(), Optional.empty()), ProfessionalReportTier.PAID);
        var pending = new ProfessionalReportEntitlement(entitlementId, assessmentId, ProfessionalReportTier.PAID,
                ProfessionalEntitlementStatus.PENDING, ProfessionalPaymentProvider.STRIPE, "cs_test_roundtrip",
                URI.create("https://checkout.stripe.test/roundtrip"), "price_configured", Optional.empty(),
                Optional.empty(), Instant.parse("2026-09-30T12:01:00Z"), Optional.empty());
        purchaseStore.savePending(pending);
        var event = new ProfessionalPaymentEvent("evt_roundtrip", ProfessionalPaymentProvider.STRIPE,
                "cs_test_roundtrip", entitlementId, assessmentId, Optional.of("pi_roundtrip"),
                Instant.parse("2026-09-30T12:02:00Z"));
        var snapshot = new PurchasedProfessionalReportSnapshot(entitlementId, assessmentId,
                report.reportVersion(), Instant.parse("2026-09-30T12:02:01Z"), report);

        assertThat(purchaseStore.findSnapshotByAssessmentId(assessmentId)).isEmpty();
        assertThat(purchaseStore.confirmAndFreeze(event, snapshot))
                .isEqualTo(ProfessionalPurchaseStore.PurchaseConfirmationResult.ACTIVATED);

        assertThat(purchaseStore.findSnapshotByAssessmentId(assessmentId)).contains(snapshot);
        assertThat(purchaseStore.findEntitlementByAssessmentId(assessmentId)).get().satisfies(active -> {
            assertThat(active.status()).isEqualTo(ProfessionalEntitlementStatus.ACTIVE);
            assertThat(active.providerCheckoutSessionId()).isEqualTo("cs_test_roundtrip");
            assertThat(active.providerPaymentReference()).contains("pi_roundtrip");
            assertThat(active.purchasedReportVersion()).contains("professional-report-v2");
            assertThat(active.confirmedAt()).contains(Instant.parse("2026-09-30T12:02:01Z"));
        });
        assertThat(purchaseStore.isEventProcessed(ProfessionalPaymentProvider.STRIPE, "evt_roundtrip")).isTrue();
        assertThat(purchaseStore.confirmAndFreeze(event, snapshot))
                .isEqualTo(ProfessionalPurchaseStore.PurchaseConfirmationResult.ALREADY_PROCESSED);
        assertThat(purchaseStore.findSnapshotByAssessmentId(assessmentId)).contains(snapshot);

        byte[] pdfBytes = "%PDF-1.7 persisted fixture".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var pdf = new PurchasedProfessionalReportPdf(entitlementId, assessmentId, report.reportVersion(),
                ProfessionalReportPdfVersions.PDF_TEMPLATE, Instant.parse("2026-09-30T12:03:00Z"),
                "ai-career-impact-software-architect-report.pdf", pdfBytes.length, "a".repeat(64), pdfBytes);
        var storedPdf = pdfStore.saveIfAbsent(pdf);
        assertThat(storedPdf).usingRecursiveComparison().isEqualTo(pdf);
        assertThat(pdfStore.findByEntitlementId(entitlementId)).get().satisfies(reloaded -> {
            assertThat(reloaded.bytes()).isEqualTo(pdfBytes);
            assertThat(reloaded.sha256()).isEqualTo("a".repeat(64));
            assertThat(reloaded.fileSize()).isEqualTo(pdfBytes.length);
            assertThat(reloaded.generatedAt()).isEqualTo(Instant.parse("2026-09-30T12:03:00Z"));
            assertThat(reloaded.purchasedReportVersion()).isEqualTo("professional-report-v2");
            assertThat(reloaded.pdfTemplateVersion()).isEqualTo("professional-report-pdf-v4");
        });
        byte[] losingBytes = "%PDF-different-racing-candidate".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        var losingCandidate = new PurchasedProfessionalReportPdf(entitlementId, assessmentId, report.reportVersion(),
                ProfessionalReportPdfVersions.PDF_TEMPLATE, Instant.parse("2026-09-30T12:04:00Z"),
                "different.pdf", losingBytes.length, "b".repeat(64), losingBytes);
        assertThat(pdfStore.saveIfAbsent(losingCandidate).bytes()).isEqualTo(pdfBytes);
    }
}
