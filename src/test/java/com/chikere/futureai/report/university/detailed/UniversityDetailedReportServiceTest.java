package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.coursemodule.university.analysis.ModuleAnalysisTestData;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisUnavailableException;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisUnavailableException.Reason;
import com.chikere.futureai.narrative.university.paid.ScriptedPaidNarrativeGenerator;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeStatus;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The university-report-v2 lifecycle: entitlement, routing, single generation, reuse, provisional retry, selection. */
class UniversityDetailedReportServiceTest {

    private final DetailedReportTestPorts ports = new DetailedReportTestPorts();

    // ---- generation and reuse ----

    @Test
    void theFirstRequestMakesOneJevCallAndOneNarrativeCallAndFreezesEveryVersion() {
        UUID id = ports.paidCourseProfile(8);
        ports.narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());

        var outcome = ports.service().prepare(id, Optional.empty());

        assertThat(outcome.provisional()).isFalse();
        assertThat(outcome.reused()).isFalse();
        assertThat(ports.jevRequests).hasSize(1);
        assertThat(ports.narrative.calls()).isEqualTo(1);
        var stored = outcome.stored().orElseThrow();
        assertThat(ports.reports).containsEntry(id, stored);
        assertThat(stored.reportVersion()).isEqualTo("university-report-v3");
        assertThat(stored.pdfVersion()).isEqualTo("university-report-pdf-v3");
        assertThat(stored.decisionPolicyVersion()).isEqualTo("university-decision-v2");
        assertThat(stored.moduleSchemaVersion()).isEqualTo("university-module-v1");
        assertThat(stored.modulePolicyVersion()).isEqualTo("university-module-policy-v1");
        assertThat(stored.guidancePolicyVersion()).isEqualTo("university-guidance-policy-v1");
        assertThat(stored.paidNarrativeVersion()).isEqualTo("university-paid-narrative-v2");
        assertThat(stored.paidNarrativeStatus()).isEqualTo(UniversityPaidNarrativeStatus.AVAILABLE);
        assertThat(stored.moduleAnalysisId()).isEqualTo(ports.moduleSnapshots.get(id).snapshotId());
        assertThat(stored.generatedAt()).isEqualTo(DetailedReportTestPorts.CLOCK.instant());
        assertThat(stored.report().perspectives().modelWritten()).isTrue();
        var context = new UniversityDetailedReportAssembler().narrativeContext(ports.assessments.get(id),
                ports.moduleSnapshots.get(id), new com.chikere.futureai.coursemodule.university.guidance
                        .UniversityGuidancePolicyV1().decide(ports.moduleSnapshots.get(id)));
        var narrative = ScriptedPaidNarrativeGenerator.cleanNarrative().apply(context);
        assertThat(stored.report().perspectives().overall()).isEqualTo(narrative.executiveSummary());
        assertThat(stored.report().practical().studyStrategy().personalised()).isTrue();
        assertThat(stored.report().practical().moduleLearning()).extracting(UniversityDetailedReport.ModuleLearning::uses)
                .containsExactlyElementsOf(narrative.moduleLearningInsights().stream()
                        .map(insight -> insight.aiLearningUses()).toList());
    }

    @Test
    void aRepeatRequestReturnsTheStoredSnapshotWithNoJevNoNarrativeAndNoRegeneration() {
        UUID id = ports.paidCourseProfile(8);
        ports.narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());
        var first = ports.service().prepare(id, Optional.empty());

        var again = ports.service().prepare(id, Optional.of(List.of(1)));

        assertThat(again.reused()).isTrue();
        assertThat(again.stored()).isEqualTo(first.stored());
        assertThat(ports.jevRequests).hasSize(1);
        assertThat(ports.narrative.calls()).isEqualTo(1);
        assertThat(ports.reportSaves).isEqualTo(1);
        assertThat(ports.service().find(id)).isEqualTo(first.stored());
    }

    @Test
    void aStoredReportIsImmutableWhenTheNarrativeProviderLaterChanges() {
        UUID id = ports.paidCourseProfile(8);
        ports.narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());
        var first = ports.service().prepare(id, Optional.empty()).stored().orElseThrow();

        ports.narrative = new ScriptedPaidNarrativeGenerator().then(ScriptedPaidNarrativeGenerator.differentNarrative());
        var again = ports.service().prepare(id, Optional.empty());

        assertThat(again.stored()).contains(first);
        assertThat(ports.narrative.calls()).isZero();
    }

    @Test
    void aFinalNarrativeFallbackIsStoredWithDeterministicCopy() {
        UUID id = ports.paidCourseProfile(8);
        java.util.function.Function<com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext,
                com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative> forbidden = context -> {
                    var valid = ScriptedPaidNarrativeGenerator.cleanNarrative().apply(context);
                    return new com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative(
                            "This course is AI-proof.", valid.measuresTogether(), valid.coursePattern(),
                            valid.moduleLearningInsights(), valid.humanCapabilityInsights(), valid.skillInsights(),
                            valid.actionPlanNotes(), valid.aiStudyStrategy(), valid.closingPerspective());
                };
        ports.narrative.then(forbidden).then(forbidden);

        var outcome = ports.service().prepare(id, Optional.empty());

        assertThat(outcome.provisional()).isFalse();
        var stored = outcome.stored().orElseThrow();
        assertThat(stored.paidNarrativeStatus()).isEqualTo(UniversityPaidNarrativeStatus.FALLBACK);
        assertThat(stored.report().perspectives().modelWritten()).isFalse();
        assertThat(stored.report().perspectives().overall()).doesNotContain("AI-proof").isNotBlank();
        // The deterministic copy still gives every section, the AI-study strategy included.
        assertThat(stored.report().practical().studyStrategy().personalised()).isFalse();
        assertThat(stored.report().practical().studyStrategy().verify()).isNotEmpty();
        assertThat(stored.report().practical().moduleLearning()).allSatisfy(module -> {
            assertThat(module.explanation()).isNull();
            assertThat(module.uses()).isEmpty();
            assertThat(module.guidance()).isNotBlank();
        });
    }

    // ---- retryable narrative ----

    @Test
    void aRetryableNarrativeKeepsTheReportProvisionalAndARetryNeverRerunsJev() {
        UUID id = ports.paidCourseProfile(8);
        ports.narrativeConfigured = false;

        var provisional = ports.service().prepare(id, Optional.empty());

        assertThat(provisional.provisional()).isTrue();
        assertThat(ports.reports).isEmpty();
        assertThat(provisional.report().perspectives().modelWritten()).isFalse();
        var shown = ports.service().provisional(id).orElseThrow();
        assertThat(shown.modules()).isEqualTo(provisional.report().modules());
        assertThat(shown.profile()).isEqualTo(provisional.report().profile());
        assertThat(ports.service().find(id)).isEmpty();

        ports.narrativeConfigured = true;
        ports.narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());
        var retried = ports.service().prepare(id, Optional.empty());

        assertThat(retried.provisional()).isFalse();
        assertThat(ports.jevRequests).hasSize(1);
        assertThat(ports.narrative.calls()).isEqualTo(1);
        // The retry changes only the narrative: scores, classifications, guidance and plan are identical.
        var report = retried.report();
        assertThat(report.profile()).isEqualTo(provisional.report().profile());
        assertThat(report.modules()).isEqualTo(provisional.report().modules());
        // The narrative adds only interpretation beside them: skill meanings and plan notes.
        assertThat(report.skills()).extracting(UniversityDetailedReport.Skill::title, UniversityDetailedReport.Skill::explanation)
                .isEqualTo(provisional.report().skills().stream().map(skill -> org.assertj.core.groups.Tuple.tuple(
                        skill.title(), skill.explanation())).toList());
        assertThat(report.safeguards()).isEqualTo(provisional.report().safeguards());
        assertThat(report.plan()).extracting(UniversityDetailedReport.Phase::actions)
                .isEqualTo(provisional.report().plan().stream().map(UniversityDetailedReport.Phase::actions).toList());
        assertThat(provisional.report().plan()).allSatisfy(phase -> assertThat(phase.steps()).allSatisfy(step ->
                assertThat(step.note()).isNull()));
        assertThat(ports.service().provisional(id)).isEmpty();
    }

    // ---- entitlement and version routing ----

    @Test
    void withoutAnActiveEntitlementNothingIsCalledOrShown() {
        UUID id = ports.paidCourseProfile(8);
        ports.purchase(id, UniversityEntitlementStatus.PENDING, true);

        assertThatThrownBy(() -> ports.service().prepare(id, Optional.empty()))
                .isInstanceOfSatisfying(UniversityModuleAnalysisUnavailableException.class, denied ->
                        assertThat(denied.reason()).isEqualTo(Reason.NOT_ENTITLED));
        assertThat(ports.service().find(id)).isEmpty();
        assertThat(ports.service().provisional(id)).isEmpty();
        assertThat(ports.service().preparation(id)).isEmpty();
        assertThat(ports.jevRequests).isEmpty();
        assertThat(ports.narrative.calls()).isZero();
    }

    @Test
    void aHistoricalV1PurchaseIsNeverRoutedToTheV2Report() {
        UUID id = UUID.randomUUID();
        ports.assessments.put(id, ModuleAnalysisTestData.assessment(id, 8));
        ports.purchase(id, UniversityEntitlementStatus.ACTIVE, false);

        assertThatThrownBy(() -> ports.service().prepare(id, Optional.empty()))
                .isInstanceOfSatisfying(UniversityModuleAnalysisUnavailableException.class, denied ->
                        assertThat(denied.reason()).isEqualTo(Reason.NOT_ENTITLED));
        assertThat(ports.service().preparation(id)).isEmpty();
        assertThat(ports.jevRequests).isEmpty();
    }

    @Test
    void aV1AssessmentIsNotGeneratedAsV2EvenWithACourseProfilePurchase() {
        UUID id = UUID.randomUUID();
        ports.assessments.put(id, ModuleAnalysisTestData.assessment(id, 8));
        ports.purchase(id, UniversityEntitlementStatus.ACTIVE, true);

        assertThatThrownBy(() -> ports.service().prepare(id, Optional.empty()))
                .isInstanceOfSatisfying(UniversityModuleAnalysisUnavailableException.class, denied ->
                        assertThat(denied.reason()).isEqualTo(Reason.ASSESSMENT_NOT_FOUND));
        assertThat(ports.jevRequests).isEmpty();
    }

    // ---- module selection ----

    @Test
    void eightOrFewerModulesNeedNoSelectionAndAreAllIncluded() {
        UUID id = ports.paidCourseProfile(6);
        var preparation = ports.service().preparation(id).orElseThrow();
        assertThat(preparation.selectionRequired()).isFalse();
        ports.narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());

        var report = ports.service().prepare(id, Optional.empty()).report();

        assertThat(report.modules().cards()).hasSize(6);
        assertThat(report.modules().selectionNote()).isEqualTo("All 6 modules listed for the course are included.");
    }

    @Test
    void moreThanEightModulesRequireASelectionBeforeAnyJevCall() {
        UUID id = ports.paidCourseProfile(11);
        var preparation = ports.service().preparation(id).orElseThrow();
        assertThat(preparation.selectionRequired()).isTrue();
        assertThat(preparation.maximum()).isEqualTo(8);
        assertThat(preparation.modules()).hasSize(11).startsWith("Module 1", "Module 2");

        assertThatThrownBy(() -> ports.service().prepare(id, Optional.empty()))
                .isInstanceOfSatisfying(UniversityModuleAnalysisUnavailableException.class, denied ->
                        assertThat(denied.reason()).isEqualTo(Reason.MODULE_SELECTION_REQUIRED));
        assertThatThrownBy(() -> ports.service().prepare(id, Optional.of(List.of())))
                .isInstanceOfSatisfying(UniversityModuleAnalysisUnavailableException.class, denied ->
                        assertThat(denied.reason()).isEqualTo(Reason.INVALID_SELECTION));
        assertThatThrownBy(() -> ports.service().prepare(id, Optional.of(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9))))
                .isInstanceOfSatisfying(UniversityModuleAnalysisUnavailableException.class, denied ->
                        assertThat(denied.reason()).isEqualTo(Reason.INVALID_SELECTION));
        assertThatThrownBy(() -> ports.service().prepare(id, Optional.of(List.of(12))))
                .isInstanceOfSatisfying(UniversityModuleAnalysisUnavailableException.class, denied ->
                        assertThat(denied.reason()).isEqualTo(Reason.INVALID_SELECTION));
        assertThat(ports.jevRequests).isEmpty();
        assertThat(ports.narrative.calls()).isZero();
    }

    @Test
    void aValidSelectionIsAnalysedInCourseOrderAndExplainedInTheReport() {
        UUID id = ports.paidCourseProfile(11);
        ports.narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());

        var report = ports.service().prepare(id, Optional.of(List.of(9, 2, 5))).report();

        assertThat(ports.jevRequests.getFirst().modules()).containsExactly("Module 2", "Module 5", "Module 9");
        assertThat(report.modules().cards()).extracting(UniversityDetailedReport.ModuleCard::title)
                .containsExactly("Module 2", "Module 5", "Module 9");
        assertThat(report.modules().selectionNote()).contains("reflects the 3 modules you selected from the 11");
    }

    @Test
    void anExistingModuleAnalysisSkipsTheSelectionStep() {
        UUID id = ports.paidCourseProfile(11);
        ports.narrativeConfigured = false;
        ports.service().prepare(id, Optional.of(List.of(1, 2)));

        var preparation = ports.service().preparation(id).orElseThrow();

        assertThat(preparation.analysisExists()).isTrue();
        assertThat(preparation.selectionRequired()).isFalse();
    }

    @Test
    void aComplimentaryActiveEntitlementBehavesLikeAPaidOne() {
        // The test entitlement is a COMPLIMENTARY provider entitlement; ACTIVE is all that matters.
        UUID id = ports.paidCourseProfile(8);
        ports.narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());

        assertThat(ports.service().prepare(id, Optional.empty()).stored()).isPresent();
    }
}
