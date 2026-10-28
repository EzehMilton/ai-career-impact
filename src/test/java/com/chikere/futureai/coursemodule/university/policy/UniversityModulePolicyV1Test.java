package com.chikere.futureai.coursemodule.university.policy;

import com.chikere.futureai.coursemodule.university.application.ModuleScoredJudgment;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentResult;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.AI_ASSISTED_LEARNING;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.HUMAN_JUDGEMENT_LED;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.MIXED;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.PRACTICAL_OR_EXPERIENTIAL;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleCategory.TECHNICAL_CORE;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleFlag.INDEPENDENT_DEMONSTRATION_SENSITIVE;
import static com.chikere.futureai.coursemodule.university.policy.UniversityModuleFlag.PROFESSIONAL_STANDARDS_RELEVANT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UniversityModulePolicyV1Test {

    private final UniversityModulePolicyV1 policy = new UniversityModulePolicyV1();

    // ---- rules and precedence ----

    @Test
    void practicalDominanceNeedsHighPracticalAheadOfHumanAndTechnicalByTheMargin() {
        var module = classify(m().practical(3.5).judgement(3.25).technical(1.0));
        assertThat(module.category()).isEqualTo(PRACTICAL_OR_EXPERIENTIAL);
        assertThat(module.rule()).isEqualTo(UniversityModuleRule.UM1_PRACTICAL_DOMINANCE);
        assertThat(module.dominantValue()).isEqualTo(3.5);
        assertThat(module.competingValue()).isEqualTo(3.25);
        assertThat(module.dominanceGap()).isEqualTo(0.25);
        assertThat(classify(m().practical(3.5).judgement(3.26)).rule()).isNotEqualTo(UniversityModuleRule.UM1_PRACTICAL_DOMINANCE);
        // Leading by a lot is not enough unless practical is itself HIGH.
        assertThat(classify(m().practical(2.4)).rule()).isNotEqualTo(UniversityModuleRule.UM1_PRACTICAL_DOMINANCE);
    }

    @Test
    void handsOnWorkWithPeopleIsPracticalWhenPracticalAndInterpersonalAreBothHighAndClose() {
        var placement = classify(m().practical(4.0).interpersonal(3.99).judgement(3.5).technical(1.6));
        assertThat(placement.category()).isEqualTo(PRACTICAL_OR_EXPERIENTIAL);
        assertThat(placement.rule()).isEqualTo(UniversityModuleRule.UM1B_HANDS_ON_WITH_PEOPLE);
        // Close practical and judgement without interpersonal work is not hands-on work with people.
        assertThat(classify(m().practical(3.3).judgement(3.5).interpersonal(1.1)).rule())
                .isNotEqualTo(UniversityModuleRule.UM1B_HANDS_ON_WITH_PEOPLE);
        // Nor when technical depth is as strong as the practical work.
        assertThat(classify(m().practical(3.6).interpersonal(3.6).technical(3.5)).rule())
                .isNotEqualTo(UniversityModuleRule.UM1B_HANDS_ON_WITH_PEOPLE);
    }

    @Test
    void humanDemandIsTheStrongerOfJudgementAndInterpersonalWork() {
        var byJudgement = classify(m().judgement(3.4).practical(1.0).technical(1.0));
        assertThat(byJudgement.category()).isEqualTo(HUMAN_JUDGEMENT_LED);
        assertThat(byJudgement.rule()).isEqualTo(UniversityModuleRule.UM2_HUMAN_JUDGEMENT_DOMINANCE);
        var byInterpersonal = classify(m().judgement(1.0).interpersonal(3.9).practical(1.5));
        assertThat(byInterpersonal.category()).isEqualTo(HUMAN_JUDGEMENT_LED);
        assertThat(byInterpersonal.dominantValue()).isEqualTo(3.9);
    }

    @Test
    void technicalCoreNeedsHighTechnicalDepthAheadOfPracticalAndHumanDemands() {
        var module = classify(m().technical(3.9).practical(2.0).judgement(1.4));
        assertThat(module.category()).isEqualTo(TECHNICAL_CORE);
        assertThat(module.rule()).isEqualTo(UniversityModuleRule.UM3_TECHNICAL_CORE);
        assertThat(classify(m().technical(3.6).practical(3.5)).category()).isNotEqualTo(TECHNICAL_CORE);
    }

    @Test
    void practicalThenHumanThenTechnicalTakePrecedenceOverTheAiRules() {
        // A dominant characteristic decides even when AI assistance and independent demonstration are both HIGH.
        assertThat(classify(m().ai(3.9).independent(3.9).practical(3.9)).category()).isEqualTo(PRACTICAL_OR_EXPERIENTIAL);
        assertThat(classify(m().ai(3.9).independent(3.9).judgement(3.9)).category()).isEqualTo(HUMAN_JUDGEMENT_LED);
        assertThat(classify(m().ai(3.9).independent(3.9).technical(3.9)).category()).isEqualTo(TECHNICAL_CORE);
    }

    @Test
    void withNoDominantCharacteristicHighIndependentDemonstrationDecides() {
        // Practical and technical both HIGH and close: neither dominates.
        var project = classify(m().ai(3.7).independent(3.99).practical(3.65).technical(3.68).judgement(3.48));
        assertThat(project.category()).isEqualTo(AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION);
        assertThat(project.rule()).isEqualTo(UniversityModuleRule.UM4_AI_WITH_INDEPENDENT_DEMONSTRATION);
        assertThat(project.customerLabel()).isEqualTo("AI may help with learning, but your own capability remains central");

        var learning = classify(m().ai(3.7).independent(2.1).judgement(2.2));
        assertThat(learning.category()).isEqualTo(AI_ASSISTED_LEARNING);
        assertThat(learning.rule()).isEqualTo(UniversityModuleRule.UM5_AI_ASSISTED_LEARNING);
    }

    @Test
    void aiLearningAssistanceAloneNeverClassifiesAModule() {
        // Maximum AI assistance does not outweigh a dominant characteristic, and is never a competitor in dominance.
        assertThat(classify(m().ai(4.0).judgement(2.6)).category()).isEqualTo(HUMAN_JUDGEMENT_LED);
        assertThat(classify(m().ai(4.0).technical(2.6)).category()).isEqualTo(TECHNICAL_CORE);
        // Competing HIGH characteristics with no independent demonstration are MIXED, not AI-assisted learning.
        var mixed = classify(m().ai(4.0).independent(2.0).practical(3.0).technical(3.1));
        assertThat(mixed.category()).isEqualTo(MIXED);
        assertThat(mixed.rule()).isEqualTo(UniversityModuleRule.UM6_MIXED);
        // Without HIGH AI assistance neither AI category applies.
        assertThat(classify(m().ai(2.4).independent(3.9)).category()).isEqualTo(MIXED);
    }

    // ---- thresholds ----

    @Test
    void highStartsAtTwoPointFiveIncludingFloatingPointAtTheThreshold() {
        assertThat(classify(m().judgement(2.5)).category()).isEqualTo(HUMAN_JUDGEMENT_LED);
        assertThat(classify(m().judgement(2.5 - 1.0e-12)).category()).isEqualTo(HUMAN_JUDGEMENT_LED);
        assertThat(classify(m().judgement(2.49)).category()).isNotEqualTo(HUMAN_JUDGEMENT_LED);
        assertThat(UniversityModulePolicyV1.MODERATE_FROM).isEqualTo(1.5);
        assertThat(UniversityModulePolicyV1.DOMINANCE_MARGIN).isEqualTo(0.25);
    }

    @Test
    void aGapOfExactlyTheMarginDominatesAndAHairLessDoesNot() {
        assertThat(classify(m().technical(3.0).practical(2.75)).category()).isEqualTo(TECHNICAL_CORE);
        assertThat(classify(m().technical(3.0).practical(2.76)).category()).isNotEqualTo(TECHNICAL_CORE);
    }

    // ---- flags ----

    @Test
    void theIntegrityFlagNeedsHighAiAssistanceAndVeryHighIndependentDemonstration() {
        assertThat(classify(m().ai(2.5).independent(3.5)).flags()).contains(INDEPENDENT_DEMONSTRATION_SENSITIVE);
        assertThat(classify(m().ai(2.5).independent(3.5 - 1.0e-12)).flags()).contains(INDEPENDENT_DEMONSTRATION_SENSITIVE);
        assertThat(classify(m().ai(2.49).independent(3.9)).flags()).doesNotContain(INDEPENDENT_DEMONSTRATION_SENSITIVE);
        assertThat(classify(m().ai(3.9).independent(3.49)).flags()).doesNotContain(INDEPENDENT_DEMONSTRATION_SENSITIVE);
        // HIGH independent demonstration alone is not enough for the flag, though it still decides UM4.
        var assisted = classify(m().ai(3.9).independent(2.6));
        assertThat(assisted.category()).isEqualTo(AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION);
        assertThat(assisted.flags()).doesNotContain(INDEPENDENT_DEMONSTRATION_SENSITIVE);
        // It is a flag, never the category: a practical module keeps its category.
        var practical = classify(m().ai(3.0).independent(3.9).practical(3.9));
        assertThat(practical.category()).isEqualTo(PRACTICAL_OR_EXPERIENTIAL);
        assertThat(practical.flags()).contains(INDEPENDENT_DEMONSTRATION_SENSITIVE);
    }

    @Test
    void aStrongProfessionalStandardsSignalIsEnoughOnItsOwn() {
        assertThat(classify(m().professional(0.75)).flags()).contains(PROFESSIONAL_STANDARDS_RELEVANT);
        assertThat(classify(m().professional(0.75 - 1.0e-12)).flags()).contains(PROFESSIONAL_STANDARDS_RELEVANT);
        assertThat(classify(m().professional(0.74)).flags()).doesNotContain(PROFESSIONAL_STANDARDS_RELEVANT);
        // It never changes the category.
        assertThat(classify(m().professional(0.99).judgement(3.0)).category()).isEqualTo(HUMAN_JUDGEMENT_LED);
    }

    @Test
    void aModerateSignalNeedsBothHighPracticalAndHighInterpersonalWork() {
        var practice = m().professional(0.54).practical(3.9).interpersonal(3.8).judgement(3.5);
        assertThat(classify(practice).flags()).contains(PROFESSIONAL_STANDARDS_RELEVANT);
        assertThat(classify(practice.professional(0.50)).flags()).contains(PROFESSIONAL_STANDARDS_RELEVANT);
        assertThat(classify(practice.professional(0.50 - 1.0e-12)).flags()).contains(PROFESSIONAL_STANDARDS_RELEVANT);
        assertThat(classify(practice.professional(0.49)).flags()).doesNotContain(PROFESSIONAL_STANDARDS_RELEVANT);
        // Without practical support, as for a moderate signal on a science module.
        assertThat(classify(practice.practical(2.49)).flags()).doesNotContain(PROFESSIONAL_STANDARDS_RELEVANT);
        assertThat(classify(m().professional(0.63).technical(3.3)).flags()).doesNotContain(PROFESSIONAL_STANDARDS_RELEVANT);
        // Without interpersonal support.
        assertThat(classify(practice.interpersonal(2.49)).flags()).doesNotContain(PROFESSIONAL_STANDARDS_RELEVANT);
        assertThat(classify(practice.practical(2.5).interpersonal(2.5)).flags()).contains(PROFESSIONAL_STANDARDS_RELEVANT);
    }

    // ---- close calls ----

    @Test
    void closeCallsAreRecordedNearTheThresholdAndTheMarginButNotOtherwise() {
        assertThat(classify(m().judgement(2.6)).closeCallReasons()).anySatisfy(reason ->
                assertThat(reason).startsWith("human_demand 2.60 near HIGH"));
        // Both values near HIGH and a 0.22 lead near the margin: three reasons the outcome could flip.
        assertThat(classify(m().judgement(2.4).technical(2.62)).closeCallReasons()).containsExactly(
                "human_demand 2.40 near HIGH 2.5", "technical_quantitative 2.62 near HIGH 2.5",
                "technical_quantitative lead 0.22 near margin 0.25");
        assertThat(classify(m().technical(3.6).practical(3.33)).closeCallReasons()).singleElement()
                .satisfies(reason -> assertThat(reason).startsWith("technical_quantitative lead 0.27 near margin"));
        var clear = classify(m().technical(3.9).practical(1.0).judgement(1.0));
        assertThat(clear.closeCall()).isFalse();
        assertThat(clear.closeCallReasons()).isEmpty();
    }

    // ---- described_clearly and determinism ----

    @Test
    void describedClearlyNeverAffectsTheClassification() {
        for (var module : List.of(m().judgement(3.0), m().technical(3.4).practical(2.0), m().ai(3.7).independent(3.9))) {
            var unclear = classify(module.clarity(0.01));
            var clear = classify(module.clarity(0.99));
            assertThat(unclear.category()).isEqualTo(clear.category());
            assertThat(unclear.rule()).isEqualTo(clear.rule());
            assertThat(unclear.flags()).isEqualTo(clear.flags());
            assertThat(unclear.closeCallReasons()).isEqualTo(clear.closeCallReasons());
            assertThat(unclear.inputs().get("described_clearly")).isEqualTo(0.01);
        }
    }

    @Test
    void theSameInputsAlwaysGiveTheSameClassification() {
        var input = m().ai(3.6).independent(3.2).judgement(2.9).interpersonal(2.9).practical(2.9).technical(2.9);
        assertThat(classify(input)).isEqualTo(classify(input));
        // Equal competing values never dominate one another: the outcome is a fixed later rule, not an arbitrary pick.
        assertThat(classify(input).rule()).isEqualTo(UniversityModuleRule.UM4_AI_WITH_INDEPENDENT_DEMONSTRATION);
    }

    @Test
    void onlyUniversityModuleV1JudgmentsAreAccepted() {
        var set = module(m());
        var wrong = new UniversityModuleJudgmentResult("university-module-v2", UniversityModuleJudgmentSource.JEV,
                Optional.empty(), Optional.empty(), OptionalLong.empty(), OptionalLong.empty(), List.of(set));
        assertThatThrownBy(() -> policy.classify(wrong)).hasMessageContaining("university-module-v1");
    }

    // ---- the 56 frozen live modules ----

    @Test
    void allFiftySixFrozenModulesClassifyAsReviewed() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("business-management", "HUMAN AI_LEARNING AI_INDEPENDENT HUMAN HUMAN TECHNICAL HUMAN HUMAN");
        expected.put("computer-science",
                "TECHNICAL TECHNICAL TECHNICAL TECHNICAL PRACTICAL TECHNICAL TECHNICAL AI_INDEPENDENT");
        expected.put("english-literature", "HUMAN HUMAN HUMAN HUMAN HUMAN HUMAN AI_INDEPENDENT HUMAN");
        expected.put("graphic-design",
                "PRACTICAL PRACTICAL PRACTICAL PRACTICAL PRACTICAL HUMAN PRACTICAL AI_INDEPENDENT");
        expected.put("law", "AI_INDEPENDENT HUMAN HUMAN HUMAN HUMAN HUMAN PRACTICAL HUMAN");
        expected.put("mechanical-engineering",
                "TECHNICAL TECHNICAL TECHNICAL TECHNICAL TECHNICAL TECHNICAL PRACTICAL TECHNICAL");
        expected.put("nursing", "PRACTICAL TECHNICAL TECHNICAL HUMAN HUMAN HUMAN HUMAN PRACTICAL");

        int modules = 0;
        for (var course : expected.entrySet()) {
            var classifications = policy.classify(ModuleProbeFixtures.load(course.getKey()));
            assertThat(String.join(" ", classifications.stream().map(value -> shortName(value.category())).toList()))
                    .as(course.getKey()).isEqualTo(course.getValue());
            assertThat(classifications).extracting(UniversityModuleClassification::moduleKey)
                    .containsExactly("m1", "m2", "m3", "m4", "m5", "m6", "m7", "m8");
            modules += classifications.size();
        }
        assertThat(modules).isEqualTo(56);
    }

    @Test
    void theFrozenModulesCarryTheExpectedFlagsAndCloseCalls() {
        List<UniversityModuleClassification> all = new ArrayList<>();
        ModuleProbeFixtures.all().forEach((course, result) -> all.addAll(policy.classify(result)));
        assertThat(all.stream().filter(value -> value.hasFlag(PROFESSIONAL_STANDARDS_RELEVANT))
                .map(UniversityModuleClassification::moduleTitle)).containsExactlyInAnyOrder(
                "Clinical Skills and Simulation", "Pharmacology and Medicines Management",
                "Communication and Compassionate Care", "Evidence-Based Practice",
                "Professional, Legal and Ethical Practice", "Leadership and Management of Care", "Clinical Placements",
                "Law Clinic");
        assertThat(all.stream().filter(value -> value.hasFlag(INDEPENDENT_DEMONSTRATION_SENSITIVE))
                .map(UniversityModuleClassification::moduleTitle)).containsExactlyInAnyOrder(
                "Dissertation or Consultancy Project", "Final-Year Individual Project", "Creative Writing",
                "Dissertation", "Typography", "Visual Identity and Branding", "Illustration",
                "Digital and Interaction Design", "Motion Graphics", "Live Briefs", "Final Major Project", "Law Clinic",
                "Dissertation", "Engineering Mathematics", "Individual Project", "Clinical Skills and Simulation",
                "Clinical Placements");
        assertThat(all.stream().filter(UniversityModuleClassification::closeCall)
                .map(UniversityModuleClassification::moduleTitle)).containsExactlyInAnyOrder(
                "Financial and Management Accounting", "Business Analytics", "Artificial Intelligence",
                "Creative Writing", "Live Briefs", "Mechanical Design and CAD", "Individual Project",
                "Evidence-Based Practice");
        // The module named "Artificial Intelligence" is classified by its judgments, not its name.
        assertThat(all.stream().filter(value -> value.moduleTitle().equals("Artificial Intelligence")).findFirst()
                .orElseThrow().category()).isEqualTo(TECHNICAL_CORE);
    }

    // ---- helpers ----

    private UniversityModuleClassification classify(Builder builder) {
        return policy.classify(module(builder));
    }

    private static String shortName(UniversityModuleCategory category) {
        return switch (category) {
            case AI_ASSISTED_LEARNING -> "AI_LEARNING";
            case AI_ASSISTED_WITH_STRONG_INDEPENDENT_DEMONSTRATION -> "AI_INDEPENDENT";
            case HUMAN_JUDGEMENT_LED -> "HUMAN";
            case PRACTICAL_OR_EXPERIENTIAL -> "PRACTICAL";
            case TECHNICAL_CORE -> "TECHNICAL";
            case MIXED -> "MIXED";
        };
    }

    private static Builder m() {
        return new Builder();
    }

    private static UniversityModuleJudgmentSet module(Builder b) {
        return new UniversityModuleJudgmentSet(1, "m1", "Test module", b.clarity, b.professional, score(b.ai),
                score(b.independent), score(b.judgement), score(b.interpersonal), score(b.practical),
                score(b.technical));
    }

    private static ModuleScoredJudgment score(double value) {
        return new ModuleScoredJudgment(value, Map.of(), OptionalDouble.of(0.7), OptionalInt.empty(), Optional.empty());
    }

    /** Defaults: everything LOW except AI assistance, as in the live probe. */
    private static final class Builder {
        double clarity = 0.8, professional = 0.1, ai = 3.6, independent = 2.0, judgement = 1.0, interpersonal = 1.0,
                practical = 1.0, technical = 1.0;

        Builder clarity(double value) { Builder copy = copy(); copy.clarity = value; return copy; }
        Builder professional(double value) { Builder copy = copy(); copy.professional = value; return copy; }
        Builder ai(double value) { Builder copy = copy(); copy.ai = value; return copy; }
        Builder independent(double value) { Builder copy = copy(); copy.independent = value; return copy; }
        Builder judgement(double value) { Builder copy = copy(); copy.judgement = value; return copy; }
        Builder interpersonal(double value) { Builder copy = copy(); copy.interpersonal = value; return copy; }
        Builder practical(double value) { Builder copy = copy(); copy.practical = value; return copy; }
        Builder technical(double value) { Builder copy = copy(); copy.technical = value; return copy; }

        private Builder copy() {
            Builder copy = new Builder();
            copy.clarity = clarity; copy.professional = professional; copy.ai = ai; copy.independent = independent;
            copy.judgement = judgement; copy.interpersonal = interpersonal; copy.practical = practical;
            copy.technical = technical;
            return copy;
        }
    }
}
