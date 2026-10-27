package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.narrative.professional.application.NarrativeRepairFeedback;
import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeContext;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeGenerator;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeService;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeStore;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeValidator;
import com.chikere.futureai.narrative.professional.paid.StoredProfessionalPaidNarrative;
import com.chikere.futureai.payment.professional.application.*;
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
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalGuidancePolicyV1;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidancePolicy;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceService;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceSnapshot;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceStore;
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

/**
 * The paid-report lifecycle with the real 4D analysis, 4E guidance and paid-narrative services. The responsibility
 * provider is a Mockito mock and the paid-narrative generator is scripted: no Jev and no OpenAI call is ever made.
 */
class ProfessionalDetailedReportServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);

    private final ProfessionalAssessmentStore assessments = mock(ProfessionalAssessmentStore.class);
    private final ProfessionalReportPurchaseUseCase purchaseUseCase = mock(ProfessionalReportPurchaseUseCase.class);
    private final ProfessionalResponsibilityJudgmentProvider jev = mock(ProfessionalResponsibilityJudgmentProvider.class);
    private final Purchases purchases = new Purchases();
    private final AnalysisStore analysisStore = new AnalysisStore();
    private final GuidanceStore guidanceStore = new GuidanceStore();
    private final NarrativeStore narrativeStore = new NarrativeStore();
    private final ReportStore reportStore = new ReportStore();
    private final CountingGuidancePolicy guidancePolicy = new CountingGuidancePolicy();
    private final Scripted openAi = new Scripted();

    @BeforeEach
    void wiring() {
        // Hand-built values per position (a good opportunity, a human-led item, a hands-on item), not live data.
        when(jev.judge(any())).thenAnswer(call -> {
            ProfessionalResponsibilityAnalysisRequest request = call.getArgument(0);
            var base = HandBuiltResponsibilityJudgments.forRequest(request);
            var sets = new ArrayList<com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet>();
            for (var set : base.responsibilities()) {
                double[] v = switch (set.responsibilityIndex() % 3) {
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
        when(purchaseUseCase.findEntitlement(any())).thenAnswer(call -> purchases.findEntitlementByAssessmentId(call.getArgument(0)));
    }

    // ---- Entitlement ----

    @Test
    void anActivePaidOrComplimentaryEntitlementGeneratesTheReportOnce() {
        for (ProfessionalPaymentProvider source : List.of(ProfessionalPaymentProvider.STRIPE,
                ProfessionalPaymentProvider.COMPLIMENTARY)) {
            UUID id = assessment(List.of("Write weekly reports", "Meet clients"));
            entitlement(id, ProfessionalEntitlementStatus.ACTIVE, source);
            var outcome = service(AssessmentTrace.disabled()).prepare(id, Optional.empty());
            assertThat(outcome.reused()).as(source.name()).isFalse();
            assertThat(outcome.snapshot().orElseThrow().reportVersion()).isEqualTo("professional-report-v3");
            assertThat(outcome.snapshot().orElseThrow().pdfVersion()).isEqualTo("professional-report-pdf-v5");
            assertThat(outcome.snapshot().orElseThrow().paidNarrativeStatus()).isEqualTo(PaidNarrativeStatus.AVAILABLE);
        }
        verify(jev, times(2)).judge(any());
        assertThat(openAi.calls).isEqualTo(2);
    }

    @Test
    void refundedOrRevokedAccessIsDeniedTheStoredReportIsKeptAndRestoredAccessReturnsTheSameReport() {
        UUID id = paid(List.of("Write weekly reports"));
        var stored = service(AssessmentTrace.disabled()).prepare(id, Optional.empty()).snapshot().orElseThrow();
        for (ProfessionalEntitlementStatus status : List.of(ProfessionalEntitlementStatus.REFUNDED,
                ProfessionalEntitlementStatus.REVOKED, ProfessionalEntitlementStatus.PENDING)) {
            entitlement(id, status, ProfessionalPaymentProvider.STRIPE);
            assertThat(service(AssessmentTrace.disabled()).find(id)).as(status.name()).isEmpty();
            assertThat(service(AssessmentTrace.disabled()).preparation(id)).isEmpty();
            assertUnavailable(id, Optional.empty(), Reason.NOT_ENTITLED);
        }
        assertThat(reportStore.reports.get(id)).isEqualTo(stored);
        entitlement(id, ProfessionalEntitlementStatus.ACTIVE, ProfessionalPaymentProvider.STRIPE);
        assertThat(service(AssessmentTrace.disabled()).find(id)).contains(stored);
        verify(jev, times(1)).judge(any());
        assertThat(openAi.calls).isEqualTo(1);
    }

    @Test
    void withoutAnyEntitlementNothingIsGenerated() {
        UUID id = assessment(List.of("Write weekly reports"));
        assertUnavailable(id, Optional.empty(), Reason.NOT_ENTITLED);
        verifyNoInteractions(jev);
        assertThat(openAi.calls).isZero();
        assertThat(reportStore.reports).isEmpty();
    }

    // ---- Idempotency and immutability ----

    @Test
    void aRepeatedRequestReturnsTheStoredReportWithZeroJevZeroOpenAiAndNoGuidanceRecomputation() {
        UUID id = paid(List.of("Write weekly reports", "Meet clients", "Install equipment on site"));
        try (TraceLogCapture log = TraceLogCapture.start()) {
            var service = service(AssessmentTrace.enabled());
            var first = service.prepare(id, Optional.empty());
            var second = service.prepare(id, Optional.empty());
            var third = service.prepare(id, Optional.of(List.of(1)));
            assertThat(second.reused()).isTrue();
            assertThat(second.snapshot()).isEqualTo(first.snapshot());
            assertThat(third.snapshot()).isEqualTo(first.snapshot());
            verify(jev, times(1)).judge(any());
            assertThat(openAi.calls).isEqualTo(1);
            assertThat(guidancePolicy.calls).isEqualTo(1);
            assertThat(log.events(TraceStage.PROFESSIONAL_REPORT_V3_GENERATED)).hasSize(1);
            assertThat(log.events(TraceStage.PROFESSIONAL_REPORT_V3_REUSED)).hasSize(2);
            var generated = log.only(TraceStage.PROFESSIONAL_REPORT_V3_GENERATED);
            assertThat(generated.get("assessmentId").asText()).isEqualTo(id.toString());
            assertThat(generated.at("/payload/paidNarrativeStatus").asText()).isEqualTo("AVAILABLE");
            assertThat(generated.at("/payload/provenance/guidancePolicyVersion").asText())
                    .isEqualTo("professional-guidance-policy-v1");
            // Identifiers, versions and counts only: no report body is copied into the trace.
            assertThat(generated.get("payload").toString()).doesNotContain("Write weekly reports", "Executive");
        }
    }

    @Test
    void aStoredReportIsReturnedUnchangedEvenIfItsSourceArtefactsWouldNowProduceSomethingElse() {
        UUID id = paid(List.of("Write weekly reports", "Meet clients"));
        var stored = service(AssessmentTrace.disabled()).prepare(id, Optional.empty()).snapshot().orElseThrow();
        // A later guidance policy or narrative could differ; neither is consulted for a stored report.
        guidanceStore.snapshots.clear();
        narrativeStore.stored.clear();
        guidancePolicy.failure = new AssertionError("the guidance policy must not run for a stored report");
        openAi.failure = new AssertionError("no paid narrative may be generated for a stored report");
        assertThat(service(AssessmentTrace.disabled()).prepare(id, Optional.empty()).snapshot()).contains(stored);
        assertThat(service(AssessmentTrace.disabled()).find(id)).contains(stored);
    }

    @Test
    void theRoleLevelContentIsThePurchasedV2SnapshotAndIsNeverRewritten() {
        UUID id = paid(List.of("Write weekly reports"));
        var roleBefore = purchases.snapshots.get(id);
        var report = service(AssessmentTrace.disabled()).prepare(id, Optional.empty()).report();
        assertThat(purchases.snapshots.get(id)).isSameAs(roleBefore);
        assertThat(roleBefore.reportVersion()).isEqualTo("professional-report-v2");
        assertThat(report.provenance().roleReportVersion()).isEqualTo("professional-report-v2");
        assertThat(report.measures()).extracting(ProfessionalDetailedReport.Measure::indicator)
                .containsExactlyElementsOf(roleBefore.report().measures().stream().map(m -> m.indicator()).toList());
        assertThat(report.factors().raising()).isEqualTo(roleBefore.report().exposureDrivers());
        verify(assessments, never()).save(any());
    }

    // ---- Narrative fallback ----

    @Test
    void aTransientNarrativeFailureGivesACompleteProvisionalReportFreezesNothingAndAnExplicitRetryCompletesIt() {
        UUID id = paid(List.of("Write weekly reports", "Meet clients", "Install equipment on site"));
        openAi.failure = new IllegalStateException("OpenAI timed out");
        try (TraceLogCapture log = TraceLogCapture.start()) {
            var outcome = service(AssessmentTrace.enabled()).prepare(id, Optional.empty());
            assertThat(outcome.provisional()).isTrue();
            assertThat(outcome.snapshot()).isEmpty();
            var report = outcome.report();
            assertThat(report.narrativeStatus()).isEqualTo(PaidNarrativeStatus.RETRYABLE_FALLBACK);
            assertThat(report.executiveSummary()).isNotEmpty();
            assertThat(report.measures()).hasSize(4);
            assertThat(report.responsibilities().cards()).hasSize(3);
            assertThat(report.opportunities().items()).isNotEmpty();
            assertThat(report.humanValue().items()).isNotEmpty();
            assertThat(report.skills().items()).isNotEmpty();
            assertThat(report.safeguards().items()).isNotEmpty();
            assertThat(report.plan().phases()).hasSize(3);
            assertThat(log.only(TraceStage.PROFESSIONAL_REPORT_V3_PROVISIONAL).at("/payload/stored").asBoolean()).isFalse();
        }
        // Nothing canonical was stored, and viewing the provisional report calls no provider.
        assertThat(reportStore.reports).isEmpty();
        assertThat(narrativeStore.stored).isEmpty();
        int callsSoFar = openAi.calls;
        var shown = service(AssessmentTrace.disabled()).provisional(id).orElseThrow();
        assertThat(shown.narrativeStatus()).isEqualTo(PaidNarrativeStatus.RETRYABLE_FALLBACK);
        assertThat(service(AssessmentTrace.disabled()).find(id)).isEmpty();
        assertThat(openAi.calls).isEqualTo(callsSoFar);

        // The outage ends; the customer's explicit retry produces and freezes the canonical report.
        openAi.failure = null;
        var retried = service(AssessmentTrace.disabled()).prepare(id, Optional.empty());
        assertThat(retried.provisional()).isFalse();
        assertThat(retried.snapshot().orElseThrow().paidNarrativeStatus()).isEqualTo(PaidNarrativeStatus.AVAILABLE);
        assertThat(service(AssessmentTrace.disabled()).provisional(id)).isEmpty();
        // Analysis and guidance were reused: one Jev call and one guidance decision overall.
        verify(jev, times(1)).judge(any());
        assertThat(guidancePolicy.calls).isEqualTo(1);
        // The deterministic content is the same as the provisional report's; only the narrative explanations are new.
        assertThat(retried.report().opportunities().items()).extracting(ProfessionalDetailedReport.Opportunity::key,
                        ProfessionalDetailedReport.Opportunity::howAiMayAssist, ProfessionalDetailedReport.Opportunity::conditions)
                .isEqualTo(shown.opportunities().items().stream().map(o -> org.assertj.core.groups.Tuple.tuple(o.key(),
                        o.howAiMayAssist(), o.conditions())).toList());
        assertThat(retried.report().opportunities().items()).allSatisfy(o -> assertThat(o.explanation()).isNotNull());
        assertThat(shown.opportunities().items()).allSatisfy(o -> assertThat(o.explanation()).isNull());
        assertThat(retried.report().responsibilities().cards()).isEqualTo(shown.responsibilities().cards());
        assertThat(retried.report().safeguards()).isEqualTo(shown.safeguards());
        assertThat(retried.report().plan().phases()).extracting(ProfessionalDetailedReport.Phase::actions)
                .extracting(actions -> actions.stream().map(ProfessionalDetailedReport.Action::title).toList())
                .isEqualTo(shown.plan().phases().stream().map(phase -> phase.actions().stream()
                        .map(ProfessionalDetailedReport.Action::title).toList()).toList());
    }

    @Test
    void aFinalValidationFallbackFreezesTheCompleteDeterministicReportImmutably() {
        UUID id = paid(List.of("Write weekly reports", "Meet clients", "Install equipment on site"));
        openAi.invalidContent = true;
        var outcome = service(AssessmentTrace.disabled()).prepare(id, Optional.empty());
        var stored = outcome.snapshot().orElseThrow();
        assertThat(stored.paidNarrativeStatus()).isEqualTo(PaidNarrativeStatus.FALLBACK);
        assertThat(narrativeStore.stored.get(id).fallbackReason()).hasValueSatisfying(reason ->
                assertThat(reason).startsWith("VALIDATION_FAILED_AFTER_REPAIR:"));
        assertThat(openAi.calls).isEqualTo(2);
        assertThat(stored.report().opportunities().items()).isNotEmpty();
        openAi.invalidContent = false;
        var later = service(AssessmentTrace.disabled()).prepare(id, Optional.empty());
        assertThat(later.snapshot()).contains(stored);
        assertThat(openAi.calls).isEqualTo(2);
        assertThat(service(AssessmentTrace.disabled()).provisional(id)).isEmpty();
    }

    // ---- Selection ----

    @Test
    void upToEightResponsibilitiesNeedNoSelection() {
        UUID id = paid(numbered(8));
        assertThat(service(AssessmentTrace.disabled()).preparation(id)).hasValueSatisfying(preparation -> {
            assertThat(preparation.selectionRequired()).isFalse();
            assertThat(preparation.responsibilities()).hasSize(8);
        });
        assertThat(service(AssessmentTrace.disabled()).prepare(id, Optional.empty()).report()
                .responsibilities().cards()).hasSize(8);
    }

    @Test
    void moreThanEightRequireAnExplicitValidSelectionAndNothingIsTruncated() {
        UUID id = paid(numbered(10));
        var service = service(AssessmentTrace.disabled());
        assertThat(service.preparation(id)).hasValueSatisfying(preparation -> {
            assertThat(preparation.selectionRequired()).isTrue();
            assertThat(preparation.responsibilities()).hasSize(10).first().isEqualTo("Responsibility 1");
            assertThat(preparation.maximum()).isEqualTo(8);
        });
        assertUnavailable(id, Optional.empty(), Reason.RESPONSIBILITY_SELECTION_REQUIRED);
        for (List<Integer> invalid : List.of(List.<Integer>of(), List.of(11), List.of(2, 2),
                List.of(1, 2, 3, 4, 5, 6, 7, 8, 9))) {
            assertUnavailable(id, Optional.of(invalid), Reason.INVALID_SELECTION);
        }
        verifyNoInteractions(jev);
        assertThat(reportStore.reports).isEmpty();

        var report = service.prepare(id, Optional.of(List.of(9, 2, 5))).report();
        ArgumentCaptor<ProfessionalResponsibilityAnalysisRequest> request =
                ArgumentCaptor.forClass(ProfessionalResponsibilityAnalysisRequest.class);
        verify(jev).judge(request.capture());
        assertThat(request.getValue().responsibilities()).containsExactly("Responsibility 2", "Responsibility 5",
                "Responsibility 9");
        assertThat(report.responsibilities().cards()).extracting(ProfessionalDetailedReport.ResponsibilityCard::text)
                .containsExactly("Responsibility 2", "Responsibility 5", "Responsibility 9");
        assertThat(service.preparation(id)).hasValueSatisfying(preparation -> {
            assertThat(preparation.reportReady()).isTrue();
            assertThat(preparation.selectionRequired()).isFalse();
        });
    }

    @Test
    void theSelectedSetIsFrozenOnceTheAnalysisExists() {
        UUID id = paid(numbered(10));
        service(AssessmentTrace.disabled()).prepare(id, Optional.of(List.of(1, 2)));
        // Even if the narrative and report had to be prepared again, a different selection never replaces the analysis.
        reportStore.reports.clear();
        narrativeStore.stored.clear();
        var again = service(AssessmentTrace.disabled());
        assertThat(again.preparation(id)).hasValueSatisfying(preparation -> {
            assertThat(preparation.analysisExists()).isTrue();
            assertThat(preparation.selectionRequired()).isFalse();
            assertThat(preparation.responsibilities()).containsExactly("Responsibility 1", "Responsibility 2");
        });
        var report = again.prepare(id, Optional.of(List.of(7, 8, 9))).report();
        assertThat(report.responsibilities().cards()).extracting(ProfessionalDetailedReport.ResponsibilityCard::text)
                .containsExactly("Responsibility 1", "Responsibility 2");
        verify(jev, times(1)).judge(any());
    }

    @Test
    void paymentConfirmationAndTheWebhookNeverReachTheDetailedReport() throws Exception {
        try (var files = java.nio.file.Files.walk(java.nio.file.Path.of("src/main/java/com/chikere/futureai/payment"))) {
            for (var file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(java.nio.file.Files.readString(file)).as(file.toString())
                        .doesNotContain("report.professional.detailed", "narrative.professional.paid");
            }
        }
    }

    // ---- Helpers ----

    private ProfessionalDetailedReportService service(AssessmentTrace trace) {
        var analyses = new ProfessionalResponsibilityAnalysisService(assessments, purchaseUseCase, () -> Optional.of(jev),
                new ProfessionalResponsibilityPolicyV1(), analysisStore, CLOCK, trace);
        var guidance = new ProfessionalResponsibilityGuidanceService(purchaseUseCase, analyses, guidancePolicy,
                guidanceStore, CLOCK, trace);
        var narratives = new ProfessionalPaidNarrativeService(narrativeStore, () -> Optional.of(openAi),
                new ProfessionalPaidNarrativeValidator(trace), CLOCK, trace);
        return new ProfessionalDetailedReportService(purchases, assessments, analyses, guidance, narratives,
                new ProfessionalDetailedReportAssembler(), reportStore, CLOCK, trace);
    }

    private void assertUnavailable(UUID id, Optional<List<Integer>> selection, Reason reason) {
        assertThatThrownBy(() -> service(AssessmentTrace.disabled()).prepare(id, selection)).isInstanceOfSatisfying(
                ResponsibilityAnalysisUnavailableException.class, unavailable -> assertThat(unavailable.reason()).isEqualTo(reason));
    }

    private UUID paid(List<String> responsibilities) {
        UUID id = assessment(responsibilities);
        entitlement(id, ProfessionalEntitlementStatus.ACTIVE, ProfessionalPaymentProvider.COMPLIMENTARY);
        return id;
    }

    private UUID assessment(List<String> responsibilities) {
        UUID id = UUID.randomUUID();
        var role = DetailedReportFixtures.roleSnapshot(id, "Test role", "java-software-developer-8");
        var state = new ProfessionalAssessmentState("Test role", "Work described for the test.", responsibilities,
                List.of("Communication"), OptionalInt.of(5));
        when(assessments.findById(id)).thenReturn(Optional.of(new StoredProfessionalAssessment(id,
                Instant.parse("2026-10-26T09:00:00Z"), state, new com.chikere.futureai.judgment.fixture.professional
                .LocalFixtureProfessionalJudgmentProvider("registered_nurse").judge(state),
                new com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult(
                        new com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2().decide(
                                new com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider(
                                        "registered_nurse").judge(state)),
                        com.chikere.futureai.assessment.professional.application.AssessmentSource.LOCAL_FIXTURE,
                        "professional-v1", "professional-decision-v2"), Optional.empty())));
        purchases.snapshots.put(id, role);
        return id;
    }

    private void entitlement(UUID id, ProfessionalEntitlementStatus status, ProfessionalPaymentProvider source) {
        boolean active = status == ProfessionalEntitlementStatus.ACTIVE;
        Instant at = Instant.parse("2026-10-26T09:05:00Z");
        purchases.entitlements.put(id, new ProfessionalReportEntitlement(purchases.snapshots.get(id).entitlementId(), id,
                ProfessionalReportTier.PAID, status, source, "session_" + id, URI.create("http://localhost/checkout"),
                "price_for_test", Optional.empty(), active ? Optional.of("professional-report-v2") : Optional.empty(), at,
                active ? Optional.of(at) : Optional.empty()));
    }

    private static List<String> numbered(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(index -> "Responsibility " + index).toList();
    }

    static final class Scripted implements ProfessionalPaidNarrativeGenerator {
        int calls;
        Throwable failure;

        boolean invalidContent;

        @Override
        public GeneratedPaidNarrative generate(ProfessionalPaidNarrativeContext context) {
            calls++;
            if (failure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (failure instanceof Error error) {
                throw error;
            }
            var narrative = DetailedReportFixtures.validNarrative(context);
            if (invalidContent) {
                narrative = new com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrative(
                        narrative.executiveSummary() + " AI will replace much of this work.", narrative.scoresTogether(),
                        narrative.responsibilityPattern(), narrative.opportunityExplanations(),
                        narrative.humanValueExplanations(), narrative.skillDevelopment(), narrative.skillExplanations(),
                        narrative.planIntroduction(), narrative.planActionNotes(), narrative.closingPerspective());
            }
            return new GeneratedPaidNarrative(narrative, Optional.of("req_" + calls), Optional.of("scripted"));
        }

        @Override
        public GeneratedPaidNarrative repair(ProfessionalPaidNarrativeContext context, NarrativeRepairFeedback feedback) {
            return generate(context);
        }

        @Override
        public String providerIdentifier() {
            return "scripted";
        }

        @Override
        public Optional<String> modelIdentifier() {
            return Optional.empty();
        }
    }

    static final class CountingGuidancePolicy implements ProfessionalResponsibilityGuidancePolicy {
        private final ProfessionalGuidancePolicyV1 policy = new ProfessionalGuidancePolicyV1();
        int calls;
        Error failure;

        @Override
        public ProfessionalResponsibilityGuidance decide(ProfessionalResponsibilityAnalysisSnapshot analysis) {
            calls++;
            if (failure != null) {
                throw failure;
            }
            return policy.decide(analysis);
        }
    }

    static final class Purchases implements ProfessionalPurchaseStore {
        final Map<UUID, ProfessionalReportEntitlement> entitlements = new HashMap<>();
        final Map<UUID, PurchasedProfessionalReportSnapshot> snapshots = new HashMap<>();

        @Override public void savePending(ProfessionalReportEntitlement entitlement) { throw new UnsupportedOperationException(); }
        @Override public void replaceExpiredCheckout(UUID entitlementId, String expired, ProfessionalCheckoutSession replacement,
                String priceConfigurationKey, Instant replacedAt) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProfessionalReportEntitlement> findEntitlementByAssessmentId(UUID assessmentId) {
            return Optional.ofNullable(entitlements.get(assessmentId));
        }
        @Override public Optional<ProfessionalReportEntitlement> findEntitlementById(UUID entitlementId) {
            return entitlements.values().stream().filter(e -> e.entitlementId().equals(entitlementId)).findFirst();
        }
        @Override public Optional<PurchasedProfessionalReportSnapshot> findSnapshotByAssessmentId(UUID assessmentId) {
            return Optional.ofNullable(snapshots.get(assessmentId));
        }
        @Override public boolean isEventProcessed(ProfessionalPaymentProvider provider, String eventId) { return false; }
        @Override public PurchaseConfirmationResult confirmAndFreeze(ProfessionalPaymentEvent event,
                PurchasedProfessionalReportSnapshot snapshot) { throw new UnsupportedOperationException(); }
        @Override public void saveComplimentary(ProfessionalReportEntitlement entitlement,
                PurchasedProfessionalReportSnapshot snapshot) { throw new UnsupportedOperationException(); }
    }

    static final class AnalysisStore implements ProfessionalResponsibilityAnalysisStore {
        final Map<UUID, ProfessionalResponsibilityAnalysisSnapshot> snapshots = new HashMap<>();
        final List<ResponsibilityAnalysisFailure> failures = new ArrayList<>();

        @Override public Optional<ProfessionalResponsibilityAnalysisSnapshot> findCompletedByAssessmentId(UUID id) {
            return Optional.ofNullable(snapshots.get(id));
        }
        @Override public ProfessionalResponsibilityAnalysisSnapshot saveCompletedIfAbsent(ProfessionalResponsibilityAnalysisSnapshot s) {
            return snapshots.computeIfAbsent(s.assessmentId(), key -> s);
        }
        @Override public void recordFailure(ResponsibilityAnalysisFailure failure) { failures.add(failure); }
        @Override public List<ResponsibilityAnalysisFailure> findFailuresByAssessmentId(UUID id) { return failures; }
    }

    static final class GuidanceStore implements ProfessionalResponsibilityGuidanceStore {
        final Map<UUID, ProfessionalResponsibilityGuidanceSnapshot> snapshots = new HashMap<>();

        @Override public Optional<ProfessionalResponsibilityGuidanceSnapshot> findByAssessmentId(UUID id) {
            return Optional.ofNullable(snapshots.get(id));
        }
        @Override public ProfessionalResponsibilityGuidanceSnapshot saveIfAbsent(ProfessionalResponsibilityGuidanceSnapshot s) {
            return snapshots.computeIfAbsent(s.assessmentId(), key -> s);
        }
    }

    static final class NarrativeStore implements ProfessionalPaidNarrativeStore {
        final Map<UUID, StoredProfessionalPaidNarrative> stored = new HashMap<>();

        @Override public Optional<StoredProfessionalPaidNarrative> findByAssessmentId(UUID id) {
            return Optional.ofNullable(stored.get(id));
        }
        @Override public StoredProfessionalPaidNarrative saveIfAbsent(StoredProfessionalPaidNarrative n) {
            return stored.computeIfAbsent(n.assessmentId(), key -> n);
        }
    }

    static final class ReportStore implements ProfessionalDetailedReportStore {
        final Map<UUID, ProfessionalDetailedReportSnapshot> reports = new HashMap<>();

        @Override public Optional<ProfessionalDetailedReportSnapshot> findByAssessmentId(UUID id) {
            return Optional.ofNullable(reports.get(id));
        }
        @Override public ProfessionalDetailedReportSnapshot saveIfAbsent(ProfessionalDetailedReportSnapshot s) {
            return reports.computeIfAbsent(s.assessmentId(), key -> s);
        }
    }
}
