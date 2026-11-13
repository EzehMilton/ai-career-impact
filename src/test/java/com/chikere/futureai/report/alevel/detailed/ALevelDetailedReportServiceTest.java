package com.chikere.futureai.report.alevel.detailed;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentStore;
import com.chikere.futureai.assessment.alevel.application.ALevelTestAssessments;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceBundle;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceCategory;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceItem;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceStore;
import com.chikere.futureai.evidence.alevel.application.EvidenceSource;
import com.chikere.futureai.evidence.alevel.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.alevel.application.EvidenceStatus;
import com.chikere.futureai.guidance.alevel.ALevelGuidancePolicyV1;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrative;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeService;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeStatus;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeValidator;
import com.chikere.futureai.narrative.alevel.paid.ScriptedALevelPaidNarrativeGenerator;
import com.chikere.futureai.payment.alevel.application.ALevelEntitlementStatus;
import com.chikere.futureai.payment.alevel.application.ALevelPaymentProvider;
import com.chikere.futureai.payment.alevel.application.ALevelPurchaseStore;
import com.chikere.futureai.payment.alevel.application.ALevelReportEntitlement;
import com.chikere.futureai.payment.alevel.application.PurchasedALevelReportSnapshot;
import com.chikere.futureai.report.alevel.application.ALevelReportAssembler;
import com.chikere.futureai.report.alevel.application.ALevelReportSource;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import com.chikere.futureai.trace.AssessmentTrace;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The v3 lifecycle and historical-v2 reuse on in-memory ports: no Jev, one narrative attempt, one snapshot. */
class ALevelDetailedReportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"), ZoneOffset.UTC);

    private final Map<UUID, StoredALevelAssessment> assessments = new HashMap<>();
    private final Map<UUID, ALevelReportEntitlement> entitlements = new HashMap<>();
    private final Map<UUID, PurchasedALevelReportSnapshot> purchases = new HashMap<>();
    private final Map<UUID, ALevelEvidenceBundle> evidence = new HashMap<>();
    private final Map<UUID, ALevelDetailedReportSnapshot> reports = new HashMap<>();
    private ScriptedALevelPaidNarrativeGenerator narrative = new ScriptedALevelPaidNarrativeGenerator();
    private boolean narrativeConfigured = true;
    private int saves;
    /** Set to simulate a concurrent preparation that committed first. */
    private ALevelDetailedReportSnapshot rival;

    private final ALevelDetailedReportStore store = new ALevelDetailedReportStore() {
        @Override
        public Optional<ALevelDetailedReportSnapshot> findByAssessmentId(UUID assessmentId) {
            return Optional.ofNullable(reports.get(assessmentId));
        }

        @Override
        public ALevelDetailedReportSnapshot saveIfAbsent(ALevelDetailedReportSnapshot snapshot) {
            saves++;
            if (rival != null) {
                reports.putIfAbsent(snapshot.assessmentId(), rival);
            }
            return reports.computeIfAbsent(snapshot.assessmentId(), id -> snapshot);
        }
    };

    private ALevelDetailedReportService service() {
        ALevelAssessmentStore assessmentStore = mock(ALevelAssessmentStore.class);
        when(assessmentStore.findById(any())).thenAnswer(call -> Optional.ofNullable(
                assessments.get(call.<UUID>getArgument(0))));
        ALevelPurchaseStore purchaseStore = mock(ALevelPurchaseStore.class);
        when(purchaseStore.findEntitlementByAssessmentId(any())).thenAnswer(call -> Optional.ofNullable(
                entitlements.get(call.<UUID>getArgument(0))));
        when(purchaseStore.findSnapshotByAssessmentId(any())).thenAnswer(call -> Optional.ofNullable(
                purchases.get(call.<UUID>getArgument(0))));
        ALevelEvidenceStore evidenceStore = mock(ALevelEvidenceStore.class);
        when(evidenceStore.findByAssessmentId(any())).thenAnswer(call -> Optional.ofNullable(
                evidence.get(call.<UUID>getArgument(0))));
        var narratives = new ALevelPaidNarrativeService(
                () -> narrativeConfigured ? Optional.of(narrative) : Optional.empty(),
                new ALevelPaidNarrativeValidator(), AssessmentTrace.disabled());
        return new ALevelDetailedReportService(purchaseStore, assessmentStore, evidenceStore,
                new ALevelGuidancePolicyV1(), narratives, ALevelDetailedReportAssembler.v3(), store, CLOCK,
                AssessmentTrace.disabled());
    }

    private UUID paidSubjectProfile(String fixture) {
        UUID id = UUID.randomUUID();
        assessments.put(id, ALevelTestAssessments.subjectProfile(id, fixture, AssessmentSource.JEV));
        var entitlement = entitlement(id, ALevelEntitlementStatus.ACTIVE);
        entitlements.put(id, entitlement);
        purchases.put(id, PurchasedALevelReportSnapshot.subjectProfile(entitlement.entitlementId(), id,
                ALevelTestAssessments.ASSESSED_AT));
        return id;
    }

    // ---- no Jev, ever ----

    @Test
    void theDetailedReportServiceHasNoJudgmentProviderToCall() {
        for (Constructor<?> constructor : ALevelDetailedReportService.class.getConstructors()) {
            assertThat(Arrays.stream(constructor.getParameterTypes()).map(Class::getSimpleName))
                    .noneMatch(name -> name.contains("JudgmentProvider") || name.contains("TypeSafe")
                            || name.contains("Jev"));
        }
    }

    // ---- generation and reuse ----

    @Test
    void theFirstPreparationMakesOneNarrativeCallAndFreezesEveryVersion() {
        UUID id = paidSubjectProfile("maths_economics_geography");
        narrative.then(ScriptedALevelPaidNarrativeGenerator.cleanNarrative());

        var outcome = service().prepare(id);

        assertThat(outcome.provisional()).isFalse();
        assertThat(outcome.reused()).isFalse();
        assertThat(narrative.calls()).isEqualTo(1);
        var stored = outcome.stored().orElseThrow();
        assertThat(stored.reportVersion()).isEqualTo("a-level-report-v3");
        assertThat(stored.pdfVersion()).isEqualTo("a-level-report-pdf-v3");
        assertThat(stored.judgmentSchemaVersion()).isEqualTo("a-level-v2");
        assertThat(stored.decisionPolicyVersion()).isEqualTo("a-level-decision-v2");
        assertThat(stored.guidancePolicyVersion()).isEqualTo("a-level-guidance-policy-v1");
        assertThat(stored.paidNarrativeVersion()).isEqualTo("a-level-paid-narrative-v1");
        assertThat(stored.paidNarrativeStatus()).isEqualTo(ALevelPaidNarrativeStatus.AVAILABLE);
        assertThat(stored.evidenceVersion()).isEmpty();
        assertThat(stored.generatedAt()).isEqualTo(CLOCK.instant());
        assertThat(stored.report().perspectives().modelWritten()).isTrue();
        // The profile in the report is the stored decision, not a recalculation.
        assertThat(stored.report().profile()).isEqualTo(new com.chikere.futureai.report.alevel.application
                .ALevelSubjectProfileComposerV3().compose(assessments.get(id).result().profile()));
        assertThat(stored.report().profile().optionsKeptOpen().id()).isEqualTo("OPTIONS_KEPT_OPEN");
        assertThat(stored.report().profile().optionsKeptOpen().name()).isEqualTo("Future study options");
    }

    @Test
    void repeatedPreparationAndViewingReuseTheSnapshotWithNoProviderCall() {
        UUID id = paidSubjectProfile("maths_economics_geography");
        narrative.then(ScriptedALevelPaidNarrativeGenerator.cleanNarrative());
        var first = service().prepare(id);

        var again = service().prepare(id);

        assertThat(again.reused()).isTrue();
        assertThat(again.stored()).isEqualTo(first.stored());
        assertThat(service().find(id)).isEqualTo(first.stored());
        assertThat(service().preparation(id)).contains(new ALevelDetailedReportUseCase.Preparation(true));
        assertThat(narrative.calls()).isEqualTo(1);
        assertThat(saves).isEqualTo(1);
    }

    @Test
    void anExistingV2SnapshotIsReturnedWithoutV3Recomposition() {
        UUID id = paidSubjectProfile("maths_economics_geography");
        var report = new ALevelDetailedReportAssembler().assemble(assessments.get(id),
                new ALevelGuidancePolicyV1().decide(assessments.get(id).result().profile(), assessments.get(id).state()),
                new com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeOutcome(
                        ALevelPaidNarrativeStatus.FALLBACK, Optional.empty(), "a-level-paid-narrative-v1", "openai",
                        Optional.empty(), Optional.empty(), 2, Optional.of("historical")), Optional.empty());
        var historical = new ALevelDetailedReportSnapshot(UUID.randomUUID(), id, entitlements.get(id).entitlementId(),
                "a-level-v2", "a-level-decision-v2", "a-level-guidance-policy-v1", "a-level-paid-narrative-v1",
                ALevelPaidNarrativeStatus.FALLBACK, Optional.empty(), "a-level-report-v2", "a-level-report-pdf-v2",
                Instant.parse("2026-10-28T09:59:00Z"), report);
        reports.put(id, historical);

        var outcome = service().prepare(id);

        assertThat(outcome.reused()).isTrue();
        assertThat(outcome.stored()).contains(historical);
        assertThat(outcome.report().profile().optionsKeptOpen().name()).isEqualTo("Options Kept Open");
        assertThat(narrative.calls()).isZero();
        assertThat(saves).isZero();
    }

    @Test
    void aStoredReportNeverChangesWhenTheProviderOrEvidenceLaterChanges() {
        UUID id = paidSubjectProfile("english_history_politics");
        narrative.then(ScriptedALevelPaidNarrativeGenerator.cleanNarrative());
        var first = service().prepare(id).stored().orElseThrow();

        narrative = new ScriptedALevelPaidNarrativeGenerator()
                .then(ScriptedALevelPaidNarrativeGenerator.differentNarrative());
        evidence.put(id, evidenceBundle(id));
        var again = service().prepare(id);

        assertThat(again.stored()).contains(first);
        assertThat(again.report().evidence()).isEmpty();
        assertThat(narrative.calls()).isZero();
    }

    @Test
    void aFinalNarrativeFallbackIsStoredWithDeterministicCopy() {
        UUID id = paidSubjectProfile("maths_economics_geography");
        var forbidden = new ALevelPaidNarrative("This guarantees you a place.", "a", "b", "c", "d");
        narrative.then(forbidden).then(forbidden);

        var stored = service().prepare(id).stored().orElseThrow();

        assertThat(stored.paidNarrativeStatus()).isEqualTo(ALevelPaidNarrativeStatus.FALLBACK);
        assertThat(stored.report().perspectives().modelWritten()).isFalse();
        assertThat(stored.report().perspectives().profile()).doesNotContain("guarantees").isNotBlank();
        assertThat(narrative.calls()).isEqualTo(2);
    }

    // ---- retryable narrative ----

    @Test
    void aRetryableNarrativeIsProvisionalStoresNothingAndARetryRetriesOnlyTheNarrative() {
        UUID id = paidSubjectProfile("mathematics_physics_computer_science");
        narrativeConfigured = false;

        var provisional = service().prepare(id);

        assertThat(provisional.provisional()).isTrue();
        assertThat(reports).isEmpty();
        assertThat(service().find(id)).isEmpty();
        assertThat(service().provisional(id)).hasValueSatisfying(report ->
                assertThat(report.actionPlan()).isEqualTo(provisional.report().actionPlan()));

        narrativeConfigured = true;
        narrative.then(ScriptedALevelPaidNarrativeGenerator.cleanNarrative());
        var retried = service().prepare(id);

        assertThat(retried.provisional()).isFalse();
        assertThat(narrative.calls()).isEqualTo(1);
        var report = retried.report();
        assertThat(report.profile()).isEqualTo(provisional.report().profile());
        assertThat(report.strengths()).isEqualTo(provisional.report().strengths());
        assertThat(report.skillPriorities()).isEqualTo(provisional.report().skillPriorities());
        assertThat(report.actionPlan()).isEqualTo(provisional.report().actionPlan());
        assertThat(report.safeguards()).isEqualTo(provisional.report().safeguards());
        assertThat(service().provisional(id)).isEmpty();
    }

    @Test
    void aConcurrentWinnerIsReturnedUnchanged() {
        UUID id = paidSubjectProfile("maths_economics_geography");
        narrative.then(ScriptedALevelPaidNarrativeGenerator.cleanNarrative());
        // Another preparation committed first with a fallback narrative.
        var winnerReport = new ALevelDetailedReportAssembler().assemble(assessments.get(id),
                new ALevelGuidancePolicyV1().decide(assessments.get(id).result().profile(),
                        assessments.get(id).state()),
                new com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeOutcome(
                        ALevelPaidNarrativeStatus.FALLBACK, Optional.empty(), "a-level-paid-narrative-v1", "openai",
                        Optional.empty(), Optional.empty(), 2, Optional.of("rival")), Optional.empty());
        rival = new ALevelDetailedReportSnapshot(UUID.randomUUID(), id, entitlements.get(id).entitlementId(),
                "a-level-v2", "a-level-decision-v2", "a-level-guidance-policy-v1", "a-level-paid-narrative-v1",
                ALevelPaidNarrativeStatus.FALLBACK, Optional.empty(), "a-level-report-v2", "a-level-report-pdf-v2",
                Instant.parse("2026-10-28T09:59:00Z"), winnerReport);

        var outcome = service().prepare(id);

        assertThat(outcome.reused()).isTrue();
        assertThat(outcome.stored()).contains(rival);
        assertThat(reports.get(id)).isSameAs(rival);
    }

    // ---- entitlement and version routing ----

    @Test
    void withoutAnActiveV2EntitlementNothingIsCalledOrShown() {
        UUID id = paidSubjectProfile("maths_economics_geography");
        entitlements.put(id, entitlement(id, ALevelEntitlementStatus.PENDING));

        assertThatThrownBy(() -> service().prepare(id)).isInstanceOfSatisfying(
                ALevelDetailedReportUnavailableException.class, denied ->
                        assertThat(denied.reason()).isEqualTo(ALevelDetailedReportUnavailableException.Reason.NOT_ENTITLED));
        assertThat(service().find(id)).isEmpty();
        assertThat(service().provisional(id)).isEmpty();
        assertThat(service().preparation(id)).isEmpty();
        assertThat(narrative.calls()).isZero();
    }

    @Test
    void aHistoricalV1PurchaseIsNeverRoutedToTheV2Report() {
        UUID id = UUID.randomUUID();
        var v1 = ALevelTestAssessments.historicalV1(id, "maths_economics_geography");
        assessments.put(id, v1);
        var entitlement = entitlement(id, ALevelEntitlementStatus.ACTIVE);
        entitlements.put(id, entitlement);
        var report = new ALevelReportAssembler().assemble(new ALevelReportSource(v1, Optional.empty(),
                Optional.empty()), ALevelReportTier.PAID);
        purchases.put(id, new PurchasedALevelReportSnapshot(entitlement.entitlementId(), id, report.reportVersion(),
                ALevelTestAssessments.ASSESSED_AT, report));

        assertThatThrownBy(() -> service().prepare(id)).isInstanceOf(ALevelDetailedReportUnavailableException.class);
        assertThat(service().preparation(id)).isEmpty();
        assertThat(narrative.calls()).isZero();
    }

    // ---- evidence ----

    @Test
    void storedEvidenceIsShownSeparatelyAndAbsentEvidenceHidesTheSection() {
        UUID withEvidence = paidSubjectProfile("maths_economics_geography");
        evidence.put(withEvidence, evidenceBundle(withEvidence));
        narrative.then(ScriptedALevelPaidNarrativeGenerator.cleanNarrative())
                .then(ScriptedALevelPaidNarrativeGenerator.cleanNarrative());

        var shown = service().prepare(withEvidence).stored().orElseThrow();
        var hidden = service().prepare(paidSubjectProfile("maths_economics_geography")).stored().orElseThrow();

        assertThat(shown.report().evidence()).singleElement().satisfies(item -> {
            assertThat(item.sourceName()).isEqualTo("Official course page");
            assertThat(item.sourceUrl()).isEqualTo("https://example.ac.uk/economics");
        });
        assertThat(shown.evidenceVersion()).contains("a-level-evidence-v1");
        // Evidence never changes the profile.
        assertThat(shown.report().profile()).isEqualTo(hidden.report().profile());
        assertThat(hidden.report().evidence()).isEmpty();
        assertThat(hidden.evidenceVersion()).isEmpty();
    }

    // ---- helpers ----

    private static ALevelReportEntitlement entitlement(UUID assessmentId, ALevelEntitlementStatus status) {
        Instant at = ALevelTestAssessments.ASSESSED_AT;
        return new ALevelReportEntitlement(UUID.nameUUIDFromBytes(assessmentId.toString().getBytes()), assessmentId,
                ALevelReportTier.PAID, status, ALevelPaymentProvider.COMPLIMENTARY, "complimentary_test",
                URI.create("http://localhost/a-level-assessment/" + assessmentId), "complimentary", Optional.empty(),
                Optional.of("a-level-report-v2"), at, status == ALevelEntitlementStatus.ACTIVE
                        ? Optional.of(at) : Optional.empty());
    }

    private static ALevelEvidenceBundle evidenceBundle(UUID assessmentId) {
        Instant at = Instant.parse("2026-10-28T09:30:00Z");
        var item = new ALevelEvidenceItem(ALevelEvidenceCategory.DEGREE_PREREQUISITES, "Economics course page",
                "A factual note from a published course page.", "Official course page", Optional.empty(),
                Optional.of(URI.create("https://example.ac.uk/economics")), Optional.of("Example University"),
                Optional.of("United Kingdom"), Optional.empty(), at, Optional.empty(), Optional.empty(),
                Optional.empty(), EvidenceSourceQuality.OFFICIAL, "Separate from scoring.");
        return new ALevelEvidenceBundle(assessmentId, "a-level-evidence-v1", EvidenceSource.LIVE, "official-provider",
                EvidenceStatus.AVAILABLE, at, List.of(item));
    }
}
