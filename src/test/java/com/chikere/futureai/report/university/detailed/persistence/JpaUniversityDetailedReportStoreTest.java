package com.chikere.futureai.report.university.detailed.persistence;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.coursemodule.university.analysis.ModuleAnalysisTestData;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisService;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisStore;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleClassifier;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentProvider;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidancePolicyV2;
import com.chikere.futureai.coursemodule.university.policy.UniversityModulePolicyV1;
import com.chikere.futureai.narrative.university.paid.ScriptedPaidNarrativeGenerator;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeService;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeStatus;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeValidator;
import com.chikere.futureai.payment.university.application.PurchasedUniversityReportSnapshot;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityPaymentProvider;
import com.chikere.futureai.payment.university.application.UniversityPurchaseStore;
import com.chikere.futureai.payment.university.application.UniversityReportEntitlement;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportAssembler;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReport;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportService;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportSnapshot;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportStore;
import com.chikere.futureai.trace.AssessmentTrace;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** university_detailed_report on migrated PostgreSQL: the full v2 lifecycle against the real stores. No provider. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JpaUniversityDetailedReportStoreTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC);

    @MockitoBean com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider unusedProfessional;
    @MockitoBean com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator unusedNarrative;
    @MockitoBean com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider unusedUniversity;
    @MockitoBean com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator unusedUniversityNarrative;
    @MockitoBean com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider unusedALevel;
    @MockitoBean com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator unusedALevelNarrative;

    @Autowired UniversityAssessmentStore assessments;
    @Autowired UniversityPurchaseStore purchases;
    @Autowired UniversityModuleAnalysisStore moduleStore;
    @Autowired UniversityDetailedReportStore store;
    @Autowired DataSource dataSource;

    private final ScriptedPaidNarrativeGenerator narrative = new ScriptedPaidNarrativeGenerator();

    @Test
    void theGeneratedReportRoundTripsExactlyWithEveryFrozenVersion() {
        UUID id = paidCourseProfile();
        narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());

        var generated = service().prepare(id, Optional.empty()).stored().orElseThrow();
        var reloaded = store.findByAssessmentId(id).orElseThrow();

        assertThat(reloaded).isEqualTo(generated);
        assertThat(reloaded.report()).isEqualTo(generated.report());
        assertThat(reloaded.paidNarrativeStatus()).isEqualTo(UniversityPaidNarrativeStatus.AVAILABLE);
        assertThat(reloaded.moduleAnalysisId()).isEqualTo(moduleStore.findCompletedByAssessmentId(id).orElseThrow()
                .snapshotId());
        var row = new JdbcTemplate(dataSource).queryForMap("select decision_policy_version, module_schema_version, "
                + "module_policy_version, guidance_policy_version, paid_narrative_version, paid_narrative_status, "
                + "report_version, pdf_version from university_detailed_report where assessment_id = ?", id);
        assertThat(row).containsEntry("decision_policy_version", "university-decision-v2")
                .containsEntry("module_schema_version", "university-module-v1")
                .containsEntry("module_policy_version", "university-module-policy-v1")
                .containsEntry("guidance_policy_version", "university-guidance-policy-v2")
                .containsEntry("paid_narrative_version", "university-paid-narrative-v2")
                .containsEntry("paid_narrative_status", "AVAILABLE")
                .containsEntry("report_version", "university-report-v3")
                .containsEntry("pdf_version", "university-report-pdf-v3");
        assertThat(reloaded.report().practical().studyStrategy().personalised()).isTrue();
        // The v2 purchase itself froze only a marker, never a v1 report.
        assertThat(purchases.findSnapshotByAssessmentId(id).orElseThrow().isCourseProfilePurchase()).isTrue();
    }

    @Test
    void aSecondSnapshotForTheSameAssessmentNeverOverwritesTheFirst() {
        UUID id = paidCourseProfile();
        narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());
        var first = service().prepare(id, Optional.empty()).stored().orElseThrow();

        var rival = new UniversityDetailedReportSnapshot(UUID.randomUUID(), id, first.entitlementId(),
                first.decisionPolicyVersion(), first.moduleAnalysisId(), first.moduleSchemaVersion(),
                first.modulePolicyVersion(), first.guidancePolicyVersion(), first.paidNarrativeVersion(),
                UniversityPaidNarrativeStatus.FALLBACK, first.reportVersion(), first.pdfVersion(),
                Instant.parse("2026-10-29T09:00:00Z"), first.report());
        var canonical = store.saveIfAbsent(rival);

        assertThat(canonical).isEqualTo(first);
        assertThat(store.findByAssessmentId(id)).contains(first);
        assertThat(new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from university_detailed_report where assessment_id = ?", Integer.class, id))
                .isEqualTo(1);
    }

    @Test
    void aStoredV2ReportIsServedExactlyAsStoredAndNeverRegeneratedOrUpgraded() throws Exception {
        UUID id = paidCourseProfile();
        // Analyse the modules (the stored v2 row references them) without storing a report.
        narrative.failing(new IllegalStateException("timeout"));
        assertThat(service().prepare(id, Optional.empty()).provisional()).isTrue();
        var modules = moduleStore.findCompletedByAssessmentId(id).orElseThrow();
        UUID entitlement = purchases.findEntitlementByAssessmentId(id).orElseThrow().entitlementId();
        String document = Files.readString(Path.of("src/test/resources/historical/university",
                "university-report-v2-nursing.json"));
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into university_detailed_report (id, assessment_id, entitlement_id, decision_policy_version, "
                + "module_analysis_id, module_schema_version, module_policy_version, guidance_policy_version, "
                + "paid_narrative_version, paid_narrative_status, report_version, pdf_version, generated_at, report) "
                + "values (?, ?, ?, 'university-decision-v2', ?, 'university-module-v1', 'university-module-policy-v1', "
                + "'university-guidance-policy-v1', 'university-paid-narrative-v1', 'AVAILABLE', 'university-report-v2', "
                + "'university-report-pdf-v2', now(), cast(? as jsonb))", UUID.randomUUID(), id, entitlement,
                modules.snapshotId(), document);
        String before = jdbc.queryForObject("select report::text from university_detailed_report where assessment_id = ?",
                String.class, id);

        var stored = store.findByAssessmentId(id).orElseThrow();
        assertThat(stored.reportVersion()).isEqualTo("university-report-v2");
        assertThat(stored.pdfVersion()).isEqualTo("university-report-pdf-v2");
        assertThat(stored.paidNarrativeVersion()).isEqualTo("university-paid-narrative-v1");
        assertThat(stored.guidancePolicyVersion()).isEqualTo("university-guidance-policy-v1");
        // The stored v1 narrative's perspectives read back as written; the v3 additions are simply absent.
        assertThat(stored.report()).isEqualTo(new ObjectMapper().findAndRegisterModules().readValue(document,
                UniversityDetailedReport.class));
        assertThat(stored.report().perspectives().modelWritten()).isTrue();
        assertThat(stored.report().practical()).isNull();

        narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());
        var again = service().prepare(id, Optional.empty());
        assertThat(again.reused()).isTrue();
        assertThat(again.stored()).contains(stored);
        assertThat(narrative.generateCalls).isEqualTo(1);
        assertThat(jdbc.queryForObject("select report::text from university_detailed_report where assessment_id = ?",
                String.class, id)).isEqualTo(before);
        assertThat(jdbc.queryForObject("select report_version from university_detailed_report where assessment_id = ?",
                String.class, id)).isEqualTo("university-report-v2");
        assertThat(jdbc.queryForObject("select guidance_policy_version from university_detailed_report where "
                + "assessment_id = ?", String.class, id)).isEqualTo("university-guidance-policy-v1");
        // Only new, current-version documents are ever written.
        assertThatThrownBy(() -> store.saveIfAbsent(stored)).hasMessageContaining("university-report-v3");
    }

    @Test
    void aRetryableNarrativeStoresNothing() {
        UUID id = paidCourseProfile();
        narrative.failing(new IllegalStateException("timeout"));

        assertThat(service().prepare(id, Optional.empty()).provisional()).isTrue();
        assertThat(store.findByAssessmentId(id)).isEmpty();
    }

    private UUID paidCourseProfile() {
        UUID id = UUID.randomUUID();
        assessments.save(ModuleAnalysisTestData.courseProfileAssessment(id, 8));
        Instant at = ModuleAnalysisTestData.ASSESSED_AT;
        var entitlement = new UniversityReportEntitlement(UUID.randomUUID(), id, UniversityReportTier.PAID,
                UniversityEntitlementStatus.ACTIVE, UniversityPaymentProvider.COMPLIMENTARY,
                "complimentary_" + id, URI.create("http://localhost/university-assessment/" + id), "complimentary",
                Optional.empty(), Optional.of("university-report-v2"), at, Optional.of(at));
        purchases.saveComplimentary(entitlement,
                PurchasedUniversityReportSnapshot.courseProfile(entitlement.entitlementId(), id, at));
        return id;
    }

    private UniversityDetailedReportService service() {
        UniversityReportPurchaseUseCase purchaseUseCase = mock(UniversityReportPurchaseUseCase.class);
        when(purchaseUseCase.findEntitlement(any())).thenAnswer(call ->
                purchases.findEntitlementByAssessmentId(call.getArgument(0)));
        UniversityModuleJudgmentProvider jev = mock(UniversityModuleJudgmentProvider.class);
        when(jev.source()).thenReturn(UniversityModuleJudgmentSource.JEV);
        when(jev.judge(any())).thenAnswer(call -> ModuleAnalysisTestData.fixtureJudgments("nursing",
                call.getArgument(0)));
        var moduleAnalyses = new UniversityModuleAnalysisService(assessments, purchaseUseCase, () -> Optional.of(jev),
                UniversityModuleClassifier.v1(new UniversityModulePolicyV1()), moduleStore, CLOCK,
                AssessmentTrace.disabled());
        var narratives = new UniversityPaidNarrativeService(() -> Optional.of(narrative),
                new UniversityPaidNarrativeValidator(), AssessmentTrace.disabled());
        return new UniversityDetailedReportService(purchases, assessments, moduleAnalyses, moduleStore,
                new UniversityGuidancePolicyV2(), narratives, new UniversityDetailedReportAssembler(), store, CLOCK,
                AssessmentTrace.disabled());
    }
}
