package com.chikere.futureai.coursemodule.university.analysis;

import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentStore;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisUnavailableException.Reason;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentException;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentProvider;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleClassification;
import com.chikere.futureai.coursemodule.university.policy.UniversityModulePolicyV1;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityReportEntitlement;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceLogCapture;
import com.chikere.futureai.trace.TraceStage;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The module-analysis lifecycle on in-memory ports: entitlement, selection, single generation, reuse and failure. */
class UniversityModuleAnalysisServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T09:00:00Z"), ZoneOffset.UTC);

    private final Assessments assessments = new Assessments();
    private final Purchases purchases = new Purchases();
    private final UniversityReportPurchaseUseCase purchaseUseCase = purchases.useCase();
    private final Store store = new Store();
    private final CountingClassifier classifier = new CountingClassifier();
    private final FixtureProvider provider = new FixtureProvider("nursing");

    // ---- generation and reuse ----

    @Test
    void theFirstRequestCallsJevOnceClassifiesOnceAndPersistsTheCompleteSnapshot() {
        UUID id = paidAssessment(8, UniversityEntitlementStatus.ACTIVE);

        var outcome = service().generateOrGet(id);

        assertThat(outcome.reused()).isFalse();
        assertThat(provider.requests).hasSize(1);
        assertThat(classifier.calls).hasValue(1);
        var snapshot = outcome.snapshot();
        assertThat(store.snapshots).containsEntry(id, snapshot);
        assertThat(snapshot.schemaVersion()).isEqualTo("university-module-v1");
        assertThat(snapshot.policyVersion()).isEqualTo("university-module-policy-v1");
        assertThat(snapshot.judgmentSource()).isEqualTo(UniversityModuleJudgmentSource.JEV);
        assertThat(snapshot.selectedModuleIds()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(snapshot.items()).extracting(item -> item.judgments().moduleKey())
                .containsExactly("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8");
        // The frozen Nursing judgments classify as reviewed in Stage 5D, with the customer labels kept.
        assertThat(snapshot.items()).extracting(item -> item.classification().category()).containsExactly(
                UniversityModuleCategory.PRACTICAL_OR_EXPERIENTIAL, UniversityModuleCategory.TECHNICAL_CORE,
                UniversityModuleCategory.TECHNICAL_CORE, UniversityModuleCategory.HUMAN_JUDGEMENT_LED,
                UniversityModuleCategory.HUMAN_JUDGEMENT_LED, UniversityModuleCategory.HUMAN_JUDGEMENT_LED,
                UniversityModuleCategory.HUMAN_JUDGEMENT_LED, UniversityModuleCategory.PRACTICAL_OR_EXPERIENTIAL);
        assertThat(snapshot.items().getFirst().displayLabel()).isEqualTo("Mainly practical or experiential");
        // Career areas are never sent; the course context is.
        var request = provider.requests.getFirst();
        assertThat(request.courseTitle()).isEqualTo("BSc Nursing (Adult)");
        assertThat(request.skillsDeveloped()).containsExactly("Clinical skills", "Communication");
    }

    @Test
    void aSecondRequestReturnsTheSameSnapshotWithNoJevCallAndNoReclassification() {
        UUID id = paidAssessment(8, UniversityEntitlementStatus.ACTIVE);
        var first = service().generateOrGet(id);

        var again = service().generateOrGet(id);

        assertThat(again.reused()).isTrue();
        assertThat(again.snapshot()).isEqualTo(first.snapshot());
        assertThat(provider.requests).hasSize(1);
        assertThat(classifier.calls).hasValue(1);
    }

    @Test
    void aLaterSelectionIsIgnoredOnceACompleteSnapshotExists() {
        UUID id = paidAssessment(10, UniversityEntitlementStatus.ACTIVE);
        var first = service().generateOrGet(id, Optional.of(List.of(10, 3, 7)));
        assertThat(first.snapshot().selectedModuleIds()).containsExactly(3, 7, 10);
        assertThat(provider.requests.getFirst().modules()).containsExactly("Module 3", "Module 7", "Module 10");

        try (TraceLogCapture log = TraceLogCapture.start()) {
            var again = service(AssessmentTrace.enabled()).generateOrGet(id, Optional.of(List.of(1, 2)));
            assertThat(again.snapshot().selectedModuleIds()).containsExactly(3, 7, 10);
            assertThat(log.only(TraceStage.UNIVERSITY_MODULE_ANALYSIS_REUSED).at("/payload/selectionIgnored").asText())
                    .contains("canonical");
        }
        // Even an invalid later selection is ignored rather than rejected: nothing about the snapshot can change.
        assertThat(service().generateOrGet(id, Optional.of(List.of(99))).snapshot()).isEqualTo(first.snapshot());
        assertThat(provider.requests).hasSize(1);
    }

    // ---- selection ----

    @Test
    void aSuppliedSelectionCannotNarrowACourseOfEightOrFewerModules() {
        UUID id = paidAssessment(6, UniversityEntitlementStatus.ACTIVE);

        var outcome = service().generateOrGet(id, Optional.of(List.of(4, 2)));

        assertThat(outcome.snapshot().selectedModuleIds()).containsExactly(1, 2, 3, 4, 5, 6);
        assertThat(provider.requests).singleElement().satisfies(request -> assertThat(request.modules())
                .containsExactly("Module 1", "Module 2", "Module 3", "Module 4", "Module 5", "Module 6"));

        UUID invalid = paidAssessment(8, UniversityEntitlementStatus.ACTIVE);
        assertThat(service().generateOrGet(invalid, Optional.of(List.of(99, 99))).snapshot().selectedModuleIds())
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
        assertThat(store.failures).isEmpty();
    }

    @Test
    void selectionIsResolvedBeforeAnyProviderCall() {
        UUID id = paidAssessment(10, UniversityEntitlementStatus.ACTIVE);
        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOfSatisfying(
                UniversityModuleAnalysisUnavailableException.class,
                failure -> assertThat(failure.reason()).isEqualTo(Reason.MODULE_SELECTION_REQUIRED));
        assertThatThrownBy(() -> service().generateOrGet(id, Optional.of(List.of(4, 4)))).isInstanceOfSatisfying(
                UniversityModuleAnalysisUnavailableException.class,
                failure -> assertThat(failure.reason()).isEqualTo(Reason.INVALID_SELECTION));
        assertThat(provider.requests).isEmpty();
        assertThat(store.snapshots).isEmpty();
        // Refusals are customer-input or access decisions, not generation attempts: no failure is recorded.
        assertThat(store.failures).isEmpty();
    }

    // ---- entitlement ----

    @Test
    void noActiveEntitlementMeansNoGenerationAndNoProviderCall() {
        for (UniversityEntitlementStatus status : List.of(UniversityEntitlementStatus.PENDING,
                UniversityEntitlementStatus.FAILED, UniversityEntitlementStatus.REFUNDED,
                UniversityEntitlementStatus.REVOKED)) {
            UUID id = paidAssessment(3, status);
            assertThatThrownBy(() -> service().generateOrGet(id)).as(status.name()).isInstanceOfSatisfying(
                    UniversityModuleAnalysisUnavailableException.class,
                    failure -> assertThat(failure.reason()).isEqualTo(Reason.NOT_ENTITLED));
        }
        UUID unpaid = UUID.randomUUID();
        assessments.save(ModuleAnalysisTestData.assessment(unpaid, 3));
        assertThatThrownBy(() -> service().generateOrGet(unpaid)).hasMessageContaining("ACTIVE entitlement");
        assertThat(provider.requests).isEmpty();
    }

    @Test
    void aStoredSnapshotIsRetainedButNotReturnedOnceTheEntitlementIsNoLongerActive() {
        UUID id = paidAssessment(3, UniversityEntitlementStatus.ACTIVE);
        var stored = service().generateOrGet(id).snapshot();
        purchases.entitlements.put(id, ModuleAnalysisTestData.entitlement(id, UniversityEntitlementStatus.REFUNDED));

        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOfSatisfying(
                UniversityModuleAnalysisUnavailableException.class,
                failure -> assertThat(failure.reason()).isEqualTo(Reason.NOT_ENTITLED));
        assertThat(store.snapshots).containsEntry(id, stored);

        purchases.entitlements.put(id, ModuleAnalysisTestData.entitlement(id, UniversityEntitlementStatus.ACTIVE));
        assertThat(service().generateOrGet(id).snapshot()).isEqualTo(stored);
        assertThat(provider.requests).hasSize(1);
    }

    @Test
    void anUnknownAssessmentOrMissingProviderIsRefusedClearly() {
        assertThatThrownBy(() -> service().generateOrGet(UUID.randomUUID())).isInstanceOfSatisfying(
                UniversityModuleAnalysisUnavailableException.class,
                failure -> assertThat(failure.reason()).isEqualTo(Reason.ASSESSMENT_NOT_FOUND));
        UUID id = paidAssessment(3, UniversityEntitlementStatus.ACTIVE);
        var noProvider = new UniversityModuleAnalysisService(assessments, purchaseUseCase, Optional::empty, classifier, store,
                CLOCK, AssessmentTrace.disabled());
        assertThatThrownBy(() -> noProvider.generateOrGet(id)).isInstanceOfSatisfying(
                UniversityModuleAnalysisUnavailableException.class,
                failure -> assertThat(failure.reason()).isEqualTo(Reason.PROVIDER_NOT_CONFIGURED));
    }

    // ---- failure and retry ----

    @Test
    void aProviderCallFailureIsRecordedAsTransientAndALaterRequestCanSucceed() {
        UUID id = paidAssessment(3, UniversityEntitlementStatus.ACTIVE);
        provider.failNext(new UniversityModuleJudgmentException(UniversityModuleJudgmentException.Kind.PROVIDER_CALL,
                "the module-analysis provider call failed: ResourceAccessException", null));

        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOfSatisfying(
                UniversityModuleAnalysisFailedException.class, failure -> assertThat(failure.category())
                        .isEqualTo(UniversityModuleAnalysisFailure.Category.PROVIDER_TRANSIENT));
        assertThat(store.snapshots).isEmpty();
        assertThat(classifier.calls).hasValue(0);
        assertThat(store.failures).singleElement().satisfies(failure -> {
            assertThat(failure.category()).isEqualTo(UniversityModuleAnalysisFailure.Category.PROVIDER_TRANSIENT);
            assertThat(failure.retryable()).isTrue();
            assertThat(failure.errorMessage()).contains("provider call failed");
            assertThat(failure.schemaVersion()).isEqualTo("university-module-v1");
            assertThat(failure.policyVersion()).isEqualTo("university-module-policy-v1");
        });

        var retried = service().generateOrGet(id);
        assertThat(retried.reused()).isFalse();
        assertThat(store.snapshots).containsKey(id);
        assertThat(provider.requests).hasSize(2);
    }

    @Test
    void invalidProviderOutputIsRecordedAsAnInvalidResponseAndStoresNothing() {
        UUID id = paidAssessment(3, UniversityEntitlementStatus.ACTIVE);
        provider.failNext(new UniversityModuleJudgmentException(
                "invalid module judgment: distribution probabilities must sum to 1, were 0.9"));
        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOfSatisfying(
                UniversityModuleAnalysisFailedException.class, failure -> assertThat(failure.category())
                        .isEqualTo(UniversityModuleAnalysisFailure.Category.PROVIDER_INVALID_RESPONSE));

        // A provider result for different modules than requested is refused the same way.
        provider.answerFor = request -> ModuleAnalysisTestData.fixtureJudgments("nursing",
                new UniversityModuleAnalysisRequest(request.assessmentId(), request.courseTitle(),
                        request.courseDescription(), List.of("Something else"), request.skillsDeveloped()));
        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOf(UniversityModuleAnalysisFailedException.class)
                .hasRootCauseMessage("the provider result does not correspond to the request");
        assertThat(store.snapshots).isEmpty();
        assertThat(store.failures).extracting(UniversityModuleAnalysisFailure::category).containsExactly(
                UniversityModuleAnalysisFailure.Category.PROVIDER_INVALID_RESPONSE,
                UniversityModuleAnalysisFailure.Category.PROVIDER_INVALID_RESPONSE);
    }

    @Test
    void aPersistenceFailureWithNoCanonicalSnapshotIsRecordedAndRetryable() {
        UUID id = paidAssessment(3, UniversityEntitlementStatus.ACTIVE);
        store.failNextSave = true;
        assertThatThrownBy(() -> service().generateOrGet(id)).isInstanceOfSatisfying(
                UniversityModuleAnalysisFailedException.class, failure -> assertThat(failure.category())
                        .isEqualTo(UniversityModuleAnalysisFailure.Category.PERSISTENCE));
        assertThat(store.failures).singleElement().satisfies(failure -> assertThat(failure.retryable()).isTrue());
        assertThat(service().generateOrGet(id).reused()).isFalse();
    }

    // ---- concurrency ----

    @Test
    void whenAnotherRequestCompletesFirstItsSnapshotIsCanonicalForBothCallers() {
        UUID id = paidAssessment(3, UniversityEntitlementStatus.ACTIVE);
        // The other request finishes its own generation while this one is calling Jev.
        provider.duringCall = () -> {
            var winner = new FixtureProvider("nursing");
            var other = new UniversityModuleAnalysisService(assessments, purchaseUseCase, () -> Optional.of(winner), classifier,
                    store, CLOCK, AssessmentTrace.disabled());
            provider.duringCall = null;
            other.generateOrGet(id);
        };

        var loser = service().generateOrGet(id);

        var canonical = store.snapshots.get(id);
        assertThat(loser.snapshot()).isEqualTo(canonical);
        assertThat(loser.reused()).isTrue();
        assertThat(store.saves).isEqualTo(2);
        assertThat(service().generateOrGet(id).snapshot()).isEqualTo(canonical);
        // Both requests paid for a provider call; only one snapshot became canonical.
        assertThat(classifier.calls).hasValue(2);
    }

    // ---- helpers ----

    private UniversityModuleAnalysisService service() {
        return service(AssessmentTrace.disabled());
    }

    private UniversityModuleAnalysisService service(AssessmentTrace trace) {
        return new UniversityModuleAnalysisService(assessments, purchaseUseCase, () -> Optional.of(provider), classifier, store,
                CLOCK, trace);
    }

    private UUID paidAssessment(int modules, UniversityEntitlementStatus status) {
        UUID id = UUID.randomUUID();
        assessments.save(ModuleAnalysisTestData.assessment(id, modules));
        purchases.entitlements.put(id, ModuleAnalysisTestData.entitlement(id, status));
        return id;
    }

    /** Answers from the frozen live fixtures, re-keyed onto each request; can fail once or run code mid-call. */
    static final class FixtureProvider implements UniversityModuleJudgmentProvider {
        final List<UniversityModuleAnalysisRequest> requests = new ArrayList<>();
        java.util.function.Function<UniversityModuleAnalysisRequest, UniversityModuleJudgmentResult> answerFor;
        Runnable duringCall;
        private RuntimeException failure;

        FixtureProvider(String course) {
            answerFor = request -> ModuleAnalysisTestData.fixtureJudgments(course, request);
        }

        void failNext(RuntimeException next) {
            failure = next;
        }

        @Override
        public UniversityModuleJudgmentResult judge(UniversityModuleAnalysisRequest request) {
            requests.add(request);
            if (failure != null) {
                RuntimeException next = failure;
                failure = null;
                throw next;
            }
            if (duringCall != null) {
                duringCall.run();
            }
            return answerFor.apply(request);
        }

        @Override
        public UniversityModuleJudgmentSource source() {
            return UniversityModuleJudgmentSource.JEV;
        }
    }

    private static final class CountingClassifier implements UniversityModuleClassifier {
        final AtomicInteger calls = new AtomicInteger();
        private final UniversityModuleClassifier delegate = UniversityModuleClassifier.v1(new UniversityModulePolicyV1());

        @Override
        public List<UniversityModuleClassification> classify(UniversityModuleJudgmentResult judgments) {
            calls.incrementAndGet();
            return delegate.classify(judgments);
        }

        @Override
        public String policyVersion() {
            return delegate.policyVersion();
        }

        @Override
        public String supportedSchemaVersion() {
            return delegate.supportedSchemaVersion();
        }
    }

    private static final class Assessments implements UniversityAssessmentStore {
        final Map<UUID, StoredUniversityAssessment> stored = new HashMap<>();

        @Override
        public void save(StoredUniversityAssessment assessment) {
            stored.put(assessment.id(), assessment);
        }

        @Override
        public Optional<StoredUniversityAssessment> findById(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }
    }

    private static final class Purchases {
        final Map<UUID, UniversityReportEntitlement> entitlements = new HashMap<>();

        UniversityReportPurchaseUseCase useCase() {
            UniversityReportPurchaseUseCase useCase = mock(UniversityReportPurchaseUseCase.class);
            when(useCase.findEntitlement(any())).thenAnswer(call -> Optional.ofNullable(
                    entitlements.get(call.<UUID>getArgument(0))));
            return useCase;
        }
    }

    /** Insert-only, first write wins, as the unique assessment constraint makes the real store. */
    private static final class Store implements UniversityModuleAnalysisStore {
        final Map<UUID, UniversityModuleAnalysisSnapshot> snapshots = new HashMap<>();
        final List<UniversityModuleAnalysisFailure> failures = new ArrayList<>();
        int saves;
        boolean failNextSave;

        @Override
        public Optional<UniversityModuleAnalysisSnapshot> findCompletedByAssessmentId(UUID assessmentId) {
            return Optional.ofNullable(snapshots.get(assessmentId));
        }

        @Override
        public UniversityModuleAnalysisSnapshot saveCompletedIfAbsent(UniversityModuleAnalysisSnapshot snapshot) {
            saves++;
            if (failNextSave) {
                failNextSave = false;
                throw new IllegalStateException("database unavailable");
            }
            return snapshots.computeIfAbsent(snapshot.assessmentId(), id -> snapshot);
        }

        @Override
        public void recordFailure(UniversityModuleAnalysisFailure failure) {
            failures.add(failure);
        }

        @Override
        public List<UniversityModuleAnalysisFailure> findFailuresByAssessmentId(UUID assessmentId) {
            return failures.stream().filter(failure -> failure.assessmentId().equals(assessmentId)).toList();
        }
    }
}
