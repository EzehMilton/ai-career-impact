package com.chikere.futureai.coursemodule.university.guidance;

import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisItem;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.guidance.UniversityGuidanceItem.Section;
import com.chikere.futureai.coursemodule.university.guidance.UniversitySkillPriority.SkillTheme;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleClassification;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.PRACTICAL_OR_EXPERIENTIAL;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.TECHNICAL_CORE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** university-guidance-policy-v1 over the seven frozen course snapshots, and its invariants. No Jev, no OpenAI. */
class UniversityGuidancePolicyV1Test {

    private final UniversityGuidancePolicyV1 policy = new UniversityGuidancePolicyV1();
    private final Map<String, UniversityModuleAnalysisSnapshot> courses = GuidanceFixtures.all();

    // ---- versions and provenance ----

    @Test
    void everyItemRecordsThePolicyVersionAndItsProvenance() {
        for (var course : courses.entrySet()) {
            var guidance = policy.decide(course.getValue());
            assertThat(guidance.policyVersion()).isEqualTo("university-guidance-policy-v1");
            assertThat(guidance.moduleAnalysisPolicyVersion()).isEqualTo("university-module-policy-v1");
            assertThat(guidance.moduleAnalysisSnapshotId()).isEqualTo(course.getValue().snapshotId());
            Set<Integer> analysed = new HashSet<>(course.getValue().selectedModuleIds());
            Set<String> ids = new HashSet<>();
            for (UniversityGuidanceItem item : items(guidance)) {
                assertThat(ids.add(item.guidanceId())).as(item.guidanceId()).isTrue();
                assertThat(item.ruleId()).isNotBlank();
                assertThat(item.moduleIds()).isNotEmpty().isSorted().allSatisfy(id -> assertThat(analysed).contains(id));
                assertThat(item.categories()).hasSameSizeAs(item.moduleIds());
                assertThat(item.evidence()).isNotEmpty();
            }
            for (var skill : guidance.skillPriorities()) {
                assertThat(skill.moduleIds()).isNotEmpty().allSatisfy(id -> assertThat(analysed).contains(id));
            }
            Set<String> actionIds = new HashSet<>();
            for (var action : actions(guidance)) {
                assertThat(actionIds.add(action.actionId())).as(action.actionId()).isTrue();
                assertThat(analysed).containsAll(action.moduleIds());
            }
        }
    }

    @Test
    void onlyUniversityModulePolicyV1SnapshotsAreAccepted() {
        var snapshot = courses.get("law");
        var other = new UniversityModuleAnalysisSnapshot(snapshot.snapshotId(), snapshot.assessmentId(),
                snapshot.schemaVersion(), "university-module-policy-v2", snapshot.judgmentSource(), snapshot.model(),
                snapshot.providerRequestId(), snapshot.inputTokens(), snapshot.outputTokens(), snapshot.courseTitle(),
                snapshot.courseDescription(), snapshot.skillsDeveloped(), snapshot.items(), snapshot.createdAt(),
                snapshot.completedAt());
        assertThatThrownBy(() -> policy.decide(other)).hasMessageContaining("university-module-policy-v1");
    }

    // ---- sections ----

    @Test
    void aiLearningOpportunitiesExcludePracticalModulesAndSpreadAcrossKindsOfModule() {
        for (var course : courses.entrySet()) {
            var opportunities = policy.decide(course.getValue()).aiLearningOpportunities();
            assertThat(opportunities).as(course.getKey()).hasSizeLessThanOrEqualTo(4)
                    .allSatisfy(item -> {
                        assertThat(item.section()).isEqualTo(Section.AI_LEARNING_OPPORTUNITY);
                        assertThat(item.categories()).doesNotContain(PRACTICAL_OR_EXPERIENTIAL);
                        assertThat(item.evidence().get("ai_learning_assistance")).isGreaterThanOrEqualTo(2.5);
                    });
        }
        // Not the four highest AI scores: an AI-assisted-learning module ranks first even with lower assistance.
        var business = policy.decide(courses.get("business-management")).aiLearningOpportunities();
        assertThat(business).extracting(UniversityGuidanceItem::ruleId).startsWith("GA_AI_ASSISTED_LEARNING");
        assertThat(business).extracting(UniversityGuidanceItem::ruleId)
                .containsExactly("GA_AI_ASSISTED_LEARNING", "GA_TECHNICAL_CORE", "GA_HUMAN_JUDGEMENT_LED",
                        "GA_HUMAN_JUDGEMENT_LED");
        // Graphic Design's studio modules are practical, so only two opportunities remain.
        assertThat(policy.decide(courses.get("graphic-design")).aiLearningOpportunities()).extracting(
                UniversityGuidanceItem::title).containsExactly("Design History and Theory", "Final Major Project");
    }

    @Test
    void ownCapabilityAreasNeverSayAiCannotDoTheWork() {
        for (var course : courses.entrySet()) {
            var areas = policy.decide(course.getValue()).ownCapabilityAreas();
            assertThat(areas).as(course.getKey()).isNotEmpty().hasSizeLessThanOrEqualTo(4).allSatisfy(item -> {
                assertThat(item.text()).doesNotContainIgnoringCase("cannot").doesNotContainIgnoringCase("can't");
                assertThat(item.ruleId()).startsWith("GB_");
            });
        }
        var nursing = policy.decide(courses.get("nursing")).ownCapabilityAreas();
        assertThat(nursing).extracting(UniversityGuidanceItem::title).containsExactly("Clinical Placements",
                "Clinical Skills and Simulation", "Communication and Compassionate Care", "Leadership and Management of Care");
        assertThat(nursing.getFirst().ruleId()).isEqualTo("GB_PRACTICAL_WITH_PEOPLE");
        assertThat(policy.decide(courses.get("law")).ownCapabilityAreas().getFirst().title()).isEqualTo("Law Clinic");
    }

    @Test
    void technicalFoundationsComeOnlyFromTechnicalModules() {
        for (var course : courses.entrySet()) {
            var technical = policy.decide(course.getValue()).technicalFoundations();
            assertThat(technical).hasSizeLessThanOrEqualTo(3).allSatisfy(item ->
                    assertThat(item.categories().contains(TECHNICAL_CORE)
                            || item.evidence().get("technical_quantitative") >= 3.0).isTrue());
        }
        assertThat(policy.decide(courses.get("english-literature")).technicalFoundations()).isEmpty();
        assertThat(policy.decide(courses.get("mechanical-engineering")).technicalFoundations()).extracting(
                UniversityGuidanceItem::title).containsExactly("Engineering Mathematics", "Thermodynamics",
                "Control Systems");
    }

    @Test
    void safeguardsAreAggregatedAndAppearOnlyWithTheirEvidence() {
        Map<String, List<String>> expected = Map.of(
                "business-management", List.of("SG1_INDEPENDENT_DEMONSTRATION", "SG3_VERIFY_TECHNICAL_OUTPUT",
                        "SG4_VERIFY_SOURCES"),
                "computer-science", List.of("SG1_INDEPENDENT_DEMONSTRATION", "SG3_VERIFY_TECHNICAL_OUTPUT"),
                "english-literature", List.of("SG1_INDEPENDENT_DEMONSTRATION", "SG4_VERIFY_SOURCES"),
                "graphic-design", List.of("SG1_INDEPENDENT_DEMONSTRATION"),
                "law", List.of("SG1_INDEPENDENT_DEMONSTRATION", "SG2_PROFESSIONAL_STANDARDS", "SG4_VERIFY_SOURCES"),
                "mechanical-engineering", List.of("SG1_INDEPENDENT_DEMONSTRATION", "SG3_VERIFY_TECHNICAL_OUTPUT"),
                "nursing", List.of("SG1_INDEPENDENT_DEMONSTRATION", "SG2_PROFESSIONAL_STANDARDS",
                        "SG3_VERIFY_TECHNICAL_OUTPUT", "SG4_VERIFY_SOURCES"));
        for (var course : courses.entrySet()) {
            var safeguards = policy.decide(course.getValue()).safeguards();
            assertThat(safeguards).extracting(UniversityGuidanceItem::ruleId).as(course.getKey())
                    .isEqualTo(expected.get(course.getKey()));
            var flagged = course.getValue().items().stream().filter(item -> item.classification().hasFlag(
                    com.chikere.futureai.coursemodule.university.policy.UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE))
                    .map(UniversityModuleAnalysisItem::moduleId).toList();
            assertThat(safeguards.getFirst().moduleIds()).isEqualTo(flagged);
            assertThat(safeguards.getFirst().text()).contains("Check your institution's assessment rules")
                    .doesNotContainIgnoringCase("permitted").doesNotContainIgnoringCase("allowed");
        }
        var law = policy.decide(courses.get("law")).safeguards().get(1);
        assertThat(law.title()).isEqualTo("Professional and client-facing standards");
        assertThat(law.text()).startsWith("Law Clinic appears").contains("support rather than authority")
                .doesNotContain("regulated");
        assertThat(policy.decide(courses.get("graphic-design")).safeguards().getFirst().text())
                .contains("these modules strongly depend");
    }

    // ---- skills ----

    @Test
    void eachCourseGetsThreeToFiveSkillPrioritiesFromItsOwnEvidence() {
        Map<String, List<SkillTheme>> expected = Map.of(
                "business-management", List.of(SkillTheme.CRITICAL_EVALUATION,
                        SkillTheme.COMMUNICATION_AND_COLLABORATION, SkillTheme.AI_LITERACY),
                "computer-science", List.of(SkillTheme.TECHNICAL_FOUNDATIONS, SkillTheme.AI_LITERACY,
                        SkillTheme.CRITICAL_EVALUATION),
                "english-literature", List.of(SkillTheme.CRITICAL_EVALUATION, SkillTheme.INDEPENDENT_WORK,
                        SkillTheme.AI_LITERACY),
                "graphic-design", List.of(SkillTheme.INDEPENDENT_WORK, SkillTheme.PRACTICAL_APPLICATION,
                        SkillTheme.CRITICAL_EVALUATION, SkillTheme.AI_LITERACY),
                "law", List.of(SkillTheme.CRITICAL_EVALUATION, SkillTheme.INDEPENDENT_WORK, SkillTheme.AI_LITERACY),
                "mechanical-engineering", List.of(SkillTheme.TECHNICAL_FOUNDATIONS, SkillTheme.INDEPENDENT_WORK,
                        SkillTheme.AI_LITERACY),
                "nursing", List.of(SkillTheme.PROFESSIONAL_JUDGEMENT, SkillTheme.COMMUNICATION_AND_COLLABORATION,
                        SkillTheme.CRITICAL_EVALUATION, SkillTheme.PRACTICAL_APPLICATION,
                        SkillTheme.TECHNICAL_FOUNDATIONS));
        for (var course : courses.entrySet()) {
            var skills = policy.decide(course.getValue()).skillPriorities();
            assertThat(skills).extracting(UniversitySkillPriority::theme).as(course.getKey())
                    .isEqualTo(expected.get(course.getKey()));
            assertThat(skills).extracting(UniversitySkillPriority::rank)
                    .isEqualTo(java.util.stream.IntStream.rangeClosed(1, skills.size()).boxed().toList());
            assertThat(skills.stream().map(UniversitySkillPriority::strength).toList())
                    .isSortedAccordingTo(java.util.Comparator.reverseOrder());
        }
        // Only Computer Science needed a relaxed theme, and it says so.
        var computing = policy.decide(courses.get("computer-science")).skillPriorities();
        assertThat(computing.getLast().relaxed()).isTrue();
        assertThat(computing.getLast().explanation()).startsWith("Final-Year Individual Project centres on");
    }

    @Test
    void aiLiteracyIsNeverUniversalAndNeverOutranksTheEvidenceItBorrows() {
        // High AI assistance everywhere does not lead the list for a technical course: technical foundations do.
        assertThat(policy.decide(courses.get("computer-science")).skillPriorities().getFirst().theme())
                .isEqualTo(SkillTheme.TECHNICAL_FOUNDATIONS);
        // With other strong themes available, Nursing has no AI-literacy priority at all.
        assertThat(policy.decide(courses.get("nursing")).skillPriorities()).extracting(UniversitySkillPriority::theme)
                .doesNotContain(SkillTheme.AI_LITERACY);
    }

    @Test
    void overlappingAdviceIsMergedIntoOneThemeAndOneSafeguardOfEachKind() {
        for (var course : courses.entrySet()) {
            var guidance = policy.decide(course.getValue());
            assertThat(guidance.skillPriorities()).extracting(UniversitySkillPriority::theme).doesNotHaveDuplicates();
            assertThat(guidance.safeguards()).extracting(UniversityGuidanceItem::ruleId).doesNotHaveDuplicates();
            for (var horizon : List.of(guidance.next30Days(), guidance.thisTerm(), guidance.next6To12Months())) {
                assertThat(horizon).extracting(UniversityGuidanceAction::ruleId).doesNotHaveDuplicates();
            }
        }
    }

    // ---- action plan ----

    @Test
    void eachHorizonHasTwoToFourActionsEachGatedOnItsTheme() {
        Map<String, SkillTheme> gates = Map.ofEntries(
                Map.entry("D3T", SkillTheme.TECHNICAL_FOUNDATIONS), Map.entry("D4", SkillTheme.TECHNICAL_FOUNDATIONS),
                Map.entry("T4", SkillTheme.TECHNICAL_FOUNDATIONS), Map.entry("M3", SkillTheme.TECHNICAL_FOUNDATIONS),
                Map.entry("D3S", SkillTheme.CRITICAL_EVALUATION), Map.entry("T3", SkillTheme.CRITICAL_EVALUATION),
                Map.entry("D6", SkillTheme.PRACTICAL_APPLICATION), Map.entry("T2", SkillTheme.PRACTICAL_APPLICATION),
                Map.entry("T5", SkillTheme.COMMUNICATION_AND_COLLABORATION),
                Map.entry("D5", SkillTheme.PROFESSIONAL_JUDGEMENT), Map.entry("T6", SkillTheme.PROFESSIONAL_JUDGEMENT),
                Map.entry("M5", SkillTheme.PROFESSIONAL_JUDGEMENT), Map.entry("M6", SkillTheme.AI_LITERACY),
                Map.entry("D2T", SkillTheme.AI_LITERACY), Map.entry("D2J", SkillTheme.AI_LITERACY),
                Map.entry("D2I", SkillTheme.AI_LITERACY), Map.entry("D2L", SkillTheme.AI_LITERACY));
        for (var course : courses.entrySet()) {
            var guidance = policy.decide(course.getValue());
            Set<SkillTheme> themes = new HashSet<>(guidance.skillPriorities().stream().map(UniversitySkillPriority::theme)
                    .toList());
            for (var horizon : List.of(guidance.next30Days(), guidance.thisTerm(), guidance.next6To12Months())) {
                assertThat(horizon).as(course.getKey()).hasSizeBetween(2, 4);
            }
            for (var action : actions(guidance)) {
                assertThat(action.text()).doesNotContainIgnoringCase("permitted").doesNotContainIgnoringCase("salary")
                        .doesNotContainIgnoringCase(" job");
                String prefix = action.ruleId().substring(0, action.ruleId().indexOf('_'));
                if (gates.containsKey(prefix) && !action.fallback()) {
                    assertThat(themes).as(course.getKey() + " " + action.ruleId()).contains(gates.get(prefix));
                }
            }
        }
        assertThat(actions(policy.decide(courses.get("nursing")))).extracting(UniversityGuidanceAction::ruleId)
                .contains("D5_FIND_THE_STANDARDS_THAT_APPLY", "T6_CHECK_AI_AGAINST_TRUSTED_GUIDANCE",
                        "M5_FOLLOW_PROFESSIONAL_GUIDANCE_ON_AI", "D6_AGREE_A_PRACTICAL_FOCUS");
        assertThat(actions(policy.decide(courses.get("english-literature")))).extracting(UniversityGuidanceAction::ruleId)
                .doesNotContain("T4_SOLVE_FIRST_THEN_CHECK_WITH_AI", "T2_SEEK_FEEDBACK_ON_PRACTICAL_WORK");
    }

    @Test
    void genericAiActionsAreNotUniversal() {
        Map<String, Integer> appearances = new LinkedHashMap<>();
        for (var snapshot : courses.values()) {
            actions(policy.decide(snapshot)).stream().map(UniversityGuidanceAction::ruleId).distinct()
                    .forEach(rule -> appearances.merge(rule, 1, Integer::sum));
        }
        // No action is in every course, nor in six of seven.
        assertThat(appearances.values()).allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(5));
        assertThat(appearances).containsEntry("D1_CHECK_ASSESSMENT_RULES", 5).containsEntry("M6_COMBINE_YOUR_SUBJECT_WITH_AI", 3)
                .containsEntry("M4_COLLECT_EXAMPLES_OF_YOUR_JUDGEMENT", 4);
        // Nursing has no AI-literacy priority, so no AI study workflow and no "combine your subject with AI".
        assertThat(actions(policy.decide(courses.get("nursing")))).extracting(UniversityGuidanceAction::ruleId)
                .noneMatch(rule -> rule.startsWith("D2") || rule.startsWith("M6"));
        // A single integrity-sensitive capstone does not earn "collect examples of your judgement".
        assertThat(actions(policy.decide(courses.get("computer-science")))).extracting(UniversityGuidanceAction::ruleId)
                .doesNotContain("M4_COLLECT_EXAMPLES_OF_YOUR_JUDGEMENT");
        // The AI study workflow's wording follows the evidence category of the first AI opportunity.
        assertThat(policy.decide(courses.get("computer-science")).next30Days()).extracting(UniversityGuidanceAction::ruleId)
                .contains("D2T_USE_AI_TO_EXPLAIN_AND_PRACTISE");
        assertThat(policy.decide(courses.get("law")).next30Days()).extracting(UniversityGuidanceAction::ruleId)
                .contains("D2J_USE_AI_TO_TEST_INTERPRETATIONS");
        assertThat(policy.decide(courses.get("business-management")).next30Days())
                .extracting(UniversityGuidanceAction::ruleId).contains("D2L_USE_AI_TO_REVISE");
    }

    @Test
    void actionOrderFollowsTheSkillEvidence() {
        // Each horizon starts with an action serving the course's strongest available theme.
        assertThat(policy.decide(courses.get("nursing")).next30Days().getFirst().ruleId())
                .isEqualTo("D5_FIND_THE_STANDARDS_THAT_APPLY");
        assertThat(policy.decide(courses.get("mechanical-engineering")).next30Days()).extracting(
                UniversityGuidanceAction::ruleId).startsWith("D3T_CHECK_AI_TECHNICAL_WORKING", "D4_STRENGTHEN_ONE_FOUNDATION");
        assertThat(policy.decide(courses.get("graphic-design")).next6To12Months().getFirst().ruleId())
                .isEqualTo("M1_BUILD_A_PORTFOLIO_OF_YOUR_OWN_WORK");
        assertThat(policy.decide(courses.get("law")).thisTerm().getFirst().ruleId())
                .isEqualTo("T3_FORM_YOUR_OWN_VIEW_FIRST");
        Set<String> firsts = new HashSet<>();
        for (var snapshot : courses.values()) {
            var guidance = policy.decide(snapshot);
            firsts.add(guidance.next30Days().getFirst().ruleId() + "/" + guidance.thisTerm().getFirst().ruleId() + "/"
                    + guidance.next6To12Months().getFirst().ruleId());
        }
        assertThat(firsts).hasSizeGreaterThanOrEqualTo(4);
    }

    @Test
    void fallbacksAreUsedOnlyToReachTwoActionsAndAreMarked() {
        // The seven frozen courses need none.
        for (var snapshot : courses.values()) {
            assertThat(actions(policy.decide(snapshot))).noneMatch(UniversityGuidanceAction::fallback);
        }
        // A one-module snapshot still gets two actions per horizon, with fallbacks marked and only where needed.
        var thin = GuidanceFixtures.subset(courses.get("law"), List.of(2));
        var guidance = policy.decide(thin);
        for (var horizon : List.of(guidance.next30Days(), guidance.thisTerm(), guidance.next6To12Months())) {
            assertThat(horizon).hasSizeBetween(2, 4);
            long evidenced = horizon.stream().filter(action -> !action.fallback()).count();
            if (evidenced >= 2) {
                assertThat(horizon).noneMatch(UniversityGuidanceAction::fallback);
            }
            assertThat(horizon.stream().filter(UniversityGuidanceAction::fallback).count())
                    .isLessThanOrEqualTo(Math.max(0, 2 - evidenced));
        }
        assertThat(actions(guidance)).anyMatch(UniversityGuidanceAction::fallback);
    }

    // ---- determinism ----

    @Test
    void theSameSnapshotAlwaysGivesIdenticalGuidance() {
        for (var snapshot : courses.values()) {
            assertThat(policy.decide(snapshot)).isEqualTo(policy.decide(snapshot));
            assertThat(new UniversityGuidancePolicyV1().decide(snapshot)).isEqualTo(policy.decide(snapshot));
        }
    }

    @Test
    void providerMetadataDoesNotChangeTheGuidance() {
        var snapshot = courses.get("nursing");
        var changed = new UniversityModuleAnalysisSnapshot(snapshot.snapshotId(), snapshot.assessmentId(),
                snapshot.schemaVersion(), snapshot.policyVersion(), snapshot.judgmentSource(), Optional.of("jev-9.9.9"),
                Optional.of("req_completely_different"), OptionalLong.of(1), OptionalLong.empty(), snapshot.courseTitle(),
                snapshot.courseDescription(), List.of("Something else"), snapshot.items(),
                snapshot.createdAt().plusSeconds(3600), snapshot.completedAt().plusSeconds(3600));
        assertThat(policy.decide(changed)).isEqualTo(policy.decide(snapshot));
    }

    @Test
    void moduleAndCourseTitlesAreNeverRules() {
        // Renaming every module and the course keeps every category, rule, module id, theme and action rule.
        for (var snapshot : courses.values()) {
            var renamed = renamed(snapshot);
            var original = policy.decide(snapshot);
            var anonymous = policy.decide(renamed);
            for (Function<UniversityGuidance, List<UniversityGuidanceItem>> section : List.<Function<UniversityGuidance,
                    List<UniversityGuidanceItem>>>of(UniversityGuidance::aiLearningOpportunities,
                    UniversityGuidance::ownCapabilityAreas, UniversityGuidance::technicalFoundations,
                    UniversityGuidance::safeguards)) {
                assertThat(section.apply(anonymous)).extracting(UniversityGuidanceItem::ruleId)
                        .isEqualTo(section.apply(original).stream().map(UniversityGuidanceItem::ruleId).toList());
                assertThat(section.apply(anonymous)).extracting(UniversityGuidanceItem::moduleIds)
                        .isEqualTo(section.apply(original).stream().map(UniversityGuidanceItem::moduleIds).toList());
            }
            assertThat(anonymous.skillPriorities()).extracting(UniversitySkillPriority::theme)
                    .isEqualTo(original.skillPriorities().stream().map(UniversitySkillPriority::theme).toList());
            assertThat(actions(anonymous)).extracting(UniversityGuidanceAction::ruleId)
                    .isEqualTo(actions(original).stream().map(UniversityGuidanceAction::ruleId).toList());
        }
    }

    @Test
    void thePolicyHasNoProviderOrModelDependency() {
        assertThat(Arrays.stream(UniversityGuidancePolicyV1.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))).isEmpty();
        assertThat(UniversityGuidancePolicyV1.class.getDeclaredConstructors()).singleElement()
                .satisfies(constructor -> assertThat(constructor.getParameterCount()).isZero());
    }

    // ---- helpers ----

    private static List<UniversityGuidanceItem> items(UniversityGuidance guidance) {
        return Stream.of(guidance.aiLearningOpportunities(), guidance.ownCapabilityAreas(),
                guidance.technicalFoundations(), guidance.safeguards()).flatMap(List::stream).toList();
    }

    private static List<UniversityGuidanceAction> actions(UniversityGuidance guidance) {
        return Stream.of(guidance.next30Days(), guidance.thisTerm(), guidance.next6To12Months()).flatMap(List::stream)
                .toList();
    }

    private static UniversityModuleAnalysisSnapshot renamed(UniversityModuleAnalysisSnapshot snapshot) {
        List<UniversityModuleAnalysisItem> items = new ArrayList<>();
        for (var item : snapshot.items()) {
            var set = item.judgments();
            String title = "Unnamed module " + item.moduleId();
            var judgments = new UniversityModuleJudgmentSet(set.moduleIndex(), set.moduleKey(), title,
                    set.describedClearly(), set.professionalStandards(), set.aiLearningAssistance(),
                    set.independentDemonstration(), set.humanJudgement(), set.interpersonalWork(),
                    set.practicalApplication(), set.technicalQuantitative());
            var c = item.classification();
            Map<String, Double> inputs = new LinkedHashMap<>(c.inputs());
            items.add(new UniversityModuleAnalysisItem(item.moduleId(), judgments, new UniversityModuleClassification(
                    c.moduleKey(), title, c.category(), c.rule(), inputs, c.dominantValue(), c.competingValue(),
                    c.dominanceGap(), c.flags(), c.closeCallReasons()), item.displayLabel()));
        }
        return new UniversityModuleAnalysisSnapshot(snapshot.snapshotId(), snapshot.assessmentId(),
                snapshot.schemaVersion(), snapshot.policyVersion(), snapshot.judgmentSource(), snapshot.model(),
                snapshot.providerRequestId(), snapshot.inputTokens(), snapshot.outputTokens(), "Unnamed course",
                "No description.", List.of(), items, snapshot.createdAt(), snapshot.completedAt());
    }
}
