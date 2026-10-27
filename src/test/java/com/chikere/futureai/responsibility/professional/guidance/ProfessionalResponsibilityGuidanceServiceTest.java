package com.chikere.futureai.responsibility.professional.guidance;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus;
import com.chikere.futureai.payment.professional.application.ProfessionalPaymentProvider;
import com.chikere.futureai.payment.professional.application.ProfessionalReportEntitlement;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.responsibility.professional.analysis.HandBuiltResponsibilityJudgments;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisService;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisStore;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisFailure;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisUnavailableException;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisUnavailableException.Reason;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceLogCapture;
import com.chikere.futureai.trace.TraceStage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** The guidance lifecycle with the real 4D analysis service and a mocked responsibility provider: no Jev, no OpenAI. */
class ProfessionalResponsibilityGuidanceServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T10:00:00Z"), ZoneOffset.UTC);

    private final ProfessionalAssessmentStore assessments = mock(ProfessionalAssessmentStore.class);
    private final ProfessionalReportPurchaseUseCase purchases = mock(ProfessionalReportPurchaseUseCase.class);
    private final ProfessionalResponsibilityJudgmentProvider provider = mock(ProfessionalResponsibilityJudgmentProvider.class);
    private final InMemoryAnalysisStore analysisStore = new InMemoryAnalysisStore();
    private final InMemoryGuidanceStore guidanceStore = new InMemoryGuidanceStore();
    private final CountingPolicy policy = new CountingPolicy();

    @BeforeEach
    void providerAnswersAnyRequest() {
        when(provider.judge(any())).thenAnswer(call -> HandBuiltResponsibilityJudgments.forRequest(call.getArgument(0)));
    }

    @Test
    void withoutAnActiveEntitlementNothingIsAnalysedOrGuided() {
        UUID none = assessment(List.of("Write weekly reports"));
        assertUnavailable(none, Optional.empty(), Reason.NOT_ENTITLED);
        UUID refunded = assessment(List.of("Write weekly reports"));
        entitlement(refunded, ProfessionalEntitlementStatus.REFUNDED);
        assertUnavailable(refunded, Optional.empty(), Reason.NOT_ENTITLED);
        verifyNoInteractions(provider);
        assertThat(policy.calls).isZero();
        assertThat(guidanceStore.snapshots).isEmpty();
    }

    @Test
    void aStoredGuidanceIsNotReturnedOnceTheEntitlementIsNoLongerActive() {
        UUID id = paid(List.of("Write weekly reports"));
        var stored = service().generateOrGet(id).snapshot();
        entitlement(id, ProfessionalEntitlementStatus.REVOKED);
        assertUnavailable(id, Optional.empty(), Reason.NOT_ENTITLED);
        assertThat(guidanceStore.snapshots.get(id)).isEqualTo(stored);
    }

    @Test
    void theFirstCallGeneratesTheAnalysisThroughTheAnalysisServiceThenTheGuidanceOnceAndTheSecondReusesBoth() {
        UUID id = paid(List.of("Write weekly reports", "Meet clients"));
        try (TraceLogCapture log = TraceLogCapture.start()) {
            var service = service(AssessmentTrace.enabled());
            var first = service.generateOrGet(id);
            var second = service.generateOrGet(id);

            assertThat(first.reused()).isFalse();
            assertThat(first.analysisReused()).isFalse();
            assertThat(second.reused()).isTrue();
            assertThat(second.snapshot()).isEqualTo(first.snapshot());
            verify(provider, times(1)).judge(any());
            assertThat(policy.calls).isEqualTo(1);

            var snapshot = first.snapshot();
            assertThat(snapshot.assessmentId()).isEqualTo(id);
            assertThat(snapshot.analysisSnapshotId()).isEqualTo(analysisStore.snapshots.get(id).snapshotId());
            assertThat(snapshot.policyVersion()).isEqualTo("professional-guidance-policy-v1");
            assertThat(snapshot.createdAt()).isEqualTo(CLOCK.instant());

            var events = log.events(TraceStage.RESPONSIBILITY_GUIDANCE);
            assertThat(events).extracting(event -> event.at("/payload/status").asText()).containsExactly("GENERATED", "REUSED");
            assertThat(events).allSatisfy(event -> assertThat(event.get("assessmentId").asText()).isEqualTo(id.toString()));
            // Rule IDs and keys only: no responsibility text, so the guidance trace adds no user-entered content.
            assertThat(events.getFirst().get("payload").toString()).doesNotContain("Write weekly reports", "Meet clients");
        }
    }

    @Test
    void anExistingAnalysisIsUsedWithoutAnyProviderCall() {
        UUID id = paid(List.of("Write weekly reports"));
        new ProfessionalResponsibilityAnalysisService(assessments, purchases, () -> Optional.of(provider),
                new ProfessionalResponsibilityPolicyV1(), analysisStore, CLOCK, AssessmentTrace.disabled()).generateOrGet(id);
        verify(provider, times(1)).judge(any());

        var outcome = service().generateOrGet(id);
        assertThat(outcome.reused()).isFalse();
        assertThat(outcome.analysisReused()).isTrue();
        verify(provider, times(1)).judge(any());
    }

    @Test
    void aStoredGuidanceIsReturnedUnchangedEvenIfTodaysPolicyWouldDecideDifferently() {
        UUID id = paid(List.of("Write weekly reports"));
        var stored = service().generateOrGet(id).snapshot();
        policy.override = analysis -> {
            throw new AssertionError("the policy must not run for a stored guidance");
        };
        assertThat(service().generateOrGet(id).snapshot()).isEqualTo(stored);
        assertThat(policy.calls).isEqualTo(1);
    }

    @Test
    void moreThanEightResponsibilitiesRequireAnExplicitSelectionAndAreNeverTruncated() {
        UUID id = paid(numbered(10));
        assertUnavailable(id, Optional.empty(), Reason.RESPONSIBILITY_SELECTION_REQUIRED);
        verifyNoInteractions(provider);
        assertThat(guidanceStore.snapshots).isEmpty();

        assertUnavailable(id, Optional.of(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9)), Reason.INVALID_SELECTION);
        assertUnavailable(id, Optional.of(List.of(1, 1)), Reason.INVALID_SELECTION);
        assertUnavailable(id, Optional.of(List.of(11)), Reason.INVALID_SELECTION);
        assertUnavailable(id, Optional.of(List.of()), Reason.INVALID_SELECTION);
        verifyNoInteractions(provider);

        var outcome = service().generateOrGet(id, Optional.of(List.of(10, 2, 7)));
        ArgumentCaptor<ProfessionalResponsibilityAnalysisRequest> request =
                ArgumentCaptor.forClass(ProfessionalResponsibilityAnalysisRequest.class);
        verify(provider).judge(request.capture());
        // The chosen responsibilities, in the order the customer entered them.
        assertThat(request.getValue().responsibilities()).containsExactly("Responsibility 2", "Responsibility 7",
                "Responsibility 10");
        assertThat(outcome.snapshot().guidance().analysisSnapshotId())
                .isEqualTo(analysisStore.snapshots.get(id).snapshotId());
    }

    @Test
    void aPolicyThatIgnoresTheStoredAnalysisIsRejectedAndNothingIsStored() {
        UUID id = paid(List.of("Write weekly reports"));
        policy.override = analysis -> {
            var real = new ProfessionalGuidancePolicyV1().decide(analysis);
            return new ProfessionalResponsibilityGuidance(real.policyVersion(), UUID.randomUUID(), real.opportunities(),
                    real.humanValueAreas(), real.skillPriorities(), real.plan(), real.safeguards());
        };
        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOf(IllegalStateException.class);
        assertThat(guidanceStore.snapshots).isEmpty();
    }

    @Test
    void whenAnotherRequestStoredGuidanceFirstItsCanonicalSnapshotIsReturned() {
        UUID id = paid(List.of("Write weekly reports"));
        var winner = service().generateOrGet(id).snapshot();
        guidanceStore.hideOnNextFind = true;
        var loser = service().generateOrGet(id);
        assertThat(loser.reused()).isTrue();
        assertThat(loser.snapshot()).isEqualTo(winner);
        assertThat(guidanceStore.snapshots).hasSize(1);
    }

    @Test
    void findOnlyReads() {
        UUID id = paid(List.of("Write weekly reports"));
        assertThat(service().find(id)).isEmpty();
        verifyNoInteractions(provider);
        assertThat(policy.calls).isZero();
        verify(assessments, never()).save(any());
    }

    // ---- Helpers ----

    private ProfessionalResponsibilityGuidanceService service() {
        return service(AssessmentTrace.disabled());
    }

    private ProfessionalResponsibilityGuidanceService service(AssessmentTrace trace) {
        var analyses = new ProfessionalResponsibilityAnalysisService(assessments, purchases, () -> Optional.of(provider),
                new ProfessionalResponsibilityPolicyV1(), analysisStore, CLOCK, trace);
        return new ProfessionalResponsibilityGuidanceService(purchases, analyses, policy, guidanceStore, CLOCK, trace);
    }

    private void assertUnavailable(UUID id, Optional<List<Integer>> selection, Reason reason) {
        assertThatThrownBy(() -> service().generateOrGet(id, selection)).isInstanceOfSatisfying(
                ResponsibilityAnalysisUnavailableException.class, unavailable -> assertThat(unavailable.reason()).isEqualTo(reason));
    }

    private UUID paid(List<String> responsibilities) {
        UUID id = assessment(responsibilities);
        entitlement(id, ProfessionalEntitlementStatus.ACTIVE);
        return id;
    }

    private UUID assessment(List<String> responsibilities) {
        UUID id = UUID.randomUUID();
        var state = new ProfessionalAssessmentState("Test role", "Work described for the test.", responsibilities,
                List.of("Communication"), OptionalInt.of(5));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("registered_nurse").judge(state);
        var decision = new ProfessionalDecisionPolicyV2().decide(judgments);
        when(assessments.findById(id)).thenReturn(Optional.of(new StoredProfessionalAssessment(id,
                Instant.parse("2026-10-26T09:00:00Z"), state, judgments, new ProfessionalAssessmentResult(decision,
                        AssessmentSource.LOCAL_FIXTURE, judgments.schemaVersion(), decision.policyVersion()),
                Optional.empty())));
        return id;
    }

    private void entitlement(UUID id, ProfessionalEntitlementStatus status) {
        boolean active = status == ProfessionalEntitlementStatus.ACTIVE;
        Instant at = Instant.parse("2026-10-26T09:05:00Z");
        when(purchases.findEntitlement(id)).thenReturn(Optional.of(new ProfessionalReportEntitlement(UUID.randomUUID(),
                id, ProfessionalReportTier.PAID, status, ProfessionalPaymentProvider.COMPLIMENTARY, "session_for_test",
                URI.create("http://localhost/checkout"), "price_for_test", Optional.empty(),
                active ? Optional.of("professional-report-v2") : Optional.empty(), at,
                active ? Optional.of(at) : Optional.empty())));
    }

    private static List<String> numbered(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(index -> "Responsibility " + index).toList();
    }

    /** The real v1 guidance policy, counting calls, optionally replaced. */
    static final class CountingPolicy implements ProfessionalResponsibilityGuidancePolicy {
        private final ProfessionalGuidancePolicyV1 policy = new ProfessionalGuidancePolicyV1();
        int calls;
        ProfessionalResponsibilityGuidancePolicy override;

        @Override
        public ProfessionalResponsibilityGuidance decide(ProfessionalResponsibilityAnalysisSnapshot analysis) {
            calls++;
            return override == null ? policy.decide(analysis) : override.decide(analysis);
        }
    }

    static final class InMemoryAnalysisStore implements ProfessionalResponsibilityAnalysisStore {
        final Map<UUID, ProfessionalResponsibilityAnalysisSnapshot> snapshots = new HashMap<>();
        final List<ResponsibilityAnalysisFailure> failures = new ArrayList<>();

        @Override
        public Optional<ProfessionalResponsibilityAnalysisSnapshot> findCompletedByAssessmentId(UUID assessmentId) {
            return Optional.ofNullable(snapshots.get(assessmentId));
        }

        @Override
        public ProfessionalResponsibilityAnalysisSnapshot saveCompletedIfAbsent(ProfessionalResponsibilityAnalysisSnapshot snapshot) {
            return snapshots.computeIfAbsent(snapshot.assessmentId(), key -> snapshot);
        }

        @Override
        public void recordFailure(ResponsibilityAnalysisFailure failure) {
            failures.add(failure);
        }

        @Override
        public List<ResponsibilityAnalysisFailure> findFailuresByAssessmentId(UUID assessmentId) {
            return failures.stream().filter(failure -> failure.assessmentId().equals(assessmentId)).toList();
        }
    }

    /** Insert-once semantics, as the database enforces them. */
    static final class InMemoryGuidanceStore implements ProfessionalResponsibilityGuidanceStore {
        final Map<UUID, ProfessionalResponsibilityGuidanceSnapshot> snapshots = new HashMap<>();
        boolean hideOnNextFind;

        @Override
        public Optional<ProfessionalResponsibilityGuidanceSnapshot> findByAssessmentId(UUID assessmentId) {
            if (hideOnNextFind) {
                hideOnNextFind = false;
                return Optional.empty();
            }
            return Optional.ofNullable(snapshots.get(assessmentId));
        }

        @Override
        public ProfessionalResponsibilityGuidanceSnapshot saveIfAbsent(ProfessionalResponsibilityGuidanceSnapshot snapshot) {
            return snapshots.computeIfAbsent(snapshot.assessmentId(), key -> snapshot);
        }
    }
}
