package com.chikere.futureai.responsibility.professional.policy;

import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSource;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

/**
 * Golden regression on the captured Stage 4B live Jev probe (jev-1.14.0, 2026-10-26): every responsibility of the
 * three saved Professional cases, classified by {@link ProfessionalResponsibilityPolicyV1}. The data is the real
 * provider output stored as a test resource; no Jev call is made.
 */
class ProfessionalResponsibilityPolicyV1LiveCalibrationTest {

    private static final JsonNode PROBE = load();
    private final ProfessionalResponsibilityPolicyV1 policy = new ProfessionalResponsibilityPolicyV1();

    @Test
    void registeredNurse() {
        var result = classify("registered-nurse-8");
        assertThat(result.regulatedContextLevel()).isEqualTo(ResponsibilityLevel.HIGH);
        expect(result,
                MAINLY_HANDS_ON_OR_IN_PERSON, true,       // Monitor patients and assess changes in their condition
                MAINLY_HANDS_ON_OR_IN_PERSON, true,       // Administer medication safely
                GOOD_AI_ASSISTANCE_OPPORTUNITY, true,     // Record observations and clinical information
                HUMAN_LED_AI_SUPPORTING, true,            // Escalate concerns to doctors and senior clinical staff
                HUMAN_LED_AI_SUPPORTING, true,            // Support patients and communicate with families
                HUMAN_LED_AI_SUPPORTING, true,            // Coordinate care with other healthcare professionals
                MAINLY_HANDS_ON_OR_IN_PERSON, true,       // Respond to emergencies and unexpected situations
                MAINLY_HANDS_ON_OR_IN_PERSON, true);      // Follow clinical procedures and safety requirements
        assertThat(result.classifications().get(4).oversightRule()).contains("O2_REGULATED_CONTEXT_WITH_ACCOUNTABILITY");
        // Physical presence 3.21 is High, but human interaction 3.98 is higher: the human signal decides.
        assertThat(result.classifications().get(4).ruleId()).isEqualTo("R2_HUMAN_DEMAND_DOMINATES");
        // Genuinely physical work stays hands-on: monitoring, medication, emergencies, procedures.
        for (int index : new int[] {0, 1, 6, 7}) {
            assertThat(result.classifications().get(index).ruleId()).as("r%d", index + 1).isEqualTo("R1_PHYSICAL_DOMINANT");
        }
    }

    @Test
    void javaSoftwareDeveloper() {
        var result = classify("java-software-developer-8");
        assertThat(result.regulatedContextLevel()).isEqualTo(ResponsibilityLevel.LOW);
        expect(result,
                AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT, false, // Design and develop backend services
                AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT, false, // Review pull requests and technical designs
                AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT, false, // Investigate and resolve production incidents
                HUMAN_LED_AI_SUPPORTING, false,            // Discuss requirements with product and business teams
                HUMAN_LED_AI_SUPPORTING, false,            // Make decisions about APIs, integrations and data models
                HUMAN_LED_AI_SUPPORTING, false,            // Mentor less experienced developers
                AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT, false, // Support releases and deployments
                AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT, false); // Improve automated tests and code quality
        // Within the close-call band: the API-decisions dominance margin (0.28 vs 0.25).
        assertThat(result.classifications().get(4).closeCall()).isTrue();
    }

    @Test
    void managementAccountant() {
        var result = classify("management-accountant-8");
        assertThat(result.regulatedContextLevel()).isEqualTo(ResponsibilityLevel.MODERATE);
        expect(result,
                GOOD_AI_ASSISTANCE_OPPORTUNITY, true,      // Prepare monthly management accounts
                GOOD_AI_ASSISTANCE_OPPORTUNITY, true,      // Analyse budgets against actual financial results
                GOOD_AI_ASSISTANCE_OPPORTUNITY, true,      // Produce financial reports for management
                AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT, true,  // Prepare forecasts and support annual budgeting
                AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT, false, // Investigate unusual costs or financial variances
                HUMAN_LED_AI_SUPPORTING, false,            // Meet department managers to discuss financial performance
                AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT, true,  // Provide financial information to support business decisions
                GOOD_AI_ASSISTANCE_OPPORTUNITY, true);     // Maintain accurate financial records and reporting processes
        // Contextual judgement 2.46 sits just under High; the 4-responsibility call put it at 2.58 (see below).
        assertThat(result.classifications().get(1).closeCall()).isTrue();
        assertThat(result.classifications()).filteredOn(ResponsibilityClassification::needsCloseHumanOversight)
                .allSatisfy(c -> assertThat(c.oversightRule()).contains("O2_REGULATED_CONTEXT_WITH_ACCOUNTABILITY"));
    }

    @Test
    void theSameResponsibilityKeepsItsOutcomeAcrossBatchSizesExceptTheOneMarkedCloseCall() {
        for (String role : List.of("registered-nurse", "java-software-developer", "management-accountant")) {
            var eight = classify(role + "-8").classifications();
            for (String smaller : List.of(role + "-1", role + "-4")) {
                var small = classify(smaller).classifications();
                for (int index = 0; index < small.size(); index++) {
                    boolean knownCloseCall = role.equals("management-accountant") && index == 1;
                    if (knownCloseCall) {
                        assertThat(small.get(index).category()).isEqualTo(AI_ASSISTANCE_WITH_HUMAN_JUDGEMENT);
                        assertThat(eight.get(index).category()).isEqualTo(GOOD_AI_ASSISTANCE_OPPORTUNITY);
                        continue;
                    }
                    assertThat(small.get(index).category()).as("%s r%d", smaller, index + 1)
                            .isEqualTo(eight.get(index).category());
                    assertThat(small.get(index).needsCloseHumanOversight()).as("%s r%d oversight", smaller, index + 1)
                            .isEqualTo(eight.get(index).needsCloseHumanOversight());
                }
            }
        }
    }

    @Test
    void clarityWasLowForCoherentlyJudgedResponsibilitiesSoItIsNotAGate() {
        var java = classify("java-software-developer-8").classifications();
        // "Mentor less experienced developers": clarity 0.14, yet a clear, stable human-led judgment.
        assertThat(java.get(5).describedClearly()).isLessThan(0.2);
        assertThat(java.get(5).category()).isEqualTo(HUMAN_LED_AI_SUPPORTING);
        assertThat(java.get(5).closeCall()).isFalse();
    }

    private void expect(ProfessionalResponsibilityClassificationResult result, Object... categoriesAndOversight) {
        List<Object> actual = new ArrayList<>();
        for (ResponsibilityClassification classification : result.classifications()) {
            actual.add(classification.category());
            actual.add(classification.needsCloseHumanOversight());
        }
        assertThat(actual).containsExactly(categoriesAndOversight);
        assertThat(result.policyVersion()).isEqualTo("professional-responsibility-policy-v1");
    }

    private ProfessionalResponsibilityClassificationResult classify(String run) {
        return policy.classify(result(PROBE.get(run)));
    }

    private static ProfessionalResponsibilityJudgmentResult result(JsonNode run) {
        List<ResponsibilityJudgmentSet> sets = new ArrayList<>();
        for (JsonNode set : run.get("responsibilities")) {
            sets.add(new ResponsibilityJudgmentSet(set.get("responsibilityIndex").asInt(),
                    set.get("responsibilityKey").asText(), set.get("responsibilityText").asText(),
                    set.get("describedClearly").asDouble(), judgment(set.get("assistanceScope")),
                    judgment(set.get("contextualJudgement")), judgment(set.get("humanInteraction")),
                    judgment(set.get("physicalPresence")), judgment(set.get("outcomeAccountability"))));
        }
        return new ProfessionalResponsibilityJudgmentResult(run.get("schemaVersion").asText(),
                ResponsibilityJudgmentSource.JEV, Optional.of(run.get("model").asText()), Optional.empty(),
                OptionalLong.empty(), OptionalLong.empty(), judgment(run.get("regulatedContext")), sets);
    }

    private static ResponsibilityScoredJudgment judgment(JsonNode node) {
        Map<Integer, Double> distribution = new LinkedHashMap<>();
        node.get("distribution").fields().forEachRemaining(level ->
                distribution.put(Integer.parseInt(level.getKey()), level.getValue().asDouble()));
        return new ResponsibilityScoredJudgment(node.get("value").asDouble(), distribution,
                node.hasNonNull("providerConfidence") ? OptionalDouble.of(node.get("providerConfidence").asDouble())
                        : OptionalDouble.empty(), OptionalInt.empty(), Optional.empty());
    }

    private static JsonNode load() {
        try (InputStream in = ProfessionalResponsibilityPolicyV1LiveCalibrationTest.class
                .getResourceAsStream("/calibration/responsibility-probe-2026-10-26.json")) {
            return new ObjectMapper().readTree(in);
        }
        catch (java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
