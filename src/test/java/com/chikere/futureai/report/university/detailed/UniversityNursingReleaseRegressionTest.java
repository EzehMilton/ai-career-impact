package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.assessment.university.web.UniversityAssessmentForm;
import com.chikere.futureai.assessment.university.web.UniversityAssessmentFormMapper;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisUnavailableException;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisUnavailableException.Reason;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import com.chikere.futureai.coursemodule.university.policy.ModuleProbeFixtures;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV2;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.university.paid.ScriptedPaidNarrativeGenerator;
import com.chikere.futureai.narrative.university.paid.UniversityNarratives;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ModuleLearningInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeContext;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeStatus;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrativeValidator;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.pdf.university.openhtml.OpenHtmlUniversityDetailedReportPdfRenderer;
import com.chikere.futureai.report.university.application.UniversityCourseProfileComposer;
import com.chikere.futureai.report.university.application.UniversityReportVersions;
import com.chikere.futureai.trace.NarrativeRuleViolation;
import jakarta.validation.Validation;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Release regression for a professional, practice-heavy course: BSc Nursing as a student would enter it, through the
 * real form mapping, decision policy, module selection, module classification, guidance, paid-narrative validation,
 * report assembly, storage and PDF. Test data only: the course-profile judgments are the frozen Nursing fixture, and
 * each module borrows the frozen Stage 5C judgments of the closest captured Nursing module. No Jev, OpenAI or Stripe
 * call is made.
 */
class UniversityNursingReleaseRegressionTest {

    static final String DESCRIPTION = "The course prepares students for professional nursing practice through study of "
            + "anatomy and physiology, clinical assessment, evidence-based practice, medicines management, communication, "
            + "public health and patient care. Students learn through lectures, simulation, clinical skills teaching and "
            + "supervised placements. The course develops clinical reasoning, communication, teamwork, practical skills, "
            + "professional judgement and the ability to evaluate research evidence.";
    static final List<String> MODULES = List.of("Human Anatomy and Physiology", "Foundations of Nursing Practice",
            "Clinical Assessment", "Evidence-Based Practice", "Medicines Management", "Communication in Healthcare",
            "Public Health", "Adult Nursing Practice", "Clinical Skills", "Professional Practice Placement");
    static final List<String> SKILLS = List.of("Clinical reasoning", "Patient communication", "Practical clinical skills",
            "Teamwork", "Evidence evaluation", "Professional judgement", "Risk awareness", "Reflective practice",
            "Research literacy", "Decision making");
    static final List<String> CAREERS = List.of("Adult nursing", "Community nursing", "Public health",
            "Clinical practice", "Healthcare leadership");
    /** Ten modules, so the student chooses eight: here every module except Public Health and Clinical Skills. */
    static final List<Integer> SELECTION = List.of(1, 2, 3, 4, 5, 6, 8, 10);

    /** The captured Nursing module whose frozen judgments each supplied module borrows. */
    private static final Map<String, String> CLOSEST = Map.of(
            "Human Anatomy and Physiology", "Anatomy and Physiology",
            "Foundations of Nursing Practice", "Professional, Legal and Ethical Practice",
            "Clinical Assessment", "Clinical Skills and Simulation",
            "Evidence-Based Practice", "Evidence-Based Practice",
            "Medicines Management", "Pharmacology and Medicines Management",
            "Communication in Healthcare", "Communication and Compassionate Care",
            "Adult Nursing Practice", "Leadership and Management of Care",
            "Professional Practice Placement", "Clinical Placements");

    private final DetailedReportTestPorts ports = new DetailedReportTestPorts();

    // ---- input ----

    @Test
    void theStudentsInputIsAcceptedAndMappedWithTheOptionalFieldsAbsent() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertThat(factory.getValidator().validate(form())).isEmpty();
        }
        UniversityAssessmentState state = new UniversityAssessmentFormMapper().toState(form());
        assertThat(state.keyModules()).containsExactlyElementsOf(MODULES);
        assertThat(state.skillsDeveloped()).containsExactlyElementsOf(SKILLS);
        assertThat(state.preferredCareerAreas()).containsExactlyElementsOf(CAREERS);
        assertThat(state.institution()).isEmpty();
        assertThat(state.country()).isEmpty();
    }

    @Test
    void tenModulesNeedAnExplicitValidChoiceOfEightBeforeAnyProviderIsCalled() {
        UUID id = paidNursing();
        assertThat(ports.service().preparation(id)).hasValueSatisfying(preparation -> {
            assertThat(preparation.selectionRequired()).isTrue();
            assertThat(preparation.modules()).containsExactlyElementsOf(MODULES);
        });
        assertReason(() -> ports.service().prepare(id, Optional.empty()), Reason.MODULE_SELECTION_REQUIRED);
        for (List<Integer> invalid : List.of(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9), List.of(1, 1), List.of(11),
                List.<Integer>of())) {
            assertReason(() -> ports.service().prepare(id, Optional.of(invalid)), Reason.INVALID_SELECTION);
        }
        assertThat(ports.jevRequests).isEmpty();
        assertThat(ports.narrative.calls()).isZero();
    }

    // ---- the available report ----

    @Test
    void anAvailableReportKeepsDeterministicDataSafeguardsAndModulePairingAndItsPdfMatches() throws Exception {
        UUID id = paidNursing();
        ports.narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());

        var outcome = ports.service().prepare(id, Optional.of(SELECTION));

        var stored = outcome.stored().orElseThrow();
        assertThat(stored.paidNarrativeStatus()).isEqualTo(UniversityPaidNarrativeStatus.AVAILABLE);
        assertThat(stored.reportVersion()).isEqualTo(UniversityReportVersions.V3);
        assertThat(stored.guidancePolicyVersion()).isEqualTo("university-guidance-policy-v2");
        assertThat(ports.jevRequests).singleElement().satisfies(request -> assertThat(request.modules())
                .containsExactly("Human Anatomy and Physiology", "Foundations of Nursing Practice",
                        "Clinical Assessment", "Evidence-Based Practice", "Medicines Management",
                        "Communication in Healthcare", "Adult Nursing Practice", "Professional Practice Placement"));
        assertThat(ports.narrative.calls()).isEqualTo(1);

        UniversityDetailedReport report = stored.report();
        // Scores and bands are the deterministic course profile, untouched by the narrative.
        var expected = new UniversityCourseProfileComposer().compose(ports.assessments.get(id).result().profile());
        assertThat(report.profile().measures()).isEqualTo(expected.measures());
        // Modules are the selected ones, in course order, each with its own narrative.
        assertThat(report.modules().cards()).extracting(UniversityDetailedReport.ModuleCard::title)
                .containsExactlyElementsOf(ports.jevRequests.getFirst().modules());
        assertThat(report.practical().moduleLearning()).isNotEmpty().allSatisfy(module -> {
            assertThat(MODULES).contains(module.title());
            assertThat(module.uses()).isNotEmpty().allSatisfy(use -> assertThat(use).contains(module.title()));
        });
        // The professional-standards safeguard is kept, and the course is not treated as a programming course.
        var context = context(id);
        assertThat(context.professionalStandardsRelevant()).isTrue();
        assertThat(context.programmingVocabularySupported()).isFalse();
        assertThat(report.safeguards()).extracting(UniversityDetailedReport.Entry::title)
                .contains("Professional and client-facing standards");
        String text = customerText(report);
        assertThat(text).doesNotContainPattern("\\b(?:code|coding|debug\\w*|SQL|programming)\\b")
                .doesNotContainPattern("(?i)\\bAI\\s+(?:can|could|will|should|may)\\s+(?:diagnose|prescribe)")
                .doesNotContainPattern("(?i)AI\\s+(?:to\\s+)?calculate\\s+(?:the\\s+)?doses?\\b");

        String pdf = pdfText(report);
        for (var measure : report.profile().measures()) {
            assertThat(pdf).contains(measure.name(), measure.score());
        }
        for (var card : report.modules().cards()) {
            assertThat(pdf).contains(card.title());
        }
        assertThat(pdf).contains("Professional and client-facing standards");

        // Reopening reuses the stored report: no second Jev or OpenAI call, the same snapshot.
        var again = ports.service().prepare(id, Optional.of(SELECTION));
        assertThat(again.reused()).isTrue();
        assertThat(again.stored()).contains(stored);
        assertThat(ports.jevRequests).hasSize(1);
        assertThat(ports.narrative.calls()).isEqualTo(1);
    }

    // ---- clinical safety ----

    @Test
    void aDraftDescribingAiSettingDosesForRealPatientsIsRepairedOnceWithEveryIdUnchanged() {
        UUID id = paidNursing();
        ports.narrative.then(context -> withFirstUse(UniversityNarratives.valid(context),
                "Using AI to calculate the dose for your placement patients"))
                .then(ScriptedPaidNarrativeGenerator.cleanNarrative());

        var stored = ports.service().prepare(id, Optional.of(SELECTION)).stored().orElseThrow();

        assertThat(stored.paidNarrativeStatus()).isEqualTo(UniversityPaidNarrativeStatus.AVAILABLE);
        assertThat(ports.narrative.calls()).isEqualTo(2);
        assertThat(ports.narrative.lastFeedback.rule()).isEqualTo(UniversityPaidNarrativeValidator.CLINICAL);
        assertThat(customerText(stored.report())).doesNotContain("calculate the dose");
    }

    @Test
    void unsafeClinicalWordingTwiceFallsBackToACompleteDeterministicReportAndPdf() throws Exception {
        UUID id = paidNursing();
        Function<UniversityPaidNarrativeContext, UniversityPaidNarrative> unsafe = context -> withFirstUse(
                UniversityNarratives.valid(context), "Letting AI diagnose what is wrong with a patient on placement");
        ports.narrative.then(unsafe).then(unsafe);

        var stored = ports.service().prepare(id, Optional.of(SELECTION)).stored().orElseThrow();

        assertThat(stored.paidNarrativeStatus()).isEqualTo(UniversityPaidNarrativeStatus.FALLBACK);
        assertThat(ports.narrative.calls()).isEqualTo(2);
        var report = stored.report();
        assertThat(report.perspectives().modelWritten()).isFalse();
        assertThat(report.practical().studyStrategy().personalised()).isFalse();
        assertThat(report.practical().moduleLearning()).isNotEmpty().allSatisfy(module -> {
            assertThat(module.explanation()).isNull();
            assertThat(module.guidance()).isNotBlank();
        });
        assertThat(report.safeguards()).extracting(UniversityDetailedReport.Entry::title)
                .contains("Professional and client-facing standards");
        assertThat(report.plan()).isNotEmpty();
        assertThat(customerText(report)).doesNotContain("diagnose");
        assertThat(pdfText(report)).contains("Your action plan", "Professional and client-facing standards");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Using AI to calculate the correct dose for your patient",
            "Letting AI decide the treatment for a patient you are caring for",
            "Following the AI's advice on medication for a patient", "AI can prescribe a suitable medicine for you"})
    void aiDoingClinicalWorkIsRejected(String use) {
        var context = nursingContext();
        assertThatThrownBy(() -> new UniversityPaidNarrativeValidator().validate(
                withFirstUse(UniversityNarratives.valid(context), use), context))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation ->
                        assertThat(violation.rule()).isEqualTo(UniversityPaidNarrativeValidator.CLINICAL));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Asking AI to set practice dose calculation questions, then working them by hand",
            "Having AI quiz you on the diagnostic criteria for a condition you are studying",
            "Asking AI to explain how a drug class acts, then checking it against your course notes"})
    void studyUsesOfClinicalMaterialPass(String use) {
        var context = nursingContext();
        var narrative = withFirstUse(UniversityNarratives.valid(context), use);
        assertThat(new UniversityPaidNarrativeValidator().validate(narrative, context)).isEqualTo(narrative);
    }

    // ---- helpers ----

    private static UniversityAssessmentForm form() {
        var form = new UniversityAssessmentForm();
        form.setCourseTitle("BSc Nursing");
        form.setCourseDescription(DESCRIPTION);
        form.setKeyModules(String.join("\n", MODULES));
        form.setSkillsDeveloped(String.join("\n", SKILLS));
        form.setPreferredCareerAreas(String.join("\n", CAREERS));
        return form;
    }

    /** The mapped assessment, decided by the real policy on the frozen Nursing judgments, with an ACTIVE v2 purchase. */
    private UUID paidNursing() {
        UUID id = UUID.randomUUID();
        UniversityAssessmentState state = new UniversityAssessmentFormMapper().toState(form());
        var judgments = new LocalFixtureUniversityJudgmentProvider("nursing").judge(state);
        var profile = new UniversityDecisionPolicyV2().decide(judgments);
        ports.assessments.put(id, new StoredUniversityAssessment(id, Instant.parse("2026-11-06T09:00:00Z"), state,
                judgments, new UniversityAssessmentResult(profile, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), profile.policyVersion()), Optional.empty()));
        ports.purchase(id, UniversityEntitlementStatus.ACTIVE, true);
        ports.judgments = UniversityNursingReleaseRegressionTest::closestJudgments;
        return id;
    }

    /** As Jev would answer this request: each module with the frozen judgments of its closest captured module. */
    private static UniversityModuleJudgmentResult closestJudgments(UniversityModuleAnalysisRequest request) {
        var probe = ModuleProbeFixtures.load("nursing");
        List<UniversityModuleJudgmentSet> sets = new ArrayList<>();
        int index = 1;
        for (var entry : request.keyedModules().entrySet()) {
            var source = probe.modules().stream()
                    .filter(set -> set.moduleTitle().equals(CLOSEST.get(entry.getValue()))).findFirst().orElseThrow();
            sets.add(new UniversityModuleJudgmentSet(index++, entry.getKey(), entry.getValue(),
                    source.describedClearly(), source.professionalStandards(), source.aiLearningAssistance(),
                    source.independentDemonstration(), source.humanJudgement(), source.interpersonalWork(),
                    source.practicalApplication(), source.technicalQuantitative()));
        }
        return new UniversityModuleJudgmentResult(probe.schemaVersion(), UniversityModuleJudgmentSource.JEV,
                probe.model(), Optional.of("req_nursing_regression"), OptionalLong.of(1), OptionalLong.of(1), sets);
    }

    /** The narrative context the paid report would send for this course, after a stored analysis. */
    private UniversityPaidNarrativeContext context(UUID id) {
        var modules = ports.moduleSnapshots.get(id);
        var guidance = new com.chikere.futureai.coursemodule.university.guidance.UniversityGuidancePolicyV2()
                .decide(modules);
        return new UniversityDetailedReportAssembler().narrativeContext(ports.assessments.get(id), modules, guidance);
    }

    private UniversityPaidNarrativeContext nursingContext() {
        UUID id = paidNursing();
        ports.narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());
        ports.service().prepare(id, Optional.of(SELECTION));
        return context(id);
    }

    private static UniversityPaidNarrative withFirstUse(UniversityPaidNarrative draft, String use) {
        List<ModuleLearningInsight> learning = new ArrayList<>(draft.moduleLearningInsights());
        var first = learning.getFirst();
        List<String> uses = new ArrayList<>(first.aiLearningUses());
        uses.set(0, use);
        learning.set(0, new ModuleLearningInsight(first.moduleId(), first.explanation(), uses,
                first.independentCapability(), first.studyExperiment()));
        return new UniversityPaidNarrative(draft.executiveSummary(), draft.measuresTogether(), draft.coursePattern(),
                learning, draft.humanCapabilityInsights(), draft.skillInsights(), draft.actionPlanNotes(),
                draft.aiStudyStrategy(), draft.closingPerspective());
    }

    private static void assertReason(Runnable call, Reason reason) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(UniversityModuleAnalysisUnavailableException.class,
                failure -> assertThat(failure.reason()).isEqualTo(reason));
    }

    private static String customerText(UniversityDetailedReport report) throws RuntimeException {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                    .writeValueAsString(report);
        }
        catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String pdfText(UniversityDetailedReport report) throws Exception {
        try (var document = Loader.loadPDF(new OpenHtmlUniversityDetailedReportPdfRenderer().render(report))) {
            return new PDFTextStripper().getText(document).replaceAll("\\s+", " ");
        }
    }
}
