package com.chikere.futureai.responsibility.professional.policy;

import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSource;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceLogCapture;
import com.chikere.futureai.trace.TraceStage;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;

import static com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory.AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT;
import static com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory.GOOD_AI_ASSISTANCE_OPPORTUNITY;
import static com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory.HUMAN_LED_AI_SUPPORTING;
import static com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory.MAINLY_HANDS_ON_OR_IN_PERSON;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Hand-built judgment values that sit exactly on, and just either side of, every rule. Not live data. */
class ProfessionalResponsibilityPolicyV1Test {

    private final ProfessionalResponsibilityPolicyV1 policy = new ProfessionalResponsibilityPolicyV1();

    @Test
    void levelsFollowTheRubricAnchorsWithInclusiveLowerBoundaries() {
        assertThat(ProfessionalResponsibilityPolicyV1.level(0.0)).isEqualTo(ResponsibilityLevel.LOW);
        assertThat(ProfessionalResponsibilityPolicyV1.level(Math.nextDown(1.5))).isEqualTo(ResponsibilityLevel.LOW);
        assertThat(ProfessionalResponsibilityPolicyV1.level(1.5)).isEqualTo(ResponsibilityLevel.MODERATE);
        assertThat(ProfessionalResponsibilityPolicyV1.level(Math.nextDown(2.5))).isEqualTo(ResponsibilityLevel.MODERATE);
        assertThat(ProfessionalResponsibilityPolicyV1.level(2.5)).isEqualTo(ResponsibilityLevel.HIGH);
        assertThat(ProfessionalResponsibilityPolicyV1.level(4.0)).isEqualTo(ResponsibilityLevel.HIGH);
        assertThatIllegalArgumentException().isThrownBy(() -> ProfessionalResponsibilityPolicyV1.level(Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> ProfessionalResponsibilityPolicyV1.level(4.01));
        assertThatIllegalArgumentException().isThrownBy(() -> ProfessionalResponsibilityPolicyV1.level(-0.01));
    }

    @Test
    void highPhysicalPresenceWinsOnlyWhenItIsAtLeastAsHighAsHumanDemand() {
        // assistance, context, interaction, physical, accountability
        assertThat(one(3.9, 1.0, 1.0, 2.5, 1.0)).satisfies(c -> rule(c, MAINLY_HANDS_ON_OR_IN_PERSON, "R1_PHYSICAL_DOMINANT"));
        assertThat(one(3.9, 1.0, 1.0, Math.nextDown(2.5), 1.0).category()).isEqualTo(GOOD_AI_ASSISTANCE_OPPORTUNITY);
        // A tie with human demand is still hands-on.
        assertThat(one(2.0, 3.2, 1.0, 3.2, 1.0)).satisfies(c -> rule(c, MAINLY_HANDS_ON_OR_IN_PERSON, "R1_PHYSICAL_DOMINANT"));
        assertThat(one(2.0, 1.0, 3.2, 3.2, 1.0).category()).isEqualTo(MAINLY_HANDS_ON_OR_IN_PERSON);
        // Higher human demand moves on to the human-demand rule: the Support-patients-and-families shape.
        assertThat(one(2.27, 2.85, 3.98, 3.21, 3.24)).satisfies(c -> rule(c, HUMAN_LED_AI_SUPPORTING, "R2_HUMAN_DEMAND_DOMINATES"));
        assertThat(one(1.0, 4.0, 4.0, 2.5, 1.0)).satisfies(c -> rule(c, HUMAN_LED_AI_SUPPORTING, "R2_HUMAN_DEMAND_DOMINATES"));
        // Physical below human demand but human demand not dominant over assistance: partial assistance.
        assertThat(one(3.0, 3.1, 1.0, 3.05, 1.0)).satisfies(c -> rule(c, AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT, "R5_PARTIAL_ASSISTANCE"));
    }

    @Test
    void highHumanDemandThatExceedsAssistanceByTheMarginIsHumanLed() {
        assertThat(one(2.75, 3.0, 1.0, 0, 1.0)).satisfies(c -> rule(c, HUMAN_LED_AI_SUPPORTING, "R2_HUMAN_DEMAND_DOMINATES"));
        assertThat(one(2.75, 1.0, 3.0, 0, 1.0)).satisfies(c -> rule(c, HUMAN_LED_AI_SUPPORTING, "R2_HUMAN_DEMAND_DOMINATES"));
        // Just inside the margin: assistance with high context, not dominated.
        assertThat(one(2.76, 3.0, 1.0, 0, 1.0)).satisfies(c -> rule(c, AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT, "R5_PARTIAL_ASSISTANCE"));
        assertThat(one(2.76, 1.0, 3.0, 0, 1.0).category()).isEqualTo(AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT);
        // Human demand must itself be High: a large margin over low assistance is not enough.
        assertThat(one(0.5, 2.4, 1.0, 0, 1.0)).satisfies(c -> rule(c, HUMAN_LED_AI_SUPPORTING, "R3_ASSISTANCE_LOW"));
        assertThat(one(1.6, Math.nextDown(2.5), 1.0, 0, 1.0).category()).isEqualTo(AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT);
    }

    @Test
    void lowAssistanceIsHumanLedAndHighAssistanceWithoutHighHumanDemandIsAGoodOpportunity() {
        assertThat(one(Math.nextDown(1.5), 1.0, 1.0, 0, 1.0)).satisfies(c -> rule(c, HUMAN_LED_AI_SUPPORTING, "R3_ASSISTANCE_LOW"));
        assertThat(one(1.5, 1.0, 1.0, 0, 1.0)).satisfies(c -> rule(c, AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT, "R5_PARTIAL_ASSISTANCE"));
        assertThat(one(2.5, Math.nextDown(2.5), Math.nextDown(2.5), 0, 1.0))
                .satisfies(c -> rule(c, GOOD_AI_ASSISTANCE_OPPORTUNITY, "R4_ASSISTANCE_HIGH_HUMAN_DEMAND_NOT_HIGH"));
        assertThat(one(Math.nextDown(2.5), 1.0, 1.0, 0, 1.0).category()).isEqualTo(AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT);
        // High assistance alongside high (but not dominant) context or interaction: parts only.
        assertThat(one(3.4, 2.5, 1.0, 0, 1.0).category()).isEqualTo(AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT);
        assertThat(one(3.4, 1.0, 2.5, 0, 1.0).category()).isEqualTo(AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT);
    }

    @Test
    void oversightComesFromVeryHighAccountabilityOrRegulatedContextWithAccountabilityAndIsIndependentOfCategory() {
        assertThat(one(3.4, 1.0, 1.0, 0, 3.5).oversightRule()).contains("O1_ACCOUNTABILITY_VERY_HIGH");
        assertThat(one(3.4, 1.0, 1.0, 0, Math.nextDown(3.5)).needsCloseHumanOversight()).isFalse();
        assertThat(one(1.0, 1.0, 1.0, 3.9, 3.9).needsCloseHumanOversight()).isTrue();

        assertThat(one(1.5, 1.0, 1.0, 0, 2.5, 1.5).oversightRule()).contains("O2_REGULATED_CONTEXT_WITH_ACCOUNTABILITY");
        assertThat(one(1.5, 1.0, 1.0, 0, 2.5, Math.nextDown(1.5)).needsCloseHumanOversight()).isFalse();
        assertThat(one(1.5, 1.0, 1.0, 0, Math.nextDown(2.5), 4.0).needsCloseHumanOversight()).isFalse();
        assertThat(one(Math.nextDown(1.5), 1.0, 1.0, 0, 3.0, 4.0).needsCloseHumanOversight()).isFalse();
        assertThat(one(3.4, 1.0, 1.0, 0, 1.0).oversightRule()).isEmpty();
        assertThat(ResponsibilityClassification.OVERSIGHT_LABEL).isEqualTo("Needs close human oversight");
    }

    @Test
    void clarityIsDiagnosticOnlyAndNeverChangesTheOutcome() {
        var clear = policy.classify(result(1.0, set(1, 0.95, 3.4, 1.0, 1.0, 0, 2.0))).classifications().getFirst();
        var vague = policy.classify(result(1.0, set(1, 0.05, 3.4, 1.0, 1.0, 0, 2.0))).classifications().getFirst();
        assertThat(vague.category()).isEqualTo(clear.category());
        assertThat(vague.needsCloseHumanOversight()).isEqualTo(clear.needsCloseHumanOversight());
        assertThat(vague.describedClearly()).isEqualTo(0.05);
    }

    @Test
    void aDecidingComparisonNearItsThresholdIsMarkedAsACloseCall() {
        assertThat(one(3.4, 1.0, 1.0, 2.6, 1.0).closeCall()).isTrue();
        assertThat(one(3.4, 1.0, 1.0, 3.9, 1.0).closeCall()).isFalse();
        assertThat(one(3.4, 2.45, 1.0, 0, 1.0).closeCall()).isTrue();
        assertThat(one(3.4, 1.0, 1.0, 0, 1.0).closeCall()).isFalse();
        assertThat(one(2.75, 3.1, 1.0, 0, 1.0).closeCall()).as("dominance margin 0.35 vs 0.25").isTrue();
    }

    @Test
    void labelsNeverUseAutomationOrSafetyLanguageAndOnlyV1SchemaIsAccepted() {
        for (ResponsibilityCategory category : ResponsibilityCategory.values()) {
            assertThat(category.label().toLowerCase()).doesNotContain("automat", "replace", "safe from", "ai can do",
                    "score", "%");
        }
        var other = new ProfessionalResponsibilityJudgmentResult("professional-responsibility-v2",
                ResponsibilityJudgmentSource.SYNTHETIC_FIXTURE, Optional.empty(), Optional.empty(), OptionalLong.empty(),
                OptionalLong.empty(), judgment(1.0), List.of(set(1, 1, 1, 1, 1, 1, 1)));
        assertThatIllegalArgumentException().isThrownBy(() -> policy.classify(other))
                .withMessageContaining("professional-responsibility-v1");
    }

    @Test
    void theClassificationIsTracedPerResponsibility() {
        try (TraceLogCapture log = TraceLogCapture.start()) {
            new ProfessionalResponsibilityPolicyV1(AssessmentTrace.enabled())
                    .classify(result(2.0, set(1, 0.3, 3.4, 1.0, 1.0, 0, 2.6)));
            var payload = log.only(TraceStage.RESPONSIBILITY_CLASSIFICATION).get("payload");
            assertThat(payload.get("policyVersion").asText()).isEqualTo("professional-responsibility-policy-v1");
            assertThat(payload.get("schemaVersion").asText()).isEqualTo("professional-responsibility-v1");
            assertThat(payload.at("/regulatedContext/level").asText()).isEqualTo("MODERATE");
            var item = payload.get("classifications").get(0);
            assertThat(item.get("responsibilityKey").asText()).isEqualTo("r1");
            assertThat(item.get("ruleId").asText()).isEqualTo("R4_ASSISTANCE_HIGH_HUMAN_DEMAND_NOT_HIGH");
            assertThat(item.get("category").asText()).isEqualTo("GOOD_AI_ASSISTANCE_OPPORTUNITY");
            assertThat(item.get("needsCloseHumanOversight").asBoolean()).isTrue();
            assertThat(item.get("oversightRule").asText()).isEqualTo("O2_REGULATED_CONTEXT_WITH_ACCOUNTABILITY");
            assertThat(item.at("/levels/ASSISTANCE_SCOPE").asText()).isEqualTo("HIGH");
            assertThat(item.at("/values/OUTCOME_ACCOUNTABILITY").asDouble()).isEqualTo(2.6);
            assertThat(item.at("/describedClearly/value").asDouble()).isEqualTo(0.3);
        }
    }

    @Test
    void thePolicyReadsNoRoleLevelDecisionAndOnlyTheResponsibilityCapabilityUsesIt() throws Exception {
        Path policyPackage = Path.of("src/main/java/com/chikere/futureai/responsibility/professional/policy");
        try (var files = Files.walk(policyPackage)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(file)).as(file.toString()).doesNotContain(
                        "com.chikere.futureai.decision", "org.springframework", "org.springaicommunity");
            }
        }
        // The paid lifecycle (Stage 4D) uses the policy; no role-level, report, payment or PDF code does.
        Path responsibility = Path.of("src/main/java/com/chikere/futureai/responsibility");
        try (var files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java") && !path.startsWith(responsibility))
                    .toList()) {
                assertThat(Files.readString(file)).as(file.toString())
                        .doesNotContain("ProfessionalResponsibilityPolicyV1", "ResponsibilityCategory");
            }
        }
    }

    // ---- Helpers ----

    private static void rule(ResponsibilityClassification classification, ResponsibilityCategory category, String rule) {
        assertThat(classification.category()).isEqualTo(category);
        assertThat(classification.ruleId()).isEqualTo(rule);
        assertThat(classification.displayLabel()).isEqualTo(category.label());
    }

    private ResponsibilityClassification one(double assistance, double context, double interaction, double physical,
            double accountability) {
        return one(assistance, context, interaction, physical, accountability, 0.5);
    }

    private ResponsibilityClassification one(double assistance, double context, double interaction, double physical,
            double accountability, double regulated) {
        return policy.classify(result(regulated, set(1, 0.9, assistance, context, interaction, physical, accountability)))
                .classifications().getFirst();
    }

    static ProfessionalResponsibilityJudgmentResult result(double regulated, ResponsibilityJudgmentSet... sets) {
        return new ProfessionalResponsibilityJudgmentResult("professional-responsibility-v1",
                ResponsibilityJudgmentSource.SYNTHETIC_FIXTURE, Optional.empty(), Optional.empty(), OptionalLong.empty(),
                OptionalLong.empty(), judgment(regulated), List.of(sets));
    }

    static ResponsibilityJudgmentSet set(int index, double clarity, double assistance, double context,
            double interaction, double physical, double accountability) {
        return new ResponsibilityJudgmentSet(index, "r" + index, "Hand-built responsibility " + index, clarity,
                judgment(assistance), judgment(context), judgment(interaction), judgment(physical), judgment(accountability));
    }

    static ResponsibilityScoredJudgment judgment(double value) {
        return new ResponsibilityScoredJudgment(value, Map.of(), OptionalDouble.empty(), OptionalInt.empty(),
                Optional.empty());
    }
}
