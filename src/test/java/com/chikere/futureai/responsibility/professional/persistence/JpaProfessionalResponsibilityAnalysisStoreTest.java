package com.chikere.futureai.responsibility.professional.persistence;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentProvider;
import com.chikere.futureai.payment.professional.application.ProfessionalReportEntitlement;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.responsibility.professional.analysis.HandBuiltResponsibilityJudgments;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisService;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisStore;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisFailedException;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisFailure;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisItem;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentException;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSource;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityJudgmentKind;
import com.chikere.futureai.trace.AssessmentTrace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The real store on migrated PostgreSQL, and the lifecycle running against it. No provider is ever contacted. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JpaProfessionalResponsibilityAnalysisStoreTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T09:00:00Z"), ZoneOffset.UTC);

    @MockitoBean ProfessionalJudgmentProvider unusedLiveProvider;
    @MockitoBean ProfessionalNarrativeGenerator unusedNarrativeGenerator;
    @MockitoBean com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider unusedUniversityProvider;
    @MockitoBean com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator unusedUniversityNarrative;
    @MockitoBean com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider unusedALevelProvider;
    @MockitoBean com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator unusedALevelNarrative;

    @Autowired ProfessionalAssessmentStore assessments;
    @Autowired ProfessionalResponsibilityAnalysisStore store;
    @Autowired DataSource dataSource;

    @Test
    void theCompleteSnapshotRoundTripsWithEveryJudgmentDistributionVersionAndClassification() {
        var assessment = assessment(List.of("Prepare monthly accounts", "Meet department managers", "Install equipment on site"));
        var provider = providerAnsweringPerResponsibility();
        var first = service(provider).generateOrGet(assessment.id());

        var reloaded = store.findCompletedByAssessmentId(assessment.id()).orElseThrow();
        assertThat(reloaded).isEqualTo(first.snapshot());
        assertThat(reloaded.schemaVersion()).isEqualTo("professional-responsibility-v1");
        assertThat(reloaded.policyVersion()).isEqualTo("professional-responsibility-policy-v1");
        assertThat(reloaded.judgmentSource()).isEqualTo(ResponsibilityJudgmentSource.SYNTHETIC_FIXTURE);
        assertThat(reloaded.model()).contains("hand-built");
        assertThat(reloaded.providerRequestId()).contains("req_hand_built");
        assertThat(reloaded.inputTokens()).hasValue(100);
        assertThat(reloaded.input().responsibilities()).containsExactly("Prepare monthly accounts",
                "Meet department managers", "Install equipment on site");
        assertThat(reloaded.items()).extracting(ResponsibilityAnalysisItem::category).containsExactly(
                ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY, ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING,
                ResponsibilityCategory.MAINLY_HANDS_ON_OR_IN_PERSON);
        var judgment = reloaded.items().getFirst().judgments().get(ResponsibilityJudgmentKind.ASSISTANCE_SCOPE);
        assertThat(judgment.value()).isEqualTo(3.4);
        assertThat(judgment.distribution()).containsOnlyKeys(3, 4);
        assertThat(judgment.distribution().get(3)).isCloseTo(0.6, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(judgment.providerConfidence()).hasValue(0.7);
        assertThat(judgment.nearestLevel()).hasValue(3);
        assertThat(judgment.nearestLabel()).contains("Hand-built level label");
        assertThat(reloaded.regulatedContext().distribution()).containsOnlyKeys(1, 2);
        assertThat(reloaded.items().get(2).oversightRule()).contains("O1_ACCOUNTABILITY_VERY_HIGH");
        assertThat(reloaded.items()).extracting(ResponsibilityAnalysisItem::classificationRule).containsExactly(
                "R4_ASSISTANCE_HIGH_HUMAN_DEMAND_NOT_HIGH", "R2_HUMAN_DEMAND_DOMINATES", "R1_PHYSICAL_DOMINANT");
        // The role-level assessment is untouched.
        assertThat(assessments.findById(assessment.id())).contains(assessment);
    }

    @Test
    void theProviderIsCalledOnceAndAStoredSnapshotIsNeverRegeneratedEvenUnderADifferentProvider() {
        var assessment = assessment(List.of("Prepare monthly accounts"));
        var original = providerAnsweringPerResponsibility();
        var first = service(original).generateOrGet(assessment.id());
        verify(original, times(1)).judge(any());

        // A later environment whose provider would judge very differently.
        ProfessionalResponsibilityJudgmentProvider later = mock(ProfessionalResponsibilityJudgmentProvider.class);
        when(later.judge(any())).thenAnswer(call -> HandBuiltResponsibilityJudgments.forRequest(call.getArgument(0),
                0.2, 3.9, 3.9, 3.9, 3.9));
        var again = service(later).generateOrGet(assessment.id());

        assertThat(again.reused()).isTrue();
        assertThat(again.snapshot()).isEqualTo(first.snapshot());
        assertThat(again.snapshot().items().getFirst().category()).isEqualTo(ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY);
        verify(later, times(0)).judge(any());
        verify(original, times(1)).judge(any());
    }

    @Test
    void theDatabaseAllowsOneSnapshotPerAssessmentAndASecondCompletionNeverOverwritesTheFirst() {
        var assessment = assessment(List.of("Prepare monthly accounts"));
        var first = service(providerAnsweringPerResponsibility()).generateOrGet(assessment.id()).snapshot();
        var competing = copyWithNewIdAndCategory(first, ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING);

        var canonical = store.saveCompletedIfAbsent(competing);

        assertThat(canonical).isEqualTo(first);
        assertThat(rows(assessment.id())).isEqualTo(1);
        assertThatThrownBy(() -> new JdbcTemplate(dataSource).update("insert into professional_responsibility_analysis "
                + "(id, assessment_id, status, schema_version, policy_version, judgment_source, profession, work_description, "
                + "skills, regulated_context_value, regulated_context_level, regulated_context_distribution, policy_parameters, "
                + "created_at, completed_at) values (?, ?, 'COMPLETED', 'v', 'p', 'JEV', 'x', 'y', '[]'::jsonb, 1, 'LOW', "
                + "'{}'::jsonb, '{}'::jsonb, now(), now())", UUID.randomUUID(), assessment.id()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void racingCompletionsBothReceiveTheOneCanonicalSnapshot() throws Exception {
        var assessment = assessment(List.of("Prepare monthly accounts"));
        // Build a valid snapshot without storing it, then race two distinct candidates for the same assessment.
        var template = service(providerAnsweringPerResponsibility()).generateOrGet(assessment(List.of("Prepare monthly accounts")).id())
                .snapshot();
        var one = retarget(copyWithNewIdAndCategory(template, ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY), assessment.id());
        var two = retarget(copyWithNewIdAndCategory(template, ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING), assessment.id());
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<ProfessionalResponsibilityAnalysisSnapshot> first = () -> { start.await(); return store.saveCompletedIfAbsent(one); };
            Callable<ProfessionalResponsibilityAnalysisSnapshot> second = () -> { start.await(); return store.saveCompletedIfAbsent(two); };
            var a = executor.submit(first);
            var b = executor.submit(second);
            start.countDown();
            assertThat(a.get().snapshotId()).isEqualTo(b.get().snapshotId());
        }
        assertThat(rows(assessment.id())).isEqualTo(1);
    }

    @Test
    void aFailedAttemptStoresNoSnapshotIsRecordedAndARetrySucceeds() {
        var assessment = assessment(List.of("Prepare monthly accounts"));
        ProfessionalResponsibilityJudgmentProvider failing = mock(ProfessionalResponsibilityJudgmentProvider.class);
        when(failing.judge(any())).thenThrow(new ResponsibilityJudgmentException("Jev returned 503"));

        assertThatThrownBy(() -> service(failing).generateOrGet(assessment.id()))
                .isInstanceOf(ResponsibilityAnalysisFailedException.class);
        assertThat(store.findCompletedByAssessmentId(assessment.id())).isEmpty();
        assertThat(store.findFailuresByAssessmentId(assessment.id())).singleElement().satisfies(failure -> {
            assertThat(failure.stage()).isEqualTo(ResponsibilityAnalysisFailure.Stage.PROVIDER);
            assertThat(failure.errorMessage()).contains("Jev returned 503");
            assertThat(failure.schemaVersion()).isEqualTo("professional-responsibility-v1");
        });

        assertThat(service(providerAnsweringPerResponsibility()).generateOrGet(assessment.id()).reused()).isFalse();
        assertThat(rows(assessment.id())).isEqualTo(1);
    }

    // ---- Helpers ----

    private ProfessionalResponsibilityAnalysisService service(ProfessionalResponsibilityJudgmentProvider provider) {
        ProfessionalReportPurchaseUseCase purchases = mock(ProfessionalReportPurchaseUseCase.class);
        when(purchases.findEntitlement(any())).thenAnswer(call -> Optional.of(new ProfessionalReportEntitlement(
                UUID.randomUUID(), call.getArgument(0), ProfessionalReportTier.PAID, ProfessionalEntitlementStatus.ACTIVE,
                ProfessionalPaymentProvider.COMPLIMENTARY, "complimentary_test", URI.create("http://localhost/report"),
                "complimentary", Optional.empty(), Optional.of("professional-report-v2"), Instant.now(CLOCK),
                Optional.of(Instant.now(CLOCK)))));
        return new ProfessionalResponsibilityAnalysisService(assessments, purchases, () -> Optional.of(provider),
                new ProfessionalResponsibilityPolicyV1(), store, CLOCK, AssessmentTrace.disabled());
    }

    /** Hand-built values chosen per responsibility so the three categories differ. */
    private static ProfessionalResponsibilityJudgmentProvider providerAnsweringPerResponsibility() {
        ProfessionalResponsibilityJudgmentProvider provider = mock(ProfessionalResponsibilityJudgmentProvider.class);
        when(provider.judge(any())).thenAnswer(call -> {
            com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest request =
                    call.getArgument(0);
            var base = HandBuiltResponsibilityJudgments.forRequest(request);
            var sets = new java.util.ArrayList<com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet>();
            for (var set : base.responsibilities()) {
                double[] v = switch (set.responsibilityIndex()) {
                    case 1 -> new double[] {3.4, 1.6, 1.2, 0.2, 2.0};
                    case 2 -> new double[] {2.6, 2.8, 3.6, 0.4, 2.2};
                    default -> new double[] {1.4, 2.0, 1.8, 3.8, 3.8};
                };
                sets.add(new com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet(
                        set.responsibilityIndex(), set.responsibilityKey(), set.responsibilityText(), 0.4,
                        HandBuiltResponsibilityJudgments.judgment(v[0]), HandBuiltResponsibilityJudgments.judgment(v[1]),
                        HandBuiltResponsibilityJudgments.judgment(v[2]), HandBuiltResponsibilityJudgments.judgment(v[3]),
                        HandBuiltResponsibilityJudgments.judgment(v[4])));
            }
            return new com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult(
                    base.schemaVersion(), base.source(), base.model(), base.providerRequestId(), base.inputTokens(),
                    base.outputTokens(), base.regulatedContext(), sets);
        });
        return provider;
    }

    private StoredProfessionalAssessment assessment(List<String> responsibilities) {
        var state = new ProfessionalAssessmentState("Management Accountant", "I prepare accounts and advise managers.",
                responsibilities, List.of("Excel"), OptionalInt.of(7));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var decision = new ProfessionalDecisionPolicyV2().decide(judgments);
        var assessment = new StoredProfessionalAssessment(UUID.randomUUID(), Instant.parse("2026-10-27T08:00:00Z"), state,
                judgments, new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        assessments.save(assessment);
        return assessments.findById(assessment.id()).orElseThrow();
    }

    private static ProfessionalResponsibilityAnalysisSnapshot copyWithNewIdAndCategory(
            ProfessionalResponsibilityAnalysisSnapshot source, ResponsibilityCategory category) {
        var items = source.items().stream().map(item -> new ResponsibilityAnalysisItem(item.responsibilityIndex(),
                item.responsibilityKey(), item.responsibilityText(), item.describedClearly(), item.judgments(), item.levels(),
                category, category.label(), item.needsCloseHumanOversight(), item.oversightRule(), item.classificationRule(),
                item.closeCall())).toList();
        return new ProfessionalResponsibilityAnalysisSnapshot(UUID.randomUUID(), source.assessmentId(), source.status(),
                source.schemaVersion(), source.policyVersion(), source.judgmentSource(), source.model(),
                source.providerRequestId(), source.inputTokens(), source.outputTokens(), source.input(),
                source.regulatedContext(), source.regulatedContextLevel(), source.policyParameters(), items,
                source.createdAt(), source.completedAt());
    }

    private static ProfessionalResponsibilityAnalysisSnapshot retarget(ProfessionalResponsibilityAnalysisSnapshot source,
            UUID assessmentId) {
        return new ProfessionalResponsibilityAnalysisSnapshot(source.snapshotId(), assessmentId, source.status(),
                source.schemaVersion(), source.policyVersion(), source.judgmentSource(), source.model(),
                source.providerRequestId(), source.inputTokens(), source.outputTokens(), source.input(),
                source.regulatedContext(), source.regulatedContextLevel(), source.policyParameters(), source.items(),
                source.createdAt(), source.completedAt());
    }

    private int rows(UUID assessmentId) {
        return new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from professional_responsibility_analysis where assessment_id = ?", Integer.class, assessmentId);
    }
}
