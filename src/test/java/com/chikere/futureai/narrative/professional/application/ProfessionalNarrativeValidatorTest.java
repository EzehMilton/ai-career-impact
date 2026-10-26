package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.DecisionFactorEffect;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceLogCapture;
import com.chikere.futureai.trace.TraceStage;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProfessionalNarrativeValidatorTest {

    private static final String FUTURE = "not forecast its future";
    private static final String CAPABILITY = "what AI can or cannot do";
    private static final String JOB_LOSS = "job loss, replacement or job security";
    private static final String ADAPTABILITY = "structure of the role, not the person";

    private final ProfessionalNarrativeValidator validator = new ProfessionalNarrativeValidator();

    // ---- The real Registered Nurse failures, each in the field where it appeared ----

    @Test
    void theRegisteredNurseForecastIsRejected() {
        rejects(safe(n -> with(n, Field.WHAT_THIS_MEANS, "Current AI developments are not expected to "
                + "fundamentally alter the core responsibilities of your role.")), FUTURE);
    }

    @Test
    void theRegisteredNurseCapabilityClaimIsRejected() {
        rejects(safe(n -> with(n, Field.HUMAN_ADVANTAGE,
                "These elements of care are deeply human and cannot be easily replicated by AI.")), CAPABILITY);
    }

    @Test
    void theRegisteredNursePersonalAdaptabilityClaimIsRejected() {
        rejects(safe(n -> with(n, Field.ADAPTABILITY, "The nurse's ability to adjust to changing circumstances, "
                + "like emergencies or varying patient needs, further illustrates this adaptability.")), ADAPTABILITY);
    }

    // ---- Paraphrases of each prohibited claim type ----

    @Test
    void forecastsAndReassurancesAboutTheFutureAreRejected() {
        for (String claim : List.of(
                "AI is not likely to change the core of the work you described.",
                "Your role will not significantly change as AI develops.",
                "Human contact will continue to be central to the work you described.",
                "Clinical judgement will remain essential in your work.",
                "Your role is unlikely to be replaced by AI.",
                "The work you described will not be replaced.",
                "This role is safe from AI.",
                "Your work is effectively future-proof.",
                "Much of the work is protected from automation.",
                "AI will replace this role.",
                "This role will disappear.",
                // Predictions of change are as unsupported as reassurances of stability.
                "Your current work appears likely to change as AI tools become more capable.",
                "This role will become increasingly automated.",
                "AI is expected to transform much of this work.",
                "Your profession will increasingly depend on AI.",
                "The work you described is likely to become more exposed to AI.",
                "Your role will be transformed by new technology.")) {
            rejects(safe(n -> with(n, Field.SUMMARY, claim)), FUTURE);
        }
    }

    @Test
    void categoricalClaimsAboutWhatAiCanNeverDoAreRejected() {
        for (String claim : List.of(
                "AI cannot replicate the empathy involved in your work.",
                "This kind of care cannot be replicated by AI.",
                "AI can't replace the trust patients place in you.",
                "Your hands-on work cannot be replaced.",
                "Only humans can provide this level of reassurance.",
                "AI is unable to read a changing situation at the bedside.",
                "AI will never manage the physical side of the work.",
                "It is impossible for AI to perform this work.")) {
            rejects(safe(n -> with(n, Field.HUMAN_ADVANTAGE, claim)), CAPABILITY);
        }
    }

    @Test
    void jobLossAndJobSecurityInferencesAreRejected() {
        for (String claim : List.of(
                "There is little risk of losing your job.",
                "The probability of replacement is low for the work you described.",
                "This suggests strong job security.",
                "Your job is relatively safe.",
                "The work is at low risk of automation.",
                "There is a real chance of being replaced by AI.")) {
            rejects(safe(n -> with(n, Field.SUMMARY, claim)), JOB_LOSS);
        }
    }

    @Test
    void roleAdaptabilityCreditedToThePersonIsRejected() {
        for (String claim : List.of(
                "Your ability to adapt makes the role highly adaptable.",
                "Your resilience and flexibility contribute to this result.",
                "You are clearly adaptable and open to change.",
                "Your willingness to learn new systems supports this result.",
                "Your openness to new technology explains the high result.",
                "The engineer's flexibility is what makes the role adaptable.")) {
            rejects(safe(n -> with(n, Field.ADAPTABILITY, claim)), ADAPTABILITY);
        }
    }

    @Test
    void aDisclaimerCannotShieldAClaimMadeInALaterClause() {
        rejects(safe(n -> with(n, Field.SUMMARY,
                "This does not mean much on its own, but AI cannot replicate the work you described.")), CAPABILITY);
    }

    // ---- Safe wording that must still be accepted ----

    @Test
    void theSafeExamplesAreAcceptedAgainstTheNurseFactors() {
        var narrative = new ProfessionalNarrative("Your assessment at a glance",
                "Your result reflects the work you described.",
                "The combination of measures describes the work as you described it today.",
                "Your work has a low level of AI Exposure because relatively little of it can be completed entirely "
                        + "through digital systems, while skilled physical presence and human trust also reduce the "
                        + "overlap with AI capabilities.",
                "The work relies strongly on human interaction, situational judgement and accountability. This "
                        + "contributes to the Human Advantage result but does not imply that AI cannot assist with "
                        + "parts of the work.",
                "There may be scope for AI to assist with record keeping while you remain responsible for care.",
                "The structure of the role combines contextual work, human responsibility and scope for "
                        + "technological assistance, which contributes to its Role Adaptability result.",
                List.of("Review which records could be drafted with assistance, checking each one yourself.",
                        "Keep prioritising the hands-on parts of the work that the assessment highlights.",
                        "Note where you are accountable for decisions so any assistance stays under your review."),
                "This assessment does not predict whether your job will be replaced or how the profession will "
                        + "change in future.");

        assertThat(validator.validate(narrative, nurseContext())).isSameAs(narrative);
    }

    @Test
    void disclaimersContainingCannotOrJobLossAreAccepted() {
        for (String disclaimer : List.of(
                "AI Exposure is not a probability that you will lose your job.",
                "The assessment does not show that AI cannot perform this work.",
                "This assessment does not predict whether your role will change.",
                "It is not a prediction that your role will remain the same.",
                "The assessment suggests AI may assist with some tasks; it cannot determine your individual job prospects.",
                "Human Advantage doesn't mean that only humans can do this work.",
                "This assessment does not predict how your role will change in future.",
                "The result does not mean that the profession will remain unchanged.",
                "The result should not be read as saying that the profession will remain unchanged.")) {
            var narrative = safe(n -> with(n, Field.CAUTION, disclaimer));
            assertThat(validator.validate(narrative, nurseContext())).as(disclaimer).isSameAs(narrative);
        }
    }

    @Test
    void roleAdaptabilityDescribingTheRoleOrDisclaimingPersonalTraitsIsAccepted() {
        for (String text : List.of(
                "The role's ability to accommodate change comes from its mix of contextual and accountable work.",
                "Role Adaptability describes the work itself. It does not assess your motivation or ability to learn.")) {
            var narrative = safe(n -> with(n, Field.ADAPTABILITY, text));
            assertThat(validator.validate(narrative, nurseContext())).as(text).isSameAs(narrative);
        }
    }

    // ---- Grounding in the factors Java selected (nurse: digital, trust and physical all lower exposure) ----

    @Test
    void anExposureExplanationThatIgnoresEverySelectedFactorIsRejected() {
        rejects(safe(n -> with(n, Field.EXPOSURE, "Your result reflects the overall profile of your role.")),
                "explain at least one of the factors Java selected");
    }

    @Test
    void anExposureExplanationThatReversesASelectedFactorIsRejected() {
        rejects(safe(n -> with(n, Field.EXPOSURE, "Much of your work is digital, which increases your AI Exposure.")),
                "must not reverse the direction");
        rejects(safe(n -> with(n, Field.EXPOSURE, "Little of your work can be done through digital systems. "
                + "Your physical presence at the bedside raises your AI Exposure.")), "must not reverse the direction");
    }

    @Test
    void anExposureExplanationThatFollowsTheSelectedFactorsIsAccepted() {
        for (String explanation : List.of(
                "Relatively little of the work you described can be completed through digital systems, which lowers "
                        + "your AI Exposure.",
                "Little of your work can be completed digitally, and it depends on physical presence and on the "
                        + "trust of the people you care for, which reduces exposure.")) {
            var narrative = safe(n -> with(n, Field.EXPOSURE, explanation));
            assertThat(validator.validate(narrative, nurseContext())).as(explanation).isSameAs(narrative);
        }
    }

    // ---- The dominant AI Exposure factor must lead the explanation ----

    private static final String DOMINANT = "must include the dominant factor";

    @Test
    void lowExposureMustIncludeTheRank1FactorLoweringIt() {
        // The live Registered Nurse explanation named the second and third factors but not the first.
        rejects(safe(n -> with(n, Field.EXPOSURE, "Your work has a low level of AI Exposure, primarily due to factors "
                + "such as the reliance on skilled physical presence and the importance of human trust and "
                + "relationships in your role.")), DOMINANT);
        var passing = safe(n -> with(n, Field.EXPOSURE, "Your work has a low level of AI Exposure because relatively "
                + "little of it can be completed entirely through digital systems, while skilled physical presence "
                + "and human trust also reduce the overlap with AI capabilities."));
        assertThat(validator.validate(passing, nurseContext())).isSameAs(passing);
    }

    @Test
    void highExposureMustIncludeTheRank1FactorRaisingIt() {
        var routine = context("routine_software_developer");
        assertThat(routine.measures().getFirst().band()).isEqualTo("High");
        assertThat(routine.exposureDrivers().getFirst().dimensionId()).isEqualTo("digital_executability");
        var omitted = safe(n -> with(n, Field.EXPOSURE,
                "Much of the work you described follows set routines, which raises your AI Exposure."));
        assertThatThrownBy(() -> validator.validate(omitted, routine)).hasMessageContaining(DOMINANT);
        var present = safe(n -> with(n, Field.EXPOSURE, "Much of the work you described can be completed through "
                + "digital systems and follows set routines, which raises your AI Exposure."));
        assertThat(validator.validate(present, routine)).isSameAs(present);
    }

    @Test
    void moderateExposureUsesTheSideWhoseRank1FactorHasTheLargerAbsoluteEffect() {
        var architect = context("senior_software_architect");
        assertThat(architect.measures().getFirst().band()).isEqualTo("Moderate");
        assertThat(Math.abs(architect.exposureDrivers().getFirst().signedEffect()))
                .isGreaterThan(Math.abs(architect.protectiveFactors().getFirst().signedEffect()));
        // The rank-1 driver (digital work) outweighs the rank-1 protective factor (less routine work), so it must appear.
        var protectiveOnly = safe(n -> with(n, Field.EXPOSURE,
                "Your work is less repetitive and depends on changing situations, which lowers your AI Exposure."));
        assertThatThrownBy(() -> validator.validate(protectiveOnly, architect)).hasMessageContaining(DOMINANT);
        var withDriver = safe(n -> with(n, Field.EXPOSURE, "Much of the work you described can be performed "
                + "digitally, which raises your AI Exposure."));
        assertThat(validator.validate(withDriver, architect)).isSameAs(withDriver);
    }

    @Test
    void anExactModerateTieAcceptsEitherRank1Factor() {
        var tie = manualContext("Moderate",
                List.of(factor("digital_executability", DecisionFactorEffect.INCREASES_AI_EXPOSURE, 0.05)),
                List.of(factor("physical_world_dependency", DecisionFactorEffect.REDUCES_AI_EXPOSURE, -0.05)));
        for (String explanation : List.of("Much of your work can be performed digitally, which raises your AI Exposure.",
                "Your work relies on physical presence, which lowers your AI Exposure.")) {
            var narrative = safe(n -> with(n, Field.EXPOSURE, explanation));
            assertThat(validator.validate(narrative, tie)).as(explanation).isSameAs(narrative);
        }
    }

    @Test
    void anEmptyRelevantListFallsBackToOrdinaryGroundingAndNoFactorsRequiresNothing() {
        // Low result with no exposure-lowering factors: any selected factor grounds the explanation.
        var lowWithoutProtective = manualContext("Low",
                List.of(factor("task_repeatability", DecisionFactorEffect.INCREASES_AI_EXPOSURE, 0.02)), List.of());
        var grounded = safe(n -> with(n, Field.EXPOSURE, "Some of your work follows set routines."));
        assertThat(validator.validate(grounded, lowWithoutProtective)).isSameAs(grounded);
        assertThatThrownBy(() -> validator.validate(safe(n -> with(n, Field.EXPOSURE,
                "Your result reflects your overall profile.")), lowWithoutProtective))
                .hasMessageContaining("explain at least one of the factors Java selected");
        // No factors at all: nothing is manufactured.
        var none = manualContext("Moderate", List.of(), List.of());
        var anything = safe(n -> with(n, Field.EXPOSURE, "Your result reflects your overall profile."));
        assertThat(validator.validate(anything, none)).isSameAs(anything);
    }

    // ---- Internal dimension labels never reach the visitor ----

    @Test
    void internalDimensionLabelsAndCodesAreRejected() {
        for (String jargon : List.of(
                "Your role benefits from high human trust dependency.",
                "The task repeatability of your work is low.",
                "Your digital executability is limited.",
                "Physical-world dependency is central to your role.",
                "Contextual complexity shapes much of your day.",
                "Your high original judgement supports this result.",
                "Accountability responsibility is an important factor here.",
                "The information sufficiency of your description was good.",
                "The factor human_trust_dependency applies to your work.",
                "This reflects LOW_DIGITAL_EXECUTABILITY in your work.")) {
            rejects(safe(n -> with(n, Field.SUMMARY, jargon)), "plain language, not internal assessment labels");
        }
    }

    @Test
    void plainEnglishDescriptionsOfTheSameFactorsAreAccepted() {
        for (String plain : List.of(
                "The importance of trust and relationships in the work you described stands out.",
                "Much of the work cannot be completed entirely through digital systems.",
                "Your work depends on understanding changing situations and their context.",
                "The role relies on original judgement and synthesis.",
                "Some of your work is repeatable.",
                "Accountability and responsibility for decisions are part of the work you described.",
                "Physical presence matters in the work you described.")) {
            var narrative = safe(n -> with(n, Field.SUMMARY, plain));
            assertThat(validator.validate(narrative, nurseContext())).as(plain).isSameAs(narrative);
        }
    }

    // ---- Regulated and safety-critical procedures ----

    @Test
    void adviceToSetAsideLoosenOrWorkAroundProceduresIsRejected() {
        for (String action : List.of(
                "Personalise established clinical routines to make the work less repetitive.",
                "Relax standard procedures where AI can make the process quicker.",
                "Work around existing safety checks when the tool is confident.",
                "Reflect on the established routines in your tasks and think about areas where you can introduce "
                        + "more personalised approaches to enhance patient care.",
                "Bypass the confidentiality policy when sharing notes with an AI tool.",
                "Skip the second check on medication when the system agrees.")) {
            rejects(safe(n -> with(n, Field.ACTION, action)), "never advise setting aside");
        }
    }

    @Test
    void adviceThatKeepsToProceduresAndHumanReviewIsAccepted() {
        for (String action : List.of(
                "Explore approved technology while continuing to follow required clinical procedures and safety checks.",
                "Use AI assistance only with appropriate human review and organisational controls.",
                "Use approved technology in ways that remain consistent with clinical procedures and organisational policy.",
                "Explore where technology could assist while keeping the required safety checks and human review.",
                "Never bypass safety checks, even when a tool seems confident.",
                "Replace manual note-taking with an approved system where your organisation allows it.")) {
            var narrative = safe(n -> with(n, Field.ACTION, action));
            assertThat(validator.validate(narrative, nurseContext())).as(action).isSameAs(narrative);
        }
    }

    // ---- Natural language ----

    @Test
    void claimsAboutTheWholeOccupationAreRejected() {
        rejects(safe(n -> with(n, Field.SUMMARY, "Registered nurses are skilled at building trust.")),
                "not make claims about the whole occupation");
        rejects(safe(n -> with(n, Field.SUMMARY, "Nurses will always be needed on wards.")), FUTURE);
    }

    @Test
    void enumStyleBandWordsAndAmericanSpellingsAreRejectedButTheUsersOwnSpellingMayBeQuoted() {
        rejects(safe(n -> with(n, Field.SUMMARY, "Your AI Exposure is LOW.")), "natural band words");
        rejects(safe(n -> with(n, Field.SUMMARY, "Prioritize the hands-on parts of your work.")), "British English");
        var quoted = safe(n -> with(n, Field.SUMMARY, "You listed clinical judgment among your skills."));
        assertThat(validator.validate(quoted, nurseContext())).isSameAs(quoted);
    }

    // ---- Existing behaviour ----

    @Test
    void rejectsNumericScoreOrPercentageClaims() {
        rejects(safe(n -> with(n, Field.SUMMARY, "Exposure is 9.5 / 10")), "numeric scores");
        rejects(safe(n -> with(n, Field.SUMMARY, "Around 92% of similar roles change")), "numeric scores");
        var trailing = new ProfessionalNarrative("Headline", "Summary", "Meaning", "Exposure", "Human",
                "Complementarity", "Adaptability", List.of("Action one", "Action two", "Exposure sits at 70%"),
                "Limitations");
        assertThatThrownBy(() -> validator.validate(trailing)).hasMessageContaining("numeric scores");
    }

    @Test
    void aMissingNarrativeIsStillRejected() {
        assertThatThrownBy(() -> validator.validate(null, nurseContext()))
                .hasMessage("generated narrative must not be null");
    }

    @Test
    void modelRejectsMissingSectionsAndWrongActionBounds() {
        assertThatThrownBy(() -> new ProfessionalNarrative("", "Summary", "Meaning", "Exposure", "Human",
                "Complementarity", "Adaptability", List.of("One", "Two", "Three"), "Limitations"))
                .hasMessageContaining("headline");
        assertThatThrownBy(() -> new ProfessionalNarrative("Headline", "Summary", "Meaning", "Exposure", "Human",
                "Complementarity", "Adaptability", List.of("One", "Two"), "Limitations"))
                .hasMessageContaining("between 3 and 5");
    }

    @Test
    void everyDevelopmentFixtureNarrativePassesTheStricterValidator() {
        var generator = new com.chikere.futureai.narrative.professional.fixture.LocalFixtureProfessionalNarrativeGenerator();
        for (String fixture : LocalFixtureProfessionalJudgmentProvider.supportedFixtureIds()) {
            var context = context(fixture);
            var narrative = generator.generate(context);
            assertThat(validator.validate(narrative, context)).as(fixture).isSameAs(narrative);
        }
    }

    // ---- Responsibilities are role-level examples, never separately scored ----

    @Test
    void aSubmittedResponsibilityPresentedAsSeparatelyScoredIsRejected() {
        var context = responsibilitiesContext();
        for (String claim : List.of(
                "Responding to emergencies has a low AI Exposure score.",
                "Monitoring patients is highly exposed to AI.",
                "Your medication rounds carry a high exposure rating.",
                "Recording clinical information is highly automatable.",
                "Supporting patients has a Moderate AI Exposure band.",
                "Monitoring patients has Low AI Exposure.",
                "Recording observations scores highly for automation.",
                "Responding to emergencies has an exposure score of 2.",
                "Medication administration is moderately exposed to AI.",
                "Patient monitoring is highly automatable.")) {
            var narrative = safe(n -> with(n, Field.SUMMARY, claim));
            assertThatThrownBy(() -> validator.validate(narrative, context)).as(claim)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("not present a submitted responsibility as separately scored");
        }
    }

    @Test
    void responsibilitiesUsedAsExamplesOfTheRoleLevelAssessmentAreAccepted() {
        var context = responsibilitiesContext();
        for (String example : List.of(
                "Responding to emergencies illustrates the changing real-world conditions involved in the work you described.",
                "Monitoring patients is one of the responsibilities you submitted.",
                "Recording clinical information may be an area worth exploring for appropriate AI assistance, subject "
                        + "to human review.",
                "Your responsibilities help explain the overall characteristics of the role-level assessment.",
                "Monitoring patients and responding to emergencies illustrate the physical and contextual nature of "
                        + "the work you described.",
                "Your responsibilities include communicating with patients and families, which illustrates the "
                        + "human-interaction component of the work you described.",
                "Monitoring patients reflects the hands-on work behind your Low AI Exposure result, which is "
                        + "assessed for the role as a whole.")) {
            var narrative = safe(n -> with(n, Field.SUMMARY, example));
            assertThat(validator.validate(narrative, context)).as(example).isSameAs(narrative);
        }
    }

    @Test
    void theTraceNamesTheResponsibilityRuleWhenItRejects() {
        try (TraceLogCapture log = TraceLogCapture.start()) {
            var traced = new ProfessionalNarrativeValidator(AssessmentTrace.enabled());
            String sentence = "Monitoring patients is highly exposed to AI.";

            assertThatThrownBy(() -> traced.validate(safe(n -> with(n, Field.SUMMARY, sentence)),
                    responsibilitiesContext())).hasMessageContaining("separately scored");

            JsonNode validation = log.only(TraceStage.NARRATIVE_VALIDATION).get("payload");
            assertThat(validation.get("outcome").asText()).isEqualTo("REJECTED");
            assertThat(validation.get("rejectedBy").asText()).isEqualTo("NO_RESPONSIBILITY_LEVEL_SCORING");
            JsonNode rule = validation.get("rules").get(validation.get("rules").size() - 1);
            assertThat(rule.get("rule").asText()).isEqualTo("NO_RESPONSIBILITY_LEVEL_SCORING");
            assertThat(rule.get("passed").asBoolean()).isFalse();
            assertThat(rule.get("triggeringText").asText()).isEqualTo(sentence);
        }
    }

    /** The nurse fixture with responsibilities matching the examples in the rule's specification. */
    private static ProfessionalNarrativeContext responsibilitiesContext() {
        var state = new ProfessionalAssessmentState("Registered Nurse", "I care for patients on a busy ward.",
                List.of("Monitor patients", "Respond to emergencies", "Administer medication",
                        "Record clinical information", "Record observations", "Support patients and families"),
                List.of("Clinical observation"), OptionalInt.of(9));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("registered_nurse").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        return new ProfessionalNarrativeContextFactory().create(new StoredProfessionalAssessment(UUID.randomUUID(),
                Instant.parse("2026-10-01T00:00:00Z"), state, judgments, new ProfessionalAssessmentResult(decision,
                        AssessmentSource.JEV, judgments.schemaVersion(), decision.policyVersion()), Optional.empty()));
    }

    // ---- Tracing ----

    @Test
    void theTraceNamesTheRuleAndSentenceThatRejectedTheNarrative() {
        try (TraceLogCapture log = TraceLogCapture.start()) {
            var traced = new ProfessionalNarrativeValidator(AssessmentTrace.enabled());
            String sentence = "The nurse's ability to adjust to changing circumstances further illustrates this adaptability.";

            assertThatThrownBy(() -> traced.validate(safe(n -> with(n, Field.ADAPTABILITY, sentence)), nurseContext()))
                    .hasMessageContaining(ADAPTABILITY);

            JsonNode validation = log.only(TraceStage.NARRATIVE_VALIDATION).get("payload");
            assertThat(validation.get("outcome").asText()).isEqualTo("REJECTED");
            assertThat(validation.get("rejectedBy").asText()).isEqualTo("ROLE_ADAPTABILITY_IS_ROLE_LEVEL");
            assertThat(validation.get("rules").findValuesAsText("rule")).containsExactly("NARRATIVE_PRESENT",
                    "WELL_FORMED_STRUCTURED_OUTPUT", "NO_NUMERIC_SCORES_OR_PERCENTAGES",
                    "NO_UNSUPPORTED_FUTURE_PREDICTIONS", "NO_CATEGORICAL_AI_CAPABILITY_CLAIMS",
                    "NO_JOB_LOSS_OR_SECURITY_INFERENCE", "ROLE_ADAPTABILITY_IS_ROLE_LEVEL", "NO_INTERNAL_DIMENSION_JARGON",
                    "NO_UNSAFE_PROCEDURE_DEVIATION", "NO_ENUM_STYLE_BAND_WORDS", "BRITISH_ENGLISH_SPELLING",
                    "EXPOSURE_EXPLANATION_GROUNDED_IN_FACTORS", "EXPOSURE_EXPLANATION_LEADS_WITH_DOMINANT_FACTOR",
                    "EXPOSURE_FACTOR_DIRECTIONS_MATCH", "NO_OCCUPATION_WIDE_CLAIMS",
                    "NO_RESPONSIBILITY_LEVEL_SCORING");
            assertThat(validation.at("/rules/6/triggeringText").asText()).isEqualTo(sentence);
            assertThat(validation.at("/rules/7/reason").asText()).startsWith("NOT_EVALUATED");

            log.clear();
            traced.validate(safe(UnaryOperator.identity()), nurseContext());
            assertThat(log.only(TraceStage.NARRATIVE_VALIDATION).at("/payload/outcome").asText()).isEqualTo("ACCEPTED");
        }
    }

    // ---- Helpers ----

    private void rejects(ProfessionalNarrative narrative, String reason) {
        assertThatThrownBy(() -> validator.validate(narrative, nurseContext()))
                .as(narrative.toString())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(reason);
    }

    private enum Field { SUMMARY, WHAT_THIS_MEANS, EXPOSURE, HUMAN_ADVANTAGE, ADAPTABILITY, ACTION, CAUTION }

    /** A narrative that passes every rule against the nurse factors, with one field changed. */
    private static ProfessionalNarrative safe(UnaryOperator<ProfessionalNarrative> change) {
        return change.apply(new ProfessionalNarrative("Your assessment", "Your result reflects the work you described.",
                "The measures describe the work as you described it today.",
                "Relatively little of the work you described can be completed through digital systems, which lowers "
                        + "your AI Exposure.",
                "The work relies on trust, physical presence and accountable decisions.",
                "There may be scope for assistance with records while you stay responsible for the outcome.",
                "The structure of the role combines contextual work and human responsibility.",
                List.of("Review records that could be drafted with assistance, checking each yourself.",
                        "Keep prioritising the hands-on work the assessment highlights.",
                        "Note decisions you are accountable for."),
                "This explains a structured assessment of the work described, not a prediction."));
    }

    private static ProfessionalNarrative with(ProfessionalNarrative n, Field field, String text) {
        return new ProfessionalNarrative(n.headline(),
                field == Field.SUMMARY ? text : n.summary(),
                field == Field.WHAT_THIS_MEANS ? text : n.whatThisMeans(),
                field == Field.EXPOSURE ? text : n.exposureExplanation(),
                field == Field.HUMAN_ADVANTAGE ? text : n.humanAdvantageExplanation(),
                n.complementarityExplanation(),
                field == Field.ADAPTABILITY ? text : n.adaptabilityExplanation(),
                field == Field.ACTION ? List.of(text, n.practicalActions().get(1), n.practicalActions().get(2))
                        : n.practicalActions(),
                field == Field.CAUTION ? text : n.cautionOrLimitations());
    }

    private static ProfessionalNarrativeContext.FactorContext factor(String id, DecisionFactorEffect effect,
            double signedEffect) {
        var direction = com.chikere.futureai.decision.professional.ProfessionalFactorDirection.valueOf(
                (effect == DecisionFactorEffect.INCREASES_AI_EXPOSURE ? "HIGH_" : "LOW_") + id.toUpperCase());
        if (id.equals("physical_world_dependency")) {
            direction = com.chikere.futureai.decision.professional.ProfessionalFactorDirection.HIGH_PHYSICAL_WORLD_DEPENDENCY;
        }
        return new ProfessionalNarrativeContext.FactorContext(id, id, "description", effect, direction, 1, 2.0,
                signedEffect);
    }

    /** A hand-built context with a chosen AI Exposure band and factor lists. */
    private static ProfessionalNarrativeContext manualContext(String exposureBand,
            List<ProfessionalNarrativeContext.FactorContext> drivers,
            List<ProfessionalNarrativeContext.FactorContext> protective) {
        var measures = java.util.Arrays.stream(com.chikere.futureai.decision.professional.ProfessionalMeasure.values())
                .map(measure -> new ProfessionalNarrativeContext.MeasureContext(measure, measure.name(), 5.0,
                        measure == com.chikere.futureai.decision.professional.ProfessionalMeasure.AI_EXPOSURE
                                ? exposureBand : "Moderate"))
                .toList();
        return new ProfessionalNarrativeContext("Registered Nurse", "I care for patients on a busy ward.",
                List.of("Monitor patients"), List.of("Clinical observation"), OptionalInt.empty(),
                com.chikere.futureai.assessment.domain.AssessmentStatus.READY, measures, drivers, protective,
                AssessmentSource.JEV, "professional-v1", "professional-decision-v1");
    }

    /** The registered-nurse fixture: digital executability, human trust and physical world all lower exposure. */
    private static ProfessionalNarrativeContext nurseContext() {
        return context("registered_nurse");
    }

    private static ProfessionalNarrativeContext context(String fixture) {
        var state = new ProfessionalAssessmentState("Registered Nurse", "I care for patients on a busy ward.",
                List.of("Monitor patients", "Respond to deterioration"), List.of("Clinical judgment"),
                OptionalInt.of(9));
        var judgments = new LocalFixtureProfessionalJudgmentProvider(fixture).judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        return new ProfessionalNarrativeContextFactory().create(new StoredProfessionalAssessment(UUID.randomUUID(),
                Instant.parse("2026-10-01T00:00:00Z"), state, judgments, new ProfessionalAssessmentResult(decision,
                        AssessmentSource.JEV, judgments.schemaVersion(), decision.policyVersion()), Optional.empty()));
    }
}
