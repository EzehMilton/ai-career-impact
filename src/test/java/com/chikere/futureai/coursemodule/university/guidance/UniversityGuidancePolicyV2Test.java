package com.chikere.futureai.coursemodule.university.guidance;

import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisSnapshot;
import com.chikere.futureai.coursemodule.university.guidance.UniversitySkillPriority.SkillTheme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * university-guidance-policy-v2: v1 with action M4 guarded, so a course whose critical evaluation comes only from an
 * independent project no longer fails. No Jev, no OpenAI.
 */
class UniversityGuidancePolicyV2Test {

    private final UniversityGuidancePolicyV1 v1 = new UniversityGuidancePolicyV1();
    private final UniversityGuidancePolicyV2 v2 = new UniversityGuidancePolicyV2();

    /**
     * Computer Science with its Final-Year Individual Project (module 11) but without the group project: critical
     * evaluation is selected only through the project, and no module is judgement-led or has HIGH interpersonal work.
     */
    private static UniversityModuleAnalysisSnapshot computerScienceWithOnlyAnIndependentProject() {
        return GuidanceFixtures.subset(GuidanceFixtures.snapshot("computer-science"), List.of(1, 3, 4, 5, 9, 10, 11));
    }

    @Test
    void theIndependentProjectOnlyComputerScienceCourseCompletesUnderV2ButStillFailsUnderV1() {
        var snapshot = computerScienceWithOnlyAnIndependentProject();
        assertThatThrownBy(() -> v1.decide(snapshot)).isInstanceOf(IllegalArgumentException.class);

        var guidance = v2.decide(snapshot);
        assertThat(guidance.policyVersion()).isEqualTo("university-guidance-policy-v2");
        assertThat(guidance.skillPriorities()).extracting(UniversitySkillPriority::theme)
                .contains(SkillTheme.CRITICAL_EVALUATION).doesNotContain(SkillTheme.COMMUNICATION_AND_COLLABORATION);
        assertThat(guidance.skillPriorities().stream().filter(skill -> skill.theme() == SkillTheme.CRITICAL_EVALUATION))
                .singleElement().satisfies(skill -> assertThat(skill.moduleIds()).containsExactly(11));
    }

    @Test
    void m4IsOmittedAndEveryOtherActionIsBuiltAsBefore() {
        var guidance = v2.decide(computerScienceWithOnlyAnIndependentProject());
        assertThat(actions(guidance)).noneMatch(action -> action.ruleId().startsWith("M4_"));
        assertThat(guidance.next30Days()).extracting(UniversityGuidanceAction::ruleId).containsExactly(
                "D3T_CHECK_AI_TECHNICAL_WORKING", "D4_STRENGTHEN_ONE_FOUNDATION", "D1_CHECK_ASSESSMENT_RULES",
                "D2T_USE_AI_TO_EXPLAIN_AND_PRACTISE");
        assertThat(guidance.thisTerm()).extracting(UniversityGuidanceAction::ruleId).containsExactly(
                "T4_SOLVE_FIRST_THEN_CHECK_WITH_AI", "T1_KEEP_EVIDENCE_OF_YOUR_OWN_WORK");
        assertThat(guidance.next6To12Months()).extracting(UniversityGuidanceAction::ruleId).containsExactly(
                "M3_APPLY_YOUR_FOUNDATIONS_IN_A_PROJECT", "M6_COMBINE_YOUR_SUBJECT_WITH_AI");
        // No module is invented or substituted: every action names only analysed modules, and none is empty-handed.
        assertThat(actions(guidance)).allSatisfy(action -> {
            assertThat(action.text()).doesNotContain(" in .", " in , ");
            assertThat(List.of(1, 3, 4, 5, 9, 10, 11)).containsAll(action.moduleIds());
        });
        assertThat(guidance.next6To12Months()).noneMatch(UniversityGuidanceAction::fallback);
    }

    @Test
    void aCourseWithJudgementLedOrInterpersonalModulesStillGetsM4() {
        for (String course : List.of("law", "english-literature", "nursing", "business-management")) {
            var guidance = v2.decide(GuidanceFixtures.snapshot(course));
            assertThat(guidance.next6To12Months()).as(course)
                    .anySatisfy(action -> {
                        assertThat(action.ruleId()).isEqualTo("M4_COLLECT_EXAMPLES_OF_YOUR_JUDGEMENT");
                        assertThat(action.moduleIds()).isNotEmpty();
                    });
        }
    }

    @Test
    void wheneverV1SucceedsV2GivesTheSameGuidanceUnderItsOwnVersion() {
        var snapshots = new ArrayList<>(GuidanceFixtures.all().values());
        snapshots.add(GuidanceFixtures.subset(GuidanceFixtures.snapshot("law"), List.of(2)));
        snapshots.add(GuidanceFixtures.snapshot("computer-science"));
        for (var snapshot : snapshots) {
            var expected = v1.decide(snapshot);
            var actual = v2.decide(snapshot);
            assertThat(actual.policyVersion()).isEqualTo(UniversityGuidancePolicyV2.POLICY_VERSION);
            assertThat(relabelled(actual, expected.policyVersion())).isEqualTo(expected);
        }
    }

    @Test
    void v1IsFrozen() throws IOException, NoSuchAlgorithmException {
        assertThat(UniversityGuidancePolicyV1.POLICY_VERSION).isEqualTo("university-guidance-policy-v1");
        assertThat(v1.decide(GuidanceFixtures.snapshot("law")).policyVersion())
                .isEqualTo("university-guidance-policy-v1");
        byte[] source = Files.readAllBytes(Path.of("src/main/java/com/chikere/futureai/coursemodule/university/guidance",
                "UniversityGuidancePolicyV1.java"));
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(source)))
                .as("UniversityGuidancePolicyV1.java is frozen; change guidance in a new policy version")
                .isEqualTo(V1_SOURCE_SHA256);
    }

    private static final String V1_SOURCE_SHA256 = "9b17d4f75f6fd8dfd9d31422d6bf45e75ea55a29ba788cddcec85f5879994205";

    private static UniversityGuidance relabelled(UniversityGuidance guidance, String policyVersion) {
        return new UniversityGuidance(policyVersion, guidance.assessmentId(), guidance.moduleAnalysisSnapshotId(),
                guidance.moduleAnalysisPolicyVersion(), guidance.aiLearningOpportunities(), guidance.ownCapabilityAreas(),
                guidance.technicalFoundations(), guidance.safeguards(), guidance.skillPriorities(), guidance.next30Days(),
                guidance.thisTerm(), guidance.next6To12Months());
    }

    private static List<UniversityGuidanceAction> actions(UniversityGuidance guidance) {
        return Stream.of(guidance.next30Days(), guidance.thisTerm(), guidance.next6To12Months()).flatMap(List::stream)
                .toList();
    }
}
