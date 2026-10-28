package com.chikere.futureai.report.university.detailed.persistence;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.coursemodule.university.analysis.ModuleAnalysisTestData;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisService;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisStore;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleClassifier;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentProvider;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidancePolicyV1;
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
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportService;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportSnapshot;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportStore;
import com.chikere.futureai.trace.AssessmentTrace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
                .containsEntry("guidance_policy_version", "university-guidance-policy-v1")
                .containsEntry("paid_narrative_version", "university-paid-narrative-v1")
                .containsEntry("paid_narrative_status", "AVAILABLE")
                .containsEntry("report_version", "university-report-v2")
                .containsEntry("pdf_version", "university-report-pdf-v2");
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
                new UniversityGuidancePolicyV1(), narratives, new UniversityDetailedReportAssembler(), store, CLOCK,
                AssessmentTrace.disabled());
    }
}
