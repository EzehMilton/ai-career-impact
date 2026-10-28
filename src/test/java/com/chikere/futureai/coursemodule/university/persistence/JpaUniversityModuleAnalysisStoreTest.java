package com.chikere.futureai.coursemodule.university.persistence;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.coursemodule.university.analysis.ModuleAnalysisTestData;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisFailedException;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisFailure;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisItem;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisService;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisStore;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleClassifier;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentException;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentProvider;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleClassification;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleFlag;
import com.chikere.futureai.coursemodule.university.policy.UniversityModulePolicyV1;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleRule;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.trace.AssessmentTrace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The real module-analysis store on migrated PostgreSQL, and the lifecycle running against it. No Jev call. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JpaUniversityModuleAnalysisStoreTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC);

    @MockitoBean com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider unusedProfessional;
    @MockitoBean com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator unusedNarrative;
    @MockitoBean com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider unusedUniversity;
    @MockitoBean com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator unusedUniversityNarrative;
    @MockitoBean com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider unusedALevel;
    @MockitoBean com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator unusedALevelNarrative;

    @Autowired UniversityAssessmentStore assessments;
    @Autowired UniversityModuleAnalysisStore store;
    @Autowired DataSource dataSource;

    @Test
    void theCompleteSnapshotRoundTripsWithItsSelectionJudgmentsClassificationAndVersions() {
        var assessment = assessment(10);
        var provider = fixtureProvider();
        var generated = service(provider).generateOrGet(assessment.id(), Optional.of(List.of(10, 9, 1, 2, 3, 4, 5, 6)))
                .snapshot();

        var reloaded = store.findCompletedByAssessmentId(assessment.id()).orElseThrow();

        assertThat(reloaded).isEqualTo(generated);
        assertThat(reloaded.schemaVersion()).isEqualTo("university-module-v1");
        assertThat(reloaded.policyVersion()).isEqualTo("university-module-policy-v1");
        assertThat(reloaded.judgmentSource()).isEqualTo(UniversityModuleJudgmentSource.JEV);
        assertThat(reloaded.providerRequestId()).contains("req_fixture_nursing");
        assertThat(reloaded.inputTokens()).hasValue(9986);
        assertThat(reloaded.courseTitle()).isEqualTo("BSc Nursing (Adult)");
        assertThat(reloaded.skillsDeveloped()).containsExactly("Clinical skills", "Communication");
        // The customer's selection, stored in course order.
        assertThat(reloaded.selectedModuleIds()).containsExactly(1, 2, 3, 4, 5, 6, 9, 10);
        assertThat(reloaded.items()).extracting(item -> item.judgments().moduleTitle()).containsExactly("Module 1",
                "Module 2", "Module 3", "Module 4", "Module 5", "Module 6", "Module 9", "Module 10");
        // Every judgment, with its distribution, survives exactly.
        var fixture = com.chikere.futureai.coursemodule.university.policy.ModuleProbeFixtures.load("nursing");
        for (int index = 0; index < 8; index++) {
            var stored = reloaded.items().get(index).judgments();
            var live = fixture.modules().get(index);
            assertThat(stored.describedClearly()).isEqualTo(live.describedClearly());
            assertThat(stored.professionalStandards()).isEqualTo(live.professionalStandards());
            assertThat(stored.aiLearningAssistance()).isEqualTo(live.aiLearningAssistance());
            assertThat(stored.independentDemonstration()).isEqualTo(live.independentDemonstration());
            assertThat(stored.humanJudgement()).isEqualTo(live.humanJudgement());
            assertThat(stored.interpersonalWork()).isEqualTo(live.interpersonalWork());
            assertThat(stored.practicalApplication()).isEqualTo(live.practicalApplication());
            assertThat(stored.technicalQuantitative()).isEqualTo(live.technicalQuantitative());
        }
        assertThat(new JdbcTemplate(dataSource).queryForObject("select count(*) from university_module_analysis_judgment "
                + "where analysis_id = ?", Integer.class, reloaded.snapshotId())).isEqualTo(64);
        // Classification as decided at generation: category, rule, flags, close calls and the customer label.
        UniversityModuleAnalysisItem placements = reloaded.items().get(7);
        assertThat(placements.classification().category()).isEqualTo(UniversityModuleCategory.PRACTICAL_OR_EXPERIENTIAL);
        assertThat(placements.classification().rule()).isEqualTo(UniversityModuleRule.UM1B_HANDS_ON_WITH_PEOPLE);
        assertThat(placements.classification().flags()).containsExactly(
                UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE, UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT);
        assertThat(placements.displayLabel()).isEqualTo("Mainly practical or experiential");
        UniversityModuleClassification evidence = reloaded.items().get(4).classification();
        assertThat(evidence.closeCallReasons()).containsExactly("human_demand 2.61 near HIGH 2.5");
        assertThat(evidence.dominanceGap()).isCloseTo(evidence.dominantValue() - evidence.competingValue(), within(1e-12));
        // The course-level assessment is untouched.
        assertThat(assessments.findById(assessment.id())).contains(assessment);
    }

    @Test
    void aStoredSnapshotIsNeverRegeneratedEvenUnderADifferentProvider() {
        var assessment = assessment(3);
        var original = fixtureProvider();
        var first = service(original).generateOrGet(assessment.id());

        UniversityModuleJudgmentProvider later = mock(UniversityModuleJudgmentProvider.class);
        when(later.judge(any())).thenAnswer(call -> ModuleAnalysisTestData.fixtureJudgments("computer-science",
                call.getArgument(0)));
        var again = service(later).generateOrGet(assessment.id(), Optional.of(List.of(1)));

        assertThat(again.reused()).isTrue();
        assertThat(again.snapshot()).isEqualTo(first.snapshot());
        verify(later, times(0)).judge(any());
        verify(original, times(1)).judge(any());
    }

    @Test
    void theDatabaseAllowsOneSnapshotPerAssessmentAndASecondCompletionNeverOverwritesTheFirst() {
        var assessment = assessment(3);
        var first = service(fixtureProvider()).generateOrGet(assessment.id()).snapshot();

        var canonical = store.saveCompletedIfAbsent(copy(first, UUID.randomUUID(), assessment.id(),
                UniversityModuleCategory.MIXED));

        assertThat(canonical).isEqualTo(first);
        assertThat(store.findCompletedByAssessmentId(assessment.id())).contains(first);
        assertThat(rows(assessment.id())).isEqualTo(1);
        assertThatThrownBy(() -> new JdbcTemplate(dataSource).update("insert into university_module_analysis (id, "
                + "assessment_id, status, schema_version, policy_version, judgment_source, course_title, "
                + "course_description, skills_developed, selected_module_count, created_at, completed_at) values (?, ?, "
                + "'COMPLETED', 'v', 'p', 'JEV', 't', 'd', '[]'::jsonb, 1, now(), now())", UUID.randomUUID(),
                assessment.id())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> new JdbcTemplate(dataSource).update("insert into university_module_analysis (id, "
                + "assessment_id, status, schema_version, policy_version, judgment_source, course_title, "
                + "course_description, skills_developed, selected_module_count, created_at, completed_at) values (?, ?, "
                + "'PENDING', 'v', 'p', 'JEV', 't', 'd', '[]'::jsonb, 1, now(), now())", UUID.randomUUID(),
                assessment(1).id())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void racingCompletionsBothReceiveTheOneCanonicalSnapshot() throws Exception {
        var template = service(fixtureProvider()).generateOrGet(assessment(3).id()).snapshot();
        var target = assessment(3);
        var one = copy(template, UUID.randomUUID(), target.id(), UniversityModuleCategory.TECHNICAL_CORE);
        var two = copy(template, UUID.randomUUID(), target.id(), UniversityModuleCategory.MIXED);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<UniversityModuleAnalysisSnapshot> first = () -> { start.await(); return store.saveCompletedIfAbsent(one); };
            Callable<UniversityModuleAnalysisSnapshot> second = () -> { start.await(); return store.saveCompletedIfAbsent(two); };
            var a = executor.submit(first);
            var b = executor.submit(second);
            start.countDown();
            assertThat(a.get()).isEqualTo(b.get());
            assertThat(a.get().snapshotId()).isIn(one.snapshotId(), two.snapshotId());
        }
        assertThat(rows(target.id())).isEqualTo(1);
    }

    @Test
    void aFailedAttemptStoresNoSnapshotIsRecordedAndARetrySucceeds() {
        var assessment = assessment(3);
        UniversityModuleJudgmentProvider failing = mock(UniversityModuleJudgmentProvider.class);
        when(failing.judge(any())).thenThrow(new UniversityModuleJudgmentException(
                UniversityModuleJudgmentException.Kind.PROVIDER_CALL, "the module-analysis provider call failed: 503", null));

        assertThatThrownBy(() -> service(failing).generateOrGet(assessment.id()))
                .isInstanceOf(UniversityModuleAnalysisFailedException.class);
        assertThat(store.findCompletedByAssessmentId(assessment.id())).isEmpty();
        assertThat(store.findFailuresByAssessmentId(assessment.id())).singleElement().satisfies(failure -> {
            assertThat(failure.category()).isEqualTo(UniversityModuleAnalysisFailure.Category.PROVIDER_TRANSIENT);
            assertThat(failure.retryable()).isTrue();
            assertThat(failure.errorType()).isEqualTo(UniversityModuleJudgmentException.class.getName());
            assertThat(failure.errorMessage()).contains("provider call failed: 503");
            assertThat(failure.policyVersion()).isEqualTo("university-module-policy-v1");
        });

        assertThat(service(fixtureProvider()).generateOrGet(assessment.id()).reused()).isFalse();
        assertThat(rows(assessment.id())).isEqualTo(1);
    }

    // ---- helpers ----

    private UniversityModuleAnalysisService service(UniversityModuleJudgmentProvider provider) {
        UniversityReportPurchaseUseCase purchases = mock(UniversityReportPurchaseUseCase.class);
        when(purchases.findEntitlement(any())).thenAnswer(call -> Optional.of(ModuleAnalysisTestData.entitlement(
                call.getArgument(0), UniversityEntitlementStatus.ACTIVE)));
        return new UniversityModuleAnalysisService(assessments, purchases, () -> Optional.of(provider),
                UniversityModuleClassifier.v1(new UniversityModulePolicyV1()), store, CLOCK, AssessmentTrace.disabled());
    }

    private static UniversityModuleJudgmentProvider fixtureProvider() {
        UniversityModuleJudgmentProvider provider = mock(UniversityModuleJudgmentProvider.class);
        when(provider.judge(any())).thenAnswer(call -> ModuleAnalysisTestData.fixtureJudgments("nursing",
                call.getArgument(0)));
        return provider;
    }

    private StoredUniversityAssessment assessment(int modules) {
        var assessment = ModuleAnalysisTestData.assessment(UUID.randomUUID(), modules);
        assessments.save(assessment);
        return assessments.findById(assessment.id()).orElseThrow();
    }

    private int rows(UUID assessmentId) {
        return new JdbcTemplate(dataSource).queryForObject("select count(*) from university_module_analysis where "
                + "assessment_id = ?", Integer.class, assessmentId);
    }

    /** The same snapshot under a new id and assessment, with every module given a different category. */
    private static UniversityModuleAnalysisSnapshot copy(UniversityModuleAnalysisSnapshot source, UUID snapshotId,
            UUID assessmentId, UniversityModuleCategory category) {
        var items = source.items().stream().map(item -> {
            var c = item.classification();
            return new UniversityModuleAnalysisItem(item.moduleId(), item.judgments(), new UniversityModuleClassification(
                    c.moduleKey(), c.moduleTitle(), category, c.rule(), c.inputs(), c.dominantValue(), c.competingValue(),
                    c.dominanceGap(), c.flags(), c.closeCallReasons()), category.customerLabel());
        }).toList();
        return new UniversityModuleAnalysisSnapshot(snapshotId, assessmentId, source.schemaVersion(),
                source.policyVersion(), source.judgmentSource(), source.model(), source.providerRequestId(),
                source.inputTokens(), source.outputTokens(), source.courseTitle(), source.courseDescription(),
                source.skillsDeveloped(), items, source.createdAt(), source.completedAt());
    }
}
