package com.chikere.futureai.responsibility.professional.guidance;

import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.HumanValueReason;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.OpportunityType;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeguardImportance;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeguardType;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SkillFamily;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.AiAssistanceOpportunity;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.HumanValueArea;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.PlanAction;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.ResponsibleUseSafeguard;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.SkillPriority;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.chikere.futureai.responsibility.professional.guidance.AnalysisSnapshots.handBuilt;
import static com.chikere.futureai.responsibility.professional.guidance.AnalysisSnapshots.row;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Rule-by-rule behaviour of the guidance policy on HAND-BUILT judgments (not live data). */
class ProfessionalGuidancePolicyV1Test {

    private final ProfessionalGuidancePolicyV1 policy = new ProfessionalGuidancePolicyV1();

    // ---- Opportunities ----

    @Test
    void aGoodOpportunityOutranksPartialAssistanceAndHandsOnWorkIsNeverAnOpportunity() {
        var guidance = policy.decide(handBuilt(0.5,
                row(3.4, 2.8, 1.0, 0.1, 2.0),    // parts: context high
                row(3.4, 1.5, 1.0, 0.1, 2.0),    // good opportunity
                row(3.9, 1.0, 1.0, 3.9, 2.0)));  // hands-on despite high assistance
        assertThat(keys(guidance.opportunities())).containsExactly("r2", "r1");
        assertThat(guidance.opportunities().getFirst().type()).isEqualTo(OpportunityType.PREPARATION_AND_INFORMATION_SUPPORT);
        assertThat(guidance.opportunities().get(1).type()).isEqualTo(OpportunityType.SUPPORT_FOR_PARTS_UNDER_YOUR_JUDGEMENT);
        assertThat(guidance.opportunities()).noneMatch(o -> o.category() == ResponsibilityCategory.MAINLY_HANDS_ON_OR_IN_PERSON);
    }

    @Test
    void strongerHumanDemandAndPhysicalPresenceLowerPriorityAccordingToTheFormula() {
        // All three are "parts" with no close call; only the penalties differ.
        var guidance = policy.decide(handBuilt(0.5,
                row(3.4, 3.3, 1.0, 0.1, 2.0),
                row(3.4, 2.8, 1.0, 0.1, 2.0),
                row(3.4, 2.8, 1.0, 2.3, 2.0)));
        assertThat(keys(guidance.opportunities())).containsExactly("r2", "r3", "r1");
        var first = guidance.opportunities().getFirst();
        assertThat(first.scoreComponents()).containsEntry("assistance", 3.4).containsEntry("categoryBonus", 0.5)
                .containsEntry("closeCallPenalty", 0.0);
        assertThat(first.scoreComponents().get("contextPenalty")).isCloseTo(-0.24, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(first.score()).isCloseTo(3.4 + 0.5 - 0.24, org.assertj.core.api.Assertions.within(1e-9));
        assertThat(guidance.opportunities().get(1).scoreComponents().get("physicalPenalty"))
                .isCloseTo(-0.09, org.assertj.core.api.Assertions.within(1e-9));
    }

    @Test
    void oversightDoesNotExcludeAssistanceButAddsReviewConstraints() {
        var guidance = policy.decide(handBuilt(3.9, row(3.4, 1.5, 1.0, 0.1, 3.9), row(3.4, 1.5, 1.0, 0.1, 2.0)));
        assertThat(keys(guidance.opportunities())).containsExactly("r2", "r1");
        var overseen = guidance.opportunities().get(1);
        assertThat(overseen.needsCloseHumanOversight()).isTrue();
        assertThat(overseen.scoreComponents()).containsEntry("governanceOversightPenalty", -0.4);
        assertThat(overseen.constraints()).contains(GuidanceVocabulary.SafeUseConstraint.REVIEW_EVERY_OUTPUT,
                GuidanceVocabulary.SafeUseConstraint.APPROVED_TOOLS_ONLY);
    }

    // ---- Governance ranking (4E.1) ----

    @Test
    void o1CarriesTheStrongestGovernancePenaltyO2ASmallerOneAndAllThreeRemainEligible() {
        var guidance = policy.decide(handBuilt(3.0,
                row(3.4, 1.5, 1.0, 0.1, 3.9),    // O1: very high accountability
                row(3.4, 1.5, 1.0, 0.1, 3.0),    // O2: regulated context with high accountability
                row(3.4, 1.5, 1.0, 0.1, 2.0)));  // no oversight
        assertThat(keys(guidance.opportunities())).containsExactly("r3", "r2", "r1");
        var none = guidance.opportunities().get(0);
        var o2 = guidance.opportunities().get(1);
        var o1 = guidance.opportunities().get(2);
        assertThat(none.scoreComponents()).containsEntry("governanceOversightPenalty", 0.0)
                .containsEntry("governanceSafetyCriticalPenalty", 0.0);
        assertThat(o2.scoreComponents()).containsEntry("governanceOversightPenalty", -ProfessionalGuidancePolicyV1.O2_PENALTY);
        assertThat(o1.scoreComponents()).containsEntry("governanceOversightPenalty", -ProfessionalGuidancePolicyV1.O1_PENALTY);
        assertThat(o2.scoreComponents().get("governanceAccountabilityPenalty")).isCloseTo(-0.15, within(1e-9));
        assertThat(o1.scoreComponents().get("governanceAccountabilityPenalty")).isCloseTo(-0.42, within(1e-9));
        assertThat(o1.score()).isLessThan(o2.score());
        assertThat(o2.score()).isLessThan(none.score());
        // Still listed, as a primary opportunity, with the rule IDs explaining the lower rank and the review safeguards.
        assertThat(o1.ruleId()).isEqualTo("OP1_PRIMARY_ASSISTANCE");
        assertThat(o1.rationale()).contains(ProfessionalGuidancePolicyV1.GOVERNANCE_O1,
                ProfessionalGuidancePolicyV1.GOVERNANCE_REGULATED_ACCOUNTABILITY);
        assertThat(o2.rationale()).contains(ProfessionalGuidancePolicyV1.GOVERNANCE_O2);
        assertThat(none.rationale()).doesNotContain("governance");
        assertThat(o1.constraints()).contains(GuidanceVocabulary.SafeUseConstraint.REVIEW_EVERY_OUTPUT,
                GuidanceVocabulary.SafeUseConstraint.APPROVED_TOOLS_ONLY);
        assertThat(guidance.safeguards()).extracting(ResponsibleUseSafeguard::type).contains(SafeguardType.ESCALATE_UNCERTAINTY);
    }

    @Test
    void comparableWorkWithoutOversightOutranksO1EvenWithSlightlyLowerAssistance() {
        for (double regulated : new double[] {0.5, 3.0}) {
            var guidance = policy.decide(handBuilt(regulated, row(3.7, 1.5, 1.0, 0.1, 3.9), row(3.4, 1.5, 1.0, 0.1, 2.0)));
            assertThat(keys(guidance.opportunities())).as("regulated " + regulated).containsExactly("r2", "r1");
        }
        // O1 is not ineligible: clearly stronger O1 work still outranks weak work without oversight.
        var stronger = policy.decide(handBuilt(0.5, row(3.9, 1.5, 1.0, 0.1, 3.9), row(2.6, 3.4, 1.0, 0.1, 2.0)));
        assertThat(keys(stronger.opportunities())).containsExactly("r1", "r2");
    }

    @Test
    void o1WorkWithSubstantialPhysicalPresenceIsTreatedAsSafetyCritical() {
        var guidance = policy.decide(handBuilt(3.0, row(3.2, 1.5, 1.0, 2.2, 3.9), row(3.2, 1.5, 1.0, 1.0, 3.9)));
        assertThat(keys(guidance.opportunities())).containsExactly("r2", "r1");
        var critical = guidance.opportunities().get(1);
        assertThat(critical.scoreComponents()).containsEntry("governanceSafetyCriticalPenalty",
                -ProfessionalGuidancePolicyV1.SAFETY_CRITICAL_PENALTY);
        assertThat(critical.rationale()).contains(ProfessionalGuidancePolicyV1.GOVERNANCE_SAFETY_CRITICAL);
        assertThat(guidance.opportunities().get(0).scoreComponents()).containsEntry("governanceSafetyCriticalPenalty", 0.0);
        // The same physical presence without O1 is not safety-critical governance.
        var withoutO1 = policy.decide(handBuilt(0.5, row(3.2, 1.5, 1.0, 2.2, 2.0)));
        assertThat(withoutO1.opportunities().getFirst().scoreComponents()).containsEntry("governanceSafetyCriticalPenalty", 0.0);
    }

    @Test
    void thePilotPrefersNoOversightThenO2ThenO1AndUsesO1OnlyWhenNothingSaferExists() {
        // O1 ranks first on its strength, but the O2 opportunity is the safer pilot.
        var mixed = policy.decide(handBuilt(3.0, row(3.9, 1.5, 1.0, 0.1, 3.9), row(3.0, 1.5, 1.0, 0.1, 3.0)));
        assertThat(keys(mixed.opportunities())).containsExactly("r1", "r2");
        assertThat(pilot(mixed)).isEqualTo("r2");

        var onlyO1 = policy.decide(handBuilt(3.0, row(3.3, 1.5, 1.0, 0.1, 3.9), row(3.4, 1.5, 1.0, 0.1, 3.9)));
        assertThat(keys(onlyO1.opportunities())).containsExactly("r2", "r1");
        assertThat(pilot(onlyO1)).isEqualTo("r2");
    }

    @Test
    void responsibilityTextAndProfessionNeverChangeTheRankingOrPilot() throws Exception {
        double[][] rows = {row(3.4, 1.5, 1.0, 0.1, 3.9), row(3.4, 1.5, 1.0, 0.1, 3.0), row(3.4, 1.5, 1.0, 0.1, 2.0)};
        var neutral = policy.decide(handBuilt("Role A", List.of(), List.of("Task one", "Task two", "Task three"), 3.0, rows));
        var sensitive = policy.decide(handBuilt("Registered Nurse", List.of(), List.of("Maintain clinical records",
                "Handle confidential customer information", "Follow safety and safeguarding procedures"), 3.0, rows));
        assertThat(keys(sensitive.opportunities())).isEqualTo(keys(neutral.opportunities()));
        assertThat(sensitive.opportunities()).extracting(AiAssistanceOpportunity::score)
                .isEqualTo(neutral.opportunities().stream().map(AiAssistanceOpportunity::score).toList());
        assertThat(pilot(sensitive)).isEqualTo(pilot(neutral));
        // Deterministic: the same analysis always yields the same guidance.
        var analysis = AnalysisSnapshots.live("electrician-8");
        assertThat(policy.decide(analysis)).isEqualTo(policy.decide(analysis));
        // No string inspection of responsibility text or profession exists in the policy.
        String source = Files.readString(Path.of(
                "src/main/java/com/chikere/futureai/responsibility/professional/guidance/ProfessionalGuidancePolicyV1.java"));
        assertThat(source).doesNotContainPattern("responsibilityText\\(\\)\\s*\\.")
                .doesNotContain(".matches(", "Pattern", "regionMatches", "profession()", ".input()");
    }

    @Test
    void aCloseCallIsPenalisedAndFlaggedAndTiesAreResolvedByResponsibilityOrder() {
        var guidance = policy.decide(handBuilt(0.5,
                row(3.4, 2.45, 1.0, 0.1, 2.0),  // good, but context within the close-call band
                row(3.4, 1.5, 1.0, 0.1, 2.0),
                row(3.4, 1.5, 1.0, 0.1, 2.0)));
        assertThat(keys(guidance.opportunities())).containsExactly("r2", "r3", "r1");
        assertThat(guidance.opportunities().get(2).dependsOnCloseCall()).isTrue();
        assertThat(guidance.opportunities().get(2).scoreComponents()).containsEntry("closeCallPenalty", -0.25);
        assertThat(policy.decide(handBuilt(0.5, row(3.4, 1.5, 1.0, 0.1, 2.0), row(3.4, 1.5, 1.0, 0.1, 2.0)))
                .opportunities()).extracting(AiAssistanceOpportunity::responsibilityKey).containsExactly("r1", "r2");
    }

    @Test
    void atMostThreeOpportunitiesAndAHumanLedOneOnlyAsALastSupportingUseWhenFewerThanTwoExist() {
        double[] good = row(3.4, 1.5, 1.0, 0.1, 2.0);
        assertThat(policy.decide(handBuilt(0.5, good, good, good, good, good)).opportunities()).hasSize(3);

        var few = policy.decide(handBuilt(0.5, good, row(2.8, 3.2, 3.6, 0.1, 2.0), row(2.6, 3.0, 3.8, 0.1, 2.0)));
        assertThat(keys(few.opportunities())).containsExactly("r1", "r2");
        assertThat(few.opportunities().get(1).type()).isEqualTo(OpportunityType.PREPARATION_ONLY_FOR_HUMAN_LED_WORK);
        assertThat(few.opportunities().get(1).ruleId()).isEqualTo("OP2_SUPPORTING_HUMAN_LED_PREPARATION");

        var enough = policy.decide(handBuilt(0.5, good, good, row(2.8, 3.2, 3.6, 0.1, 2.0)));
        assertThat(enough.opportunities()).noneMatch(o -> o.category() == ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING);
        // Moderate assistance never counts as a promising opportunity.
        assertThat(policy.decide(handBuilt(0.5, row(2.3, 1.5, 1.0, 0.1, 2.0))).opportunities()).isEmpty();
    }

    // ---- Human value ----

    @Test
    void eachHumanValueReasonIsChosenFromTheStrongestSignalAndDistinctReasonsComeFirst() {
        var guidance = policy.decide(handBuilt(0.5,
                row(2.6, 2.9, 3.9, 0.2, 2.0),   // human-led, interaction
                row(2.6, 3.8, 2.0, 0.2, 2.0),   // human-led, context
                row(2.2, 2.0, 1.0, 3.9, 2.0),   // hands-on, physical
                row(2.6, 2.9, 3.8, 0.2, 2.0),   // human-led, interaction (second)
                row(3.4, 1.5, 1.0, 0.2, 2.0))); // good opportunity: never a human-value area
        assertThat(guidance.humanValueAreas()).hasSize(3)
                .extracting(HumanValueArea::reason).containsExactlyInAnyOrder(HumanValueReason.HUMAN_INTERACTION,
                        HumanValueReason.CONTEXTUAL_JUDGEMENT, HumanValueReason.PHYSICAL_PRESENCE);
        assertThat(guidance.humanValueAreas()).extracting(HumanValueArea::responsibilityKey).doesNotContain("r4", "r5");
        assertThat(guidance.humanValueAreas()).allSatisfy(area ->
                assertThat(area.explanation()).isEqualTo(area.reason().explanation()));
    }

    @Test
    void accountabilityIsTheReasonOnlyWhenNoHumanSignalIsHigh() {
        var guidance = policy.decide(handBuilt(0.5, row(1.2, 2.0, 2.0, 1.0, 3.9)));
        assertThat(guidance.humanValueAreas()).singleElement().satisfies(area -> {
            assertThat(area.reason()).isEqualTo(HumanValueReason.ACCOUNTABILITY);
            assertThat(area.ruleId()).isEqualTo("HV_ACCOUNTABILITY");
        });
    }

    // ---- Skills ----

    @Test
    void skillsAreConsolidatedRankedAndCappedAndRegulatedWorkPutsResponsibleUseFirst() {
        var guidance = policy.decide(handBuilt(3.0,
                row(3.4, 1.5, 1.0, 0.1, 2.0), row(3.4, 1.5, 1.0, 0.1, 2.0), row(3.3, 1.5, 1.0, 0.1, 2.0),
                row(2.6, 3.1, 3.9, 0.2, 3.9), row(2.6, 3.2, 3.6, 0.2, 2.0)));
        assertThat(guidance.skillPriorities()).hasSizeLessThanOrEqualTo(4)
                .extracting(SkillPriority::family).doesNotHaveDuplicates();
        assertThat(guidance.skillPriorities().getFirst().family()).isEqualTo(SkillFamily.RESPONSIBLE_AI_USE);
        assertThat(guidance.skillPriorities()).filteredOn(skill -> skill.family() == SkillFamily.VERIFICATION_AND_QUALITY_CHECKING)
                .singleElement().satisfies(skill -> assertThat(skill.supportingResponsibilityKeys()).containsExactly("r1", "r2", "r3"));
        // Five families are triggered; the cap of four drops the weakest, with ties broken by the fixed family order.
        assertThat(guidance.skillPriorities()).extracting(SkillPriority::family).containsExactly(
                SkillFamily.RESPONSIBLE_AI_USE, SkillFamily.VERIFICATION_AND_QUALITY_CHECKING,
                SkillFamily.AI_ASSISTED_WORKFLOW, SkillFamily.JUDGEMENT_AND_DECISION_MAKING);
    }

    @Test
    void aMainlyPhysicalRoleGetsPracticeAndSafetyAndNoInventedTechnicalSkill() {
        var guidance = policy.decide(handBuilt(3.9,
                row(2.2, 1.9, 0.9, 3.9, 3.9), row(2.3, 1.7, 0.6, 3.9, 3.9), row(2.0, 2.4, 0.6, 3.9, 3.9),
                row(3.1, 1.0, 0.7, 0.9, 3.9)));
        assertThat(guidance.skillPriorities()).extracting(SkillPriority::family)
                .contains(SkillFamily.HANDS_ON_PRACTICE_AND_SAFETY)
                .doesNotContain(SkillFamily.AI_ASSISTED_WORKFLOW);
        assertThat(guidance.skillPriorities()).extracting(SkillPriority::label)
                .noneMatch(label -> label.toLowerCase().matches(".*(coding|programming|build|course|certif).*"));
    }

    @Test
    void enteredSkillsAndTheProfessionNeverChangeTheGuidance() {
        double[][] rows = {row(3.4, 1.5, 1.0, 0.1, 2.0), row(2.6, 3.1, 3.9, 0.2, 2.0)};
        var plain = policy.decide(handBuilt("Role A", List.of(), 1.0, rows));
        var different = policy.decide(handBuilt("Completely different profession", List.of("Communication",
                "Expert in everything"), 1.0, rows));
        assertThat(different.opportunities()).isEqualTo(plain.opportunities());
        assertThat(different.humanValueAreas()).isEqualTo(plain.humanValueAreas());
        assertThat(different.skillPriorities()).isEqualTo(plain.skillPriorities());
        assertThat(different.plan()).isEqualTo(plain.plan());
        assertThat(different.safeguards()).isEqualTo(plain.safeguards());
    }

    // ---- Safeguards ----

    @Test
    void highOversightGetsCriticalSafeguardsFirstAndOrdinaryWorkGetsStandardOnesWithinTheCap() {
        var strict = policy.decide(handBuilt(3.9, row(2.0, 1.5, 1.0, 3.9, 3.9), row(3.4, 1.5, 1.0, 0.1, 3.9),
                row(3.4, 2.8, 1.0, 0.1, 3.0)));
        assertThat(strict.safeguards()).hasSize(4);
        assertThat(strict.safeguards().subList(0, 2)).extracting(ResponsibleUseSafeguard::importance)
                .containsOnly(SafeguardImportance.CRITICAL);
        assertThat(strict.safeguards()).extracting(ResponsibleUseSafeguard::type).contains(
                SafeguardType.NO_RELIANCE_FOR_SAFETY_CRITICAL_PHYSICAL_WORK, SafeguardType.ESCALATE_UNCERTAINTY);

        var ordinary = policy.decide(handBuilt(0.5, row(3.4, 1.5, 1.0, 0.1, 2.0)));
        assertThat(ordinary.safeguards()).extracting(ResponsibleUseSafeguard::importance).containsOnly(SafeguardImportance.STANDARD);
        assertThat(ordinary.safeguards()).extracting(ResponsibleUseSafeguard::type)
                .doesNotContain(SafeguardType.ESCALATE_UNCERTAINTY, SafeguardType.NO_RELIANCE_FOR_SAFETY_CRITICAL_PHYSICAL_WORK);
    }

    // ---- Plan ----

    @Test
    void thePlanHasThreePhasesOfTwoToFourTraceableActionsAndStartsLowRisk() {
        var guidance = policy.decide(handBuilt(1.8,
                row(3.9, 1.5, 1.0, 0.1, 3.9),   // top score despite the O1 governance penalty
                row(3.0, 2.8, 1.0, 0.1, 2.0),   // parts, no oversight: the lower-risk pilot
                row(2.6, 3.1, 3.9, 0.2, 2.0)));
        var plan = guidance.plan();
        for (List<PlanAction> phase : List.of(plan.days1To30(), plan.days31To60(), plan.days61To90())) {
            assertThat(phase).hasSizeBetween(2, 4).allSatisfy(action -> assertThat(action.ruleId()).isEqualTo(action.actionId()));
        }
        assertThat(plan.days1To30().getFirst().actionId()).isEqualTo("P1_CONFIRM_POLICY_AND_TOOLS");
        assertThat(guidance.opportunities().getFirst().responsibilityKey()).isEqualTo("r1");
        assertThat(plan.days31To60()).filteredOn(action -> action.actionId().equals("P2_PILOT_WITH_REVIEW"))
                .singleElement().satisfies(action -> assertThat(action.responsibilityKeys()).containsExactly("r2"));
    }

    @Test
    void withoutOpportunitiesThePlanStillHasTwoActionsPerPhaseAndInventsNoPilot() {
        var plan = policy.decide(handBuilt(0.5, row(2.0, 3.6, 3.9, 0.2, 2.0), row(1.0, 2.0, 1.0, 3.9, 2.0))).plan();
        for (List<PlanAction> phase : List.of(plan.days1To30(), plan.days31To60(), plan.days61To90())) {
            assertThat(phase).hasSizeBetween(2, 4);
        }
        assertThat(Stream.of(plan.days1To30(), plan.days31To60(), plan.days61To90()).flatMap(List::stream))
                .extracting(PlanAction::actionId).doesNotContain("P2_PILOT_WITH_REVIEW", "P3_ADOPT_OR_STOP");
    }

    // ---- Language and dependencies ----

    @Test
    void noCustomerFacingWordingClaimsAutomationReplacementOrComplianceAndThePolicyIsPureJava() throws Exception {
        var guidance = policy.decide(AnalysisSnapshots.live("solicitor-8"));
        String text = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
                .writeValueAsString(guidance).toLowerCase();
        assertThat(text).doesNotContain("automat", "replace", "ai can do", "safe from ai", "legislation", "compliant",
                "gdpr", "chatgpt", "copilot", "job loss", "certificat");
        try (var files = Files.walk(Path.of("src/main/java/com/chikere/futureai/responsibility/professional/guidance"))) {
            for (Path file : files.filter(path -> path.getFileName().toString().matches("(ProfessionalGuidancePolicyV1|GuidanceVocabulary|ProfessionalResponsibilityGuidance)\\.java")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain("org.springframework",
                        "jakarta.persistence", "org.springaicommunity", "openai", "stripe", "profession()");
            }
        }
    }

    private static String pilot(ProfessionalResponsibilityGuidance guidance) {
        return guidance.plan().days31To60().stream().filter(action -> action.actionId().equals("P2_PILOT_WITH_REVIEW"))
                .findFirst().orElseThrow().responsibilityKeys().getFirst();
    }

    private static List<String> keys(List<AiAssistanceOpportunity> opportunities) {
        return opportunities.stream().map(AiAssistanceOpportunity::responsibilityKey).toList();
    }
}
