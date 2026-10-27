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
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisStore;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalGuidancePolicyV1;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidancePolicy;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceService;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceSnapshot;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceStore;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The guidance store on migrated PostgreSQL, and the guidance lifecycle running against it. No provider is contacted. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JpaProfessionalResponsibilityGuidanceStoreTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);

    @MockitoBean ProfessionalJudgmentProvider unusedLiveProvider;
    @MockitoBean ProfessionalNarrativeGenerator unusedNarrativeGenerator;
    @MockitoBean com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider unusedUniversityProvider;
    @MockitoBean com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator unusedUniversityNarrative;
    @MockitoBean com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider unusedALevelProvider;
    @MockitoBean com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator unusedALevelNarrative;

    @Autowired ProfessionalAssessmentStore assessments;
    @Autowired ProfessionalResponsibilityAnalysisStore analyses;
    @Autowired ProfessionalResponsibilityGuidanceStore store;
    @Autowired DataSource dataSource;

    @Test
    void theCompleteGuidanceRoundTripsLinkedToItsAnalysisAndIsReusedWithoutRecomputation() {
        var assessment = assessment(List.of("Prepare monthly accounts", "Meet department managers", "Install equipment on site"));
        var provider = provider();
        var calls = new AtomicInteger();
        var first = service(provider, calls).generateOrGet(assessment.id());

        var reloaded = store.findByAssessmentId(assessment.id()).orElseThrow();
        assertThat(reloaded).isEqualTo(first.snapshot());
        assertThat(reloaded.analysisSnapshotId()).isEqualTo(analyses.findCompletedByAssessmentId(assessment.id())
                .orElseThrow().snapshotId());
        assertThat(reloaded.policyVersion()).isEqualTo("professional-guidance-policy-v1");
        var guidance = reloaded.guidance();
        assertThat(guidance.opportunities()).isNotEmpty();
        assertThat(guidance.opportunities().getFirst().scoreComponents()).isNotEmpty();
        assertThat(guidance.humanValueAreas()).isNotEmpty();
        assertThat(guidance.skillPriorities()).isNotEmpty();
        assertThat(guidance.plan().days1To30()).isNotEmpty();
        assertThat(guidance.safeguards()).isNotEmpty();

        var again = service(provider, calls).generateOrGet(assessment.id());
        assertThat(again.reused()).isTrue();
        assertThat(again.snapshot()).isEqualTo(first.snapshot());
        assertThat(calls).hasValue(1);
        verify(provider, times(1)).judge(any());
    }

    @Test
    void oneGuidancePerAssessmentAndPerAnalysisAndASecondSaveNeverOverwritesTheFirst() {
        var assessment = assessment(List.of("Prepare monthly accounts"));
        var first = service(provider(), new AtomicInteger()).generateOrGet(assessment.id()).snapshot();
        var competing = new ProfessionalResponsibilityGuidanceSnapshot(UUID.randomUUID(), first.assessmentId(),
                first.analysisSnapshotId(), first.policyVersion(), first.createdAt().plusSeconds(60), first.guidance());

        assertThat(store.saveIfAbsent(competing)).isEqualTo(first);
        assertThat(rows(assessment.id())).isEqualTo(1);
        assertThatThrownBy(() -> insert(UUID.randomUUID(), assessment.id(), first.analysisSnapshotId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void guidanceMustReferenceAnExistingAssessmentAndAnalysis() {
        var assessment = assessment(List.of("Prepare monthly accounts"));
        assertThatThrownBy(() -> insert(UUID.randomUUID(), assessment.id(), UUID.randomUUID()))
                .isInstanceOf(DataIntegrityViolationException.class);
        var analysed = assessment(List.of("Prepare monthly accounts"));
        service(provider(), new AtomicInteger()).generateOrGet(analysed.id());
        UUID analysisId = analyses.findCompletedByAssessmentId(analysed.id()).orElseThrow().snapshotId();
        assertThatThrownBy(() -> insert(UUID.randomUUID(), UUID.randomUUID(), analysisId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void storedGuidanceIsReturnedAsStoredEvenIfItNoLongerMatchesTodaysPolicy() {
        var assessment = assessment(List.of("Prepare monthly accounts", "Meet department managers"));
        var calls = new AtomicInteger();
        service(provider(), calls).generateOrGet(assessment.id());
        // A later edit to the stored row (standing in for an older policy's output) is served unchanged.
        new JdbcTemplate(dataSource).update("update professional_responsibility_guidance set guidance = "
                + "jsonb_set(guidance, '{opportunities,0,rationale}', '\"An older rationale\"') where assessment_id = ?",
                assessment.id());

        var served = service(provider(), calls).generateOrGet(assessment.id()).snapshot();
        assertThat(served.guidance().opportunities().getFirst().rationale()).isEqualTo("An older rationale");
        assertThat(calls).hasValue(1);
    }

    // ---- Helpers ----

    private ProfessionalResponsibilityGuidanceService service(ProfessionalResponsibilityJudgmentProvider provider,
            AtomicInteger policyCalls) {
        ProfessionalReportPurchaseUseCase purchases = mock(ProfessionalReportPurchaseUseCase.class);
        when(purchases.findEntitlement(any())).thenAnswer(call -> Optional.of(new ProfessionalReportEntitlement(
                UUID.randomUUID(), call.getArgument(0), ProfessionalReportTier.PAID, ProfessionalEntitlementStatus.ACTIVE,
                ProfessionalPaymentProvider.COMPLIMENTARY, "complimentary_test", URI.create("http://localhost/report"),
                "complimentary", Optional.empty(), Optional.of("professional-report-v2"), Instant.now(CLOCK),
                Optional.of(Instant.now(CLOCK)))));
        var analysisService = new ProfessionalResponsibilityAnalysisService(assessments, purchases, () -> Optional.of(provider),
                new ProfessionalResponsibilityPolicyV1(), analyses, CLOCK, AssessmentTrace.disabled());
        var real = new ProfessionalGuidancePolicyV1();
        ProfessionalResponsibilityGuidancePolicy counting = analysis -> {
            policyCalls.incrementAndGet();
            return real.decide(analysis);
        };
        return new ProfessionalResponsibilityGuidanceService(purchases, analysisService, counting, store, CLOCK,
                AssessmentTrace.disabled());
    }

    /** Hand-built values per responsibility: a good opportunity, a human-led item and a hands-on item. */
    private static ProfessionalResponsibilityJudgmentProvider provider() {
        ProfessionalResponsibilityJudgmentProvider provider = mock(ProfessionalResponsibilityJudgmentProvider.class);
        when(provider.judge(any())).thenAnswer(call -> {
            ProfessionalResponsibilityAnalysisRequest request = call.getArgument(0);
            var base = HandBuiltResponsibilityJudgments.forRequest(request);
            var sets = new ArrayList<ResponsibilityJudgmentSet>();
            for (var set : base.responsibilities()) {
                double[] v = switch (set.responsibilityIndex()) {
                    case 1 -> new double[] {3.4, 1.6, 1.2, 0.2, 2.0};
                    case 2 -> new double[] {2.6, 2.8, 3.6, 0.4, 2.2};
                    default -> new double[] {1.4, 2.0, 1.8, 3.8, 3.8};
                };
                sets.add(new ResponsibilityJudgmentSet(set.responsibilityIndex(), set.responsibilityKey(),
                        set.responsibilityText(), 0.4, HandBuiltResponsibilityJudgments.judgment(v[0]),
                        HandBuiltResponsibilityJudgments.judgment(v[1]), HandBuiltResponsibilityJudgments.judgment(v[2]),
                        HandBuiltResponsibilityJudgments.judgment(v[3]), HandBuiltResponsibilityJudgments.judgment(v[4])));
            }
            return new ProfessionalResponsibilityJudgmentResult(base.schemaVersion(), base.source(), base.model(),
                    base.providerRequestId(), base.inputTokens(), base.outputTokens(), base.regulatedContext(), sets);
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

    private void insert(UUID id, UUID assessmentId, UUID analysisId) {
        new JdbcTemplate(dataSource).update("insert into professional_responsibility_guidance "
                + "(id, assessment_id, analysis_id, policy_version, created_at, guidance) values (?, ?, ?, 'p', now(), '{}'::jsonb)",
                id, assessmentId, analysisId);
    }

    private int rows(UUID assessmentId) {
        return new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from professional_responsibility_guidance where assessment_id = ?", Integer.class, assessmentId);
    }
}
