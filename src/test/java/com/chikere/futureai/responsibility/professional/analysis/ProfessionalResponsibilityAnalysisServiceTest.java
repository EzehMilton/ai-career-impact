package com.chikere.futureai.responsibility.professional.analysis;

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
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentException;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityClassificationResult;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityClassifier;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;
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
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProfessionalResponsibilityAnalysisServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T09:00:00Z"), ZoneOffset.UTC);

    private final ProfessionalAssessmentStore assessments = mock(ProfessionalAssessmentStore.class);
    private final ProfessionalReportPurchaseUseCase purchases = mock(ProfessionalReportPurchaseUseCase.class);
    private final ProfessionalResponsibilityJudgmentProvider provider = mock(ProfessionalResponsibilityJudgmentProvider.class);
    private final CountingClassifier policy = new CountingClassifier();
    private final InMemoryStore store = new InMemoryStore();

    @BeforeEach
    void providerAnswersAnyRequest() {
        when(provider.judge(any())).thenAnswer(call -> HandBuiltResponsibilityJudgments.forRequest(call.getArgument(0)));
    }

    // ---- Eligibility ----

    @Test
    void anActivePaidOrComplimentaryEntitlementIsAllowed() {
        for (ProfessionalPaymentProvider source : List.of(ProfessionalPaymentProvider.STRIPE,
                ProfessionalPaymentProvider.COMPLIMENTARY)) {
            UUID id = assessment(List.of("Write weekly reports"));
            entitlement(id, ProfessionalEntitlementStatus.ACTIVE, source);
            assertThat(service().generateOrGet(id).reused()).as(source.name()).isFalse();
        }
        verify(provider, times(2)).judge(any());
    }

    @Test
    void aMissingOrInactiveEntitlementIsRefusedWithoutCallingTheProvider() {
        UUID none = assessment(List.of("Write weekly reports"));
        assertUnavailable(none, ResponsibilityAnalysisUnavailableException.Reason.NOT_ENTITLED);
        for (ProfessionalEntitlementStatus status : List.of(ProfessionalEntitlementStatus.PENDING,
                ProfessionalEntitlementStatus.FAILED, ProfessionalEntitlementStatus.REFUNDED,
                ProfessionalEntitlementStatus.REVOKED)) {
            UUID id = assessment(List.of("Write weekly reports"));
            entitlement(id, status, ProfessionalPaymentProvider.STRIPE);
            assertUnavailable(id, ResponsibilityAnalysisUnavailableException.Reason.NOT_ENTITLED);
        }
        assertUnavailable(UUID.randomUUID(), ResponsibilityAnalysisUnavailableException.Reason.ASSESSMENT_NOT_FOUND);
        verifyNoInteractions(provider);
        assertThat(store.snapshots).isEmpty();
    }

    @Test
    void anExistingSnapshotIsReturnedOnlyWhileTheEntitlementIsActiveAndIsNeverRemovedOrRegenerated() {
        UUID id = paid(List.of("Write weekly reports"));
        var stored = service().generateOrGet(id).snapshot();

        var again = service().generateOrGet(id);
        assertThat(again.reused()).isTrue();
        assertThat(again.snapshot()).isEqualTo(stored);

        for (ProfessionalEntitlementStatus status : List.of(ProfessionalEntitlementStatus.REFUNDED,
                ProfessionalEntitlementStatus.REVOKED, ProfessionalEntitlementStatus.PENDING)) {
            entitlement(id, status, ProfessionalPaymentProvider.STRIPE);
            assertUnavailable(id, ResponsibilityAnalysisUnavailableException.Reason.NOT_ENTITLED);
        }
        when(purchases.findEntitlement(id)).thenReturn(Optional.empty());
        assertUnavailable(id, ResponsibilityAnalysisUnavailableException.Reason.NOT_ENTITLED);

        // Refused access neither deletes, changes nor regenerates the stored analysis.
        assertThat(store.snapshots.get(id)).isEqualTo(stored);
        assertThat(store.failures).isEmpty();
        verify(provider, times(1)).judge(any());
        assertThat(policy.calls).isEqualTo(1);

        // Access restored: the same stored snapshot, still without a provider call.
        entitlement(id, ProfessionalEntitlementStatus.ACTIVE, ProfessionalPaymentProvider.COMPLIMENTARY);
        assertThat(service().generateOrGet(id).snapshot()).isEqualTo(stored);
        verify(provider, times(1)).judge(any());
    }

    @Test
    void paymentConfirmationAndTheStripeWebhookNeverReachResponsibilityAnalysis() throws Exception {
        // The webhook only establishes the ACTIVE entitlement; no payment code can call the responsibility capability.
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/com/chikere/futureai/payment"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(java.nio.file.Files.readString(file)).as(file.toString())
                        .doesNotContain("com.chikere.futureai.responsibility", "ProfessionalResponsibilityAnalysisService");
            }
        }
    }

    @Test
    void withoutAConfiguredProviderNothingIsInvented() {
        UUID id = paid(List.of("Write weekly reports"));
        var service = new ProfessionalResponsibilityAnalysisService(assessments, purchases, Optional::empty, policy, store,
                CLOCK, AssessmentTrace.disabled());
        assertThatThrownBy(() -> service.generateOrGet(id)).isInstanceOfSatisfying(
                ResponsibilityAnalysisUnavailableException.class, unavailable -> assertThat(unavailable.reason())
                        .isEqualTo(ResponsibilityAnalysisUnavailableException.Reason.PROVIDER_NOT_CONFIGURED));
    }

    // ---- Selection ----

    @Test
    void theSelectionRuleRemovesBlanksAndKeepsOrderAndText() {
        assertThat(ResponsibilitySelection.select(java.util.Arrays.asList("First", " ", null, "Second ", "", "Third")))
                .containsExactly("First", "Second", "Third");
        assertThat(ResponsibilitySelection.select(numbered(8))).hasSize(8);
    }

    @Test
    void oneToEightResponsibilitiesAreAnalysedInOrder() {
        UUID one = paid(List.of("Only one"));
        assertThat(service().generateOrGet(one).snapshot().input().responsibilities()).containsExactly("Only one");

        UUID eight = paid(numbered(8));
        assertThat(service().generateOrGet(eight).snapshot().items()).hasSize(8);

        UUID ordered = paid(List.of("First", "Second", "Third"));
        var snapshot = service().generateOrGet(ordered).snapshot();
        assertThat(snapshot.input().responsibilities()).containsExactly("First", "Second", "Third");
        assertThat(snapshot.items()).extracting(ResponsibilityAnalysisItem::responsibilityKey)
                .containsExactly("r1", "r2", "r3");
        ArgumentCaptor<ProfessionalResponsibilityAnalysisRequest> request =
                ArgumentCaptor.forClass(ProfessionalResponsibilityAnalysisRequest.class);
        verify(provider, times(3)).judge(request.capture());
        assertThat(request.getValue().responsibilities()).containsExactly("First", "Second", "Third");
    }

    @Test
    void noResponsibilitiesMoreThanEightOrDuplicatesAreRefusedAndNeverTruncated() {
        assertUnavailable(paid(List.of()), ResponsibilityAnalysisUnavailableException.Reason.NO_RESPONSIBILITIES);
        assertThatThrownBy(() -> ResponsibilitySelection.select(List.of(" ", ""))).isInstanceOfSatisfying(
                ResponsibilityAnalysisUnavailableException.class, unavailable -> assertThat(unavailable.reason())
                        .isEqualTo(ResponsibilityAnalysisUnavailableException.Reason.NO_RESPONSIBILITIES));
        UUID nine = paid(numbered(9));
        assertThatThrownBy(() -> service().generateOrGet(nine)).isInstanceOfSatisfying(
                ResponsibilityAnalysisUnavailableException.class, unavailable -> {
                    assertThat(unavailable.reason()).isEqualTo(ResponsibilityAnalysisUnavailableException.Reason.RESPONSIBILITY_SELECTION_REQUIRED);
                    assertThat(unavailable.getMessage()).contains("at most 8").contains("none are dropped");
                });
        assertUnavailable(paid(List.of("Same", "Same")), ResponsibilityAnalysisUnavailableException.Reason.DUPLICATE_RESPONSIBILITIES);
        verifyNoInteractions(provider);
        assertThat(store.snapshots).isEmpty();
    }

    @Test
    void anExplicitSelectionKeepsTheChosenResponsibilitiesInTheirOriginalOrderAndRejectsInvalidChoices() {
        List<String> ten = numbered(10);
        assertThat(ResponsibilitySelection.selectExplicitly(ten, List.of(9, 1, 4)))
                .containsExactly("Responsibility 1", "Responsibility 4", "Responsibility 9");
        assertThat(ResponsibilitySelection.selectExplicitly(ten, List.of(1, 2, 3, 4, 5, 6, 7, 8))).hasSize(8);
        for (List<Integer> invalid : List.of(List.<Integer>of(), List.of(0), List.of(11), List.of(2, 2),
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9))) {
            assertThatThrownBy(() -> ResponsibilitySelection.selectExplicitly(ten, invalid)).as(invalid.toString())
                    .isInstanceOfSatisfying(ResponsibilityAnalysisUnavailableException.class, unavailable -> assertThat(
                            unavailable.reason()).isEqualTo(ResponsibilityAnalysisUnavailableException.Reason.INVALID_SELECTION));
        }
        // Positions count usable responsibilities only, so a blank entry is never chosen.
        assertThat(ResponsibilitySelection.selectExplicitly(java.util.Arrays.asList("First", " ", "Second"), List.of(2)))
                .containsExactly("Second");

        UUID nine = paid(numbered(9));
        var snapshot = service().generateOrGet(nine, Optional.of(List.of(3, 9))).snapshot();
        assertThat(snapshot.input().responsibilities()).containsExactly("Responsibility 3", "Responsibility 9");
        verify(provider, times(1)).judge(any());
    }

    // ---- Orchestration and reuse ----

    @Test
    void theFirstRequestCallsTheProviderAndPolicyOnceAndStoresTheCompleteSnapshotAndTheSecondReusesIt() {
        UUID id = paid(List.of("Write weekly reports", "Meet clients"));
        try (TraceLogCapture log = TraceLogCapture.start()) {
            var service = service(AssessmentTrace.enabled());
            var first = service.generateOrGet(id);
            var second = service.generateOrGet(id);

            assertThat(first.reused()).isFalse();
            assertThat(second.reused()).isTrue();
            assertThat(second.snapshot()).isEqualTo(first.snapshot());
            verify(provider, times(1)).judge(any());
            assertThat(policy.calls).isEqualTo(1);
            assertThat(log.events()).extracting(event -> event.get("stage").asText())
                    .containsSubsequence("RESPONSIBILITY_ANALYSIS_PERSISTED", "RESPONSIBILITY_ANALYSIS_REUSED");
            assertThat(log.only(TraceStage.RESPONSIBILITY_ANALYSIS_PERSISTED).at("/payload/providerCalled").asBoolean())
                    .isTrue();
            assertThat(log.only(TraceStage.RESPONSIBILITY_ANALYSIS_REUSED).at("/payload/providerCalled").asBoolean())
                    .isFalse();
            assertThat(log.only(TraceStage.RESPONSIBILITY_ANALYSIS_PERSISTED).get("assessmentId").asText())
                    .isEqualTo(id.toString());
        }
        var snapshot = store.snapshots.get(id);
        assertThat(snapshot.status()).isEqualTo(ResponsibilityAnalysisStatus.COMPLETED);
        assertThat(snapshot.schemaVersion()).isEqualTo("professional-responsibility-v1");
        assertThat(snapshot.policyVersion()).isEqualTo("professional-responsibility-policy-v1");
        assertThat(snapshot.model()).contains("hand-built");
        assertThat(snapshot.providerRequestId()).contains("req_hand_built");
        assertThat(snapshot.input().profession()).isEqualTo("Test role");
        assertThat(snapshot.input().skills()).containsExactly("Communication");
        assertThat(snapshot.policyParameters()).containsEntry("highFrom", 2.5).containsEntry("dominanceMargin", 0.25);
        assertThat(snapshot.items()).allSatisfy(item -> {
            assertThat(item.category()).isEqualTo(ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY);
            // Accountability 2.0 is below High, so the policy sets no oversight marker.
            assertThat(item.needsCloseHumanOversight()).isFalse();
            assertThat(item.judgments()).hasSize(5);
        });
        // Only read: the role-level assessment is never saved, rescored or re-judged.
        verify(assessments, never()).save(any());
    }

    @Test
    void aStoredSnapshotIsReturnedUnchangedEvenWhenTodaysProviderAndPolicyWouldDisagree() {
        UUID id = paid(List.of("Write weekly reports"));
        service().generateOrGet(id);
        var stored = store.snapshots.get(id);
        // Rewrite the stored classification so it no longer matches what the policy computes from its raw values.
        var item = stored.items().getFirst();
        var tampered = new ResponsibilityAnalysisItem(item.responsibilityIndex(), item.responsibilityKey(),
                item.responsibilityText(), item.describedClearly(), item.judgments(), item.levels(),
                ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING, "Human-led, with AI support", false, Optional.empty(),
                "R2_HUMAN_DEMAND_DOMINATES", false);
        store.snapshots.put(id, new ProfessionalResponsibilityAnalysisSnapshot(stored.snapshotId(), id, stored.status(),
                stored.schemaVersion(), stored.policyVersion(), stored.judgmentSource(), stored.model(),
                stored.providerRequestId(), stored.inputTokens(), stored.outputTokens(), stored.input(),
                stored.regulatedContext(), stored.regulatedContextLevel(), stored.policyParameters(), List.of(tampered),
                stored.createdAt(), stored.completedAt()));
        doAnswer(call -> HandBuiltResponsibilityJudgments.forRequest(call.getArgument(0), 0.5, 3.9, 3.9, 3.9, 3.9))
                .when(provider).judge(any());

        var later = service().generateOrGet(id);

        assertThat(later.reused()).isTrue();
        assertThat(later.snapshot().items().getFirst().category()).isEqualTo(ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING);
        assertThat(later.snapshot().items().getFirst().judgments()).isEqualTo(item.judgments());
        verify(provider, times(1)).judge(any());
        assertThat(policy.calls).isEqualTo(1);
    }

    // ---- Failure and retry ----

    @Test
    void aProviderFailureStoresNoSnapshotRecordsTheFailureAndCanBeRetried() {
        UUID id = paid(List.of("Write weekly reports"));
        doThrow(new ResponsibilityJudgmentException("Jev returned 503"))
                .doAnswer(call -> HandBuiltResponsibilityJudgments.forRequest(call.getArgument(0)))
                .when(provider).judge(any());
        try (TraceLogCapture log = TraceLogCapture.start()) {
            assertThatThrownBy(() -> service(AssessmentTrace.enabled()).generateOrGet(id))
                    .isInstanceOfSatisfying(ResponsibilityAnalysisFailedException.class,
                            failed -> assertThat(failed.stage()).isEqualTo(ResponsibilityAnalysisFailure.Stage.PROVIDER));
            assertThat(log.only(TraceStage.RESPONSIBILITY_ANALYSIS_FAILED).at("/payload/stage").asText()).isEqualTo("PROVIDER");
        }
        assertThat(store.snapshots).isEmpty();
        assertThat(store.failures).singleElement().satisfies(failure -> {
            assertThat(failure.stage()).isEqualTo(ResponsibilityAnalysisFailure.Stage.PROVIDER);
            assertThat(failure.errorMessage()).contains("Jev returned 503");
            assertThat(failure.policyVersion()).isEqualTo("professional-responsibility-policy-v1");
        });

        assertThat(service().generateOrGet(id).reused()).isFalse();
        assertThat(store.snapshots).containsKey(id);
        verify(provider, times(2)).judge(any());
    }

    @Test
    void anUnsupportedSchemaOrAMismatchedResultIsAFailureNotASnapshot() {
        UUID id = paid(List.of("Write weekly reports"));
        doAnswer(call -> {
            ProfessionalResponsibilityJudgmentResult valid = HandBuiltResponsibilityJudgments.forRequest(call.getArgument(0));
            return new ProfessionalResponsibilityJudgmentResult("professional-responsibility-v2", valid.source(),
                    valid.model(), valid.providerRequestId(), valid.inputTokens(), valid.outputTokens(),
                    valid.regulatedContext(), valid.responsibilities());
        }).when(provider).judge(any());
        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOf(ResponsibilityAnalysisFailedException.class);

        doAnswer(call -> HandBuiltResponsibilityJudgments.forRequest(
                new ProfessionalResponsibilityAnalysisRequest(id, "Test role", "Work.", List.of("Something else"), List.of())))
                .when(provider).judge(any());
        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOf(ResponsibilityAnalysisFailedException.class);
        assertThat(store.snapshots).isEmpty();
        assertThat(store.failures).hasSize(2);
    }

    @Test
    void aClassificationFailureStoresNoSnapshot() {
        UUID id = paid(List.of("Write weekly reports"));
        policy.failure = new IllegalStateException("policy fault");
        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOfSatisfying(
                ResponsibilityAnalysisFailedException.class,
                failed -> assertThat(failed.stage()).isEqualTo(ResponsibilityAnalysisFailure.Stage.CLASSIFICATION));
        assertThat(store.snapshots).isEmpty();
        assertThat(store.failures).extracting(ResponsibilityAnalysisFailure::stage)
                .containsExactly(ResponsibilityAnalysisFailure.Stage.CLASSIFICATION);
    }

    @Test
    void aPersistenceFailureIsNeverReportedAsSuccess() {
        UUID id = paid(List.of("Write weekly reports"));
        store.failNextSave = true;
        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOfSatisfying(
                ResponsibilityAnalysisFailedException.class,
                failed -> assertThat(failed.stage()).isEqualTo(ResponsibilityAnalysisFailure.Stage.PERSISTENCE));
        assertThat(store.snapshots).isEmpty();
    }

    @Test
    void whenAnotherRequestCompletedFirstItsCanonicalSnapshotIsReturned() {
        UUID id = paid(List.of("Write weekly reports"));
        var winner = service().generateOrGet(id).snapshot();
        store.hideOnNextFind = true; // as if this request had looked before the winner committed
        var loser = service().generateOrGet(id);

        assertThat(loser.reused()).isTrue();
        assertThat(loser.snapshot()).isEqualTo(winner);
        assertThat(store.snapshots).hasSize(1);
    }

    // ---- Helpers ----

    private ProfessionalResponsibilityAnalysisService service() {
        return service(AssessmentTrace.disabled());
    }

    private ProfessionalResponsibilityAnalysisService service(AssessmentTrace trace) {
        return new ProfessionalResponsibilityAnalysisService(assessments, purchases, () -> Optional.of(provider), policy,
                store, CLOCK, trace);
    }

    private void assertUnavailable(UUID id, ResponsibilityAnalysisUnavailableException.Reason reason) {
        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOfSatisfying(
                ResponsibilityAnalysisUnavailableException.class, unavailable -> assertThat(unavailable.reason()).isEqualTo(reason));
    }

    private UUID paid(List<String> responsibilities) {
        UUID id = assessment(responsibilities);
        entitlement(id, ProfessionalEntitlementStatus.ACTIVE, ProfessionalPaymentProvider.COMPLIMENTARY);
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

    private void entitlement(UUID id, ProfessionalEntitlementStatus status, ProfessionalPaymentProvider source) {
        boolean active = status == ProfessionalEntitlementStatus.ACTIVE;
        Instant at = Instant.parse("2026-10-26T09:05:00Z");
        when(purchases.findEntitlement(id)).thenReturn(Optional.of(new ProfessionalReportEntitlement(UUID.randomUUID(),
                id, ProfessionalReportTier.PAID, status, source, "session_for_test", URI.create("http://localhost/checkout"),
                "price_for_test", Optional.empty(), active ? Optional.of("professional-report-v2") : Optional.empty(), at,
                active ? Optional.of(at) : Optional.empty())));
    }

    /** The real v1 policy, counting calls and optionally failing, so the lifecycle can be observed. */
    static final class CountingClassifier implements ProfessionalResponsibilityClassifier {
        private final ProfessionalResponsibilityPolicyV1 policy = new ProfessionalResponsibilityPolicyV1();
        int calls;
        RuntimeException failure;

        @Override
        public ProfessionalResponsibilityClassificationResult classify(ProfessionalResponsibilityJudgmentResult judgments) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return policy.classify(judgments);
        }
    }

    private static List<String> numbered(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(index -> "Responsibility " + index).toList();
    }

    /** Insert-once semantics, as the database enforces them. */
    static final class InMemoryStore implements ProfessionalResponsibilityAnalysisStore {
        final Map<UUID, ProfessionalResponsibilityAnalysisSnapshot> snapshots = new HashMap<>();
        final List<ResponsibilityAnalysisFailure> failures = new ArrayList<>();
        boolean failNextSave;
        boolean hideOnNextFind;

        @Override
        public Optional<ProfessionalResponsibilityAnalysisSnapshot> findCompletedByAssessmentId(UUID assessmentId) {
            if (hideOnNextFind) {
                hideOnNextFind = false;
                return Optional.empty();
            }
            return Optional.ofNullable(snapshots.get(assessmentId));
        }

        @Override
        public ProfessionalResponsibilityAnalysisSnapshot saveCompletedIfAbsent(ProfessionalResponsibilityAnalysisSnapshot snapshot) {
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("database unavailable");
            }
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

}
