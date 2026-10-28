package com.chikere.futureai.guidance.alevel;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.decision.alevel.ALevelProfileDecisionResult;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelV2JudgmentProvider;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** a-level-guidance-policy-v1: deterministic, neutral, personalised by context but never changing a score. */
class ALevelGuidancePolicyV1Test {

    private final ALevelGuidancePolicyV1 policy = new ALevelGuidancePolicyV1();

    static ALevelProfileDecisionResult profile(String fixture) {
        return new ALevelDecisionPolicyV2().decide(new LocalFixtureALevelV2JudgmentProvider(fixture)
                .judge(new ALevelJudgmentInput(List.of("Subject A", "Subject B"))));
    }

    static ALevelAssessmentState bare() {
        return new ALevelAssessmentState(List.of("History", "English Literature", "Politics"), Map.of(), List.of(),
                List.of(), List.of(), Optional.empty(), Optional.empty());
    }

    static ALevelAssessmentState rich() {
        return new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of("Mathematics", "A", "Economics", "B"), List.of("Debating", "Data"),
                List.of("Economics", "Geography"), List.of("Finance"), Optional.of("United Kingdom"),
                Optional.of("Keeping options open."));
    }

    @Test
    void everyFixtureProducesCompleteGuidanceWithProvenance() {
        for (String fixture : LocalFixtureALevelV2JudgmentProvider.supportedFixtureIds()) {
            var guidance = policy.decide(profile(fixture), rich());

            assertThat(guidance.policyVersion()).isEqualTo("a-level-guidance-policy-v1");
            assertThat(guidance.combinationShape()).as(fixture).hasSize(3);
            assertThat(guidance.strengths()).as(fixture).hasSizeLessThanOrEqualTo(4);
            assertThat(guidance.areasToStrengthen()).as(fixture).hasSizeLessThanOrEqualTo(3);
            assertThat(guidance.skillPriorities()).as(fixture).hasSizeBetween(3, 5);
            assertThat(guidance.actionPlan().now()).as(fixture).hasSizeGreaterThanOrEqualTo(2);
            assertThat(guidance.actionPlan().sixthForm()).as(fixture).hasSizeGreaterThanOrEqualTo(2);
            assertThat(guidance.actionPlan().applications()).as(fixture).hasSizeGreaterThanOrEqualTo(2);
            assertThat(guidance.aiStudy().caution()).isEqualTo("AI assistance does not replace developing your own "
                    + "understanding, and it does not mean AI use is permitted in assessed work.");
            assertThat(guidance.aiStudy().uses()).hasSizeBetween(3, 5);
            all(guidance).forEach(item -> assertThat(item.provenance()).as(item.id()).isNotEmpty());
            assertThat(guidance.safeguards()).extracting(ALevelGuidance.Item::id).contains("SG1", "SG2", "SG4");
        }
    }

    @Test
    void neverUsesDeficitComparativeOrPromiseLanguage() {
        for (String fixture : LocalFixtureALevelV2JudgmentProvider.supportedFixtureIds()) {
            for (var state : List.of(bare(), rich())) {
                var guidance = policy.decide(profile(fixture), state);
                String text = text(guidance).toLowerCase();
                assertThat(text).as(fixture).doesNotContain("weak", "poor", "bad ", "lacking", "you lack",
                        "worse than", "other students", "alternatives", "guarantee", "will get into",
                        "will be accepted", "ai-proof", "ai cannot", "salary", "employab", "you are allowed to use ai",
                        "requires a-level", "aaa", "aab", "future optionality", "%");
            }
        }
    }

    @Test
    void aLighterSideOnlyBecomesAnAreaWhenItIsClearlyLighterAndIsWordedAsAnInvitation() {
        // English, history and politics: quantitative and technical work are lighter in this development fixture.
        var humanities = policy.decide(profile("english_history_politics"), bare());
        assertThat(humanities.areasToStrengthen()).extracting(ALevelGuidance.Item::provenance)
                .allSatisfy(provenance -> assertThat(provenance).containsAnyOf("quantitative_reasoning",
                        "technical_digital_foundation"));
        assertThat(humanities.areasToStrengthen()).allSatisfy(item -> assertThat(item.text())
                .containsAnyOf("Consider deliberately", "You may want to build", "Your subjects place less emphasis"));
        // A balanced mix has nothing clearly lighter, so no area is manufactured.
        assertThat(policy.decide(profile("mixed_balanced"), bare()).areasToStrengthen()).isEmpty();
        // Strengths follow the strongest stored capabilities, with their provenance.
        assertThat(humanities.strengths().getFirst().provenance()).containsAnyOf("written_verbal_communication",
                "contextual_social_understanding");
    }

    @Test
    void personalContextPersonalisesGuidanceButNeverTheDecision() {
        var profile = profile("maths_economics_geography");

        var withContext = policy.decide(profile, rich());
        var without = policy.decide(profile, bare());

        assertThat(withContext.options().checks()).extracting(ALevelGuidance.Item::title)
                .contains("Check entry requirements for Economics", "Check entry requirements for Geography");
        assertThat(withContext.options().checks()).anySatisfy(check -> assertThat(check.text())
                .contains("Check the published entry requirements of individual courses",
                        "particularly any required subjects or grades"));
        assertThat(withContext.options().gradesNote()).hasValueSatisfying(note -> assertThat(note)
                .contains("Each course sets its own subject and grade requirements").doesNotContain("good", "high"));
        assertThat(withContext.actionPlan().applications()).extracting(ALevelGuidance.Item::id).contains("APP_GRADES");
        assertThat(withContext.safeguards()).extracting(ALevelGuidance.Item::id).contains("SG5");
        assertThat(withContext.skillPriorities()).extracting(ALevelGuidance.Item::id).contains("SKILL_INTEREST");

        assertThat(without.options().checks()).isEmpty();
        assertThat(without.options().gradesNote()).isEmpty();
        assertThat(without.safeguards()).extracting(ALevelGuidance.Item::id).doesNotContain("SG5");
        // The decided profile is identical whichever context accompanies it.
        assertThat(withContext.combinationShape()).isEqualTo(without.combinationShape());
        assertThat(withContext.strengths()).isEqualTo(without.strengths());
        assertThat(withContext.areasToStrengthen()).isEqualTo(without.areasToStrengthen());
    }

    @Test
    void theOptionsSectionSaysWhatItCannotSayAndNeverStatesARequirement() {
        var options = policy.decide(profile("english_history_politics"), rich()).options();

        assertThat(options.cannotSay()).contains("It cannot tell you whether a particular course will accept your "
                + "subjects.", "It does not check entry requirements, required subjects or grades for any course.");
        assertThat(String.join(" ", options.canSay())).doesNotContainIgnoringCase("require");
    }

    @Test
    void careerInterestsAreHandledCautiously() {
        var checks = policy.decide(profile("maths_economics_geography"), rich()).options().checks();

        assertThat(checks).filteredOn(check -> check.id().equals("CHECK_CAREERS")).singleElement()
                .satisfies(check -> assertThat(check.text()).contains("describes your subjects, not a career"));
    }

    @Test
    void aiStudyDepthFollowsLearningAlongsideAiWithoutGrantingPermission() {
        var guidance = policy.decide(profile("mathematics_physics_computer_science"), bare());

        assertThat(guidance.aiStudy().uses()).extracting(ALevelGuidance.Item::id).contains("AI_EXPLAIN",
                "AI_CALCULATIONS");
        assertThat(guidance.aiStudy().uses()).allSatisfy(use -> assertThat(use.provenance())
                .contains("LEARNING_ALONGSIDE_AI"));
        assertThat(text(guidance)).doesNotContain("submit AI", "AI is always right");
    }

    @Test
    void rejectsAProfileThatNeedsMoreInformation() {
        // A historical v1 decision cannot even be passed: the policy only accepts a v2 subject profile.
        var insufficient = new ALevelDecisionPolicyV2().decide(
                com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2Test.judgments(0.40,
                        com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2Test.scores(2.0)));
        assertThatThrownBy(() -> policy.decide(insufficient, bare())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void thePolicyNamesNoSubjectAndCallsNoProvider() throws Exception {
        String source = Files.readString(Path.of("src", "main", "java", "com", "chikere", "futureai", "guidance",
                "alevel", "ALevelGuidancePolicyV1.java"));

        assertThat(source).doesNotContain("\"Mathematics", "\"Physics", "\"Chemistry", "\"Biology", "\"Economics",
                "\"History", "\"English", "\"Politics", "\"Psychology", "\"Sociology", "\"Geography", "\"Art",
                "TypeSafe", "OpenAi", "ChatClient", "EvidenceProvider");
    }

    private static Stream<ALevelGuidance.Item> all(ALevelGuidance guidance) {
        return Stream.of(guidance.combinationShape(), guidance.strengths(), guidance.areasToStrengthen(),
                guidance.options().checks(), guidance.aiStudy().uses(), guidance.skillPriorities(),
                guidance.actionPlan().now(), guidance.actionPlan().sixthForm(), guidance.actionPlan().applications(),
                guidance.safeguards()).flatMap(List::stream);
    }

    private static String text(ALevelGuidance guidance) {
        return String.join(" ", Stream.concat(all(guidance).flatMap(item -> Stream.of(item.title(), item.text())),
                Stream.concat(Stream.concat(guidance.options().canSay().stream(),
                        guidance.options().cannotSay().stream()), Stream.concat(guidance.options().gradesNote().stream(),
                        Stream.of(guidance.aiStudy().caution())))).toList());
    }
}
