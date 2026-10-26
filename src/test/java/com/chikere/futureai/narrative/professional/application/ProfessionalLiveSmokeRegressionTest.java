package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.trace.NarrativeRuleViolation;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Deterministic regressions for the three live Stage 1 smoke runs: Registered Nurse (Low), Java Software
 * Developer (Moderate) and Management Accountant (High). Each uses a fixture with the same AI Exposure band and the
 * person's real job title and responsibilities.
 */
class ProfessionalLiveSmokeRegressionTest {

    private final ProfessionalNarrativeValidator validator = new ProfessionalNarrativeValidator();

    // ---- Registered Nurse: stays green, first attempt, no repair ----

    @Test
    void theRegisteredNurseNarrativeIsAcceptedOnTheFirstAttemptWithoutRepair() {
        var assessment = stored("registered_nurse", "Registered Nurse", List.of("Monitor patients",
                "Administer medication", "Respond to emergencies", "Record clinical information"));
        var narrative = narrative("Your AI Exposure result is low, primarily because the work you described cannot be "
                + "fully performed through digital systems.");
        var context = new ProfessionalNarrativeContextFactory().create(assessment);
        assertThat(context.measures().getFirst().band()).isEqualTo("Low");
        assertThat(validator.validate(narrative, context)).isSameAs(narrative);
        assertThat(validator.validate(with(narrative, "Patient monitoring illustrates the physical and real-world nature "
                + "of the nursing work you described."), context)).isNotNull();
        rejects(with(narrative, "Patient monitoring has low AI Exposure."), context, "NO_RESPONSIBILITY_LEVEL_SCORING");

        AtomicInteger generations = new AtomicInteger();
        AtomicInteger repairs = new AtomicInteger();
        var result = service(assessment, new ProfessionalNarrativeGenerator() {
            @Override public ProfessionalNarrative generate(ProfessionalNarrativeContext ignored) {
                generations.incrementAndGet();
                return narrative;
            }
            @Override public ProfessionalNarrative repair(ProfessionalNarrativeContext ignored, NarrativeRepairFeedback f) {
                repairs.incrementAndGet();
                return narrative;
            }
            @Override public NarrativeSource source() { return NarrativeSource.OPENAI; }
            @Override public String providerIdentifier() { return "openai"; }
        }).generateFor(assessment.id());

        assertThat(result.status()).isEqualTo(NarrativeStatus.AVAILABLE);
        assertThat(result.promptSchemaVersion()).isEqualTo("professional-narrative-v6");
        assertThat(generations.get()).isEqualTo(1);
        assertThat(repairs.get()).isZero();
    }

    // ---- Java Software Developer: role-level statements pass, responsibility-level ones fail ----

    private static final String JAVA_LIVE_SENTENCE = "The assessment indicates that your role as a Java Software "
            + "Developer has a moderate level of AI exposure, with significant opportunities for AI assistance while "
            + "still requiring human input.";

    @Test
    void theLiveJavaDeveloperRoleLevelSentenceIsNoLongerRejected() {
        var context = javaContext();
        assertThat(context.measures().getFirst().band()).isEqualTo("Moderate");
        for (String roleLevel : List.of(JAVA_LIVE_SENTENCE,
                "Your role as a Java Software Developer has moderate AI Exposure.",
                "Your assessment indicates moderate AI Exposure for the work you described.",
                "The role has high AI Complementarity.",
                "Reviewing code is an example of the digitally performed work you described.")) {
            var narrative = with(narrative(javaExposure()), roleLevel);
            assertThat(validator.validate(narrative, context)).as(roleLevel).isSameAs(narrative);
        }
    }

    @Test
    void javaDeveloperResponsibilityLevelScoresAreStillRejected() {
        var context = javaContext();
        for (String claim : List.of("Designing backend services has high AI Exposure.",
                "Reviewing pull requests scores highly for automation.",
                "Writing unit tests has a moderate exposure rating.")) {
            rejects(with(narrative(javaExposure()), claim), context, "NO_RESPONSIBILITY_LEVEL_SCORING");
        }
        // The live repair attempt's wording is still caught as internal jargon.
        rejects(with(narrative(javaExposure()), "This is primarily due to the high digital executability of your tasks."),
                context, "NO_INTERNAL_DIMENSION_JARGON");
    }

    @Test
    void theJavaDeveloperProviderPayloadCarriesNoInternalIdentifiersButKeepsTheDominantFactor() throws Exception {
        var context = javaContext();
        String payload = new ObjectMapper().findAndRegisterModules().writeValueAsString(ProfessionalNarrativePrompt.from(context));

        assertThat(payload).doesNotContain("dimensionId", "digital_executability", "task_repeatability",
                "_DEPENDENCY", "_EXECUTABILITY", "_REPEATABILITY", "HIGH_", "LOW_", "INCREASES_AI_EXPOSURE",
                "REDUCES_AI_EXPOSURE", "AI_EXPOSURE", "direction", "signedEffect", "judgmentScore", "assessmentStatus");
        assertThat(payload).contains("\"description\":\"Much of the work can be performed digitally\"",
                "\"effect\":\"raises AI Exposure\"", "\"dominant\":true");
        // The validator still uses the full internal context, so the dominant factor is still enforced.
        rejects(with(narrative("Your work is less repetitive, which lowers your AI Exposure."), "Summary."), context,
                "EXPOSURE_EXPLANATION_LEADS_WITH_DOMINANT_FACTOR");
    }

    // ---- Java Software Developer: the exact live EXPOSURE_FACTOR_DIRECTIONS_MATCH false positive ----

    private static final String JAVA_LIVE_REPAIRED_EXPLANATION = "Your work has a moderate AI Exposure result, primarily "
            + "due to the fact that much of it can be performed digitally. This is supported by the requirement for little "
            + "physical-world presence and a lower dependency on human trust or relationships.";

    @Test
    void theLiveJavaDeveloperExplanationDescribingFactorLevelsPassesTheDirectionRule() {
        var context = liveJavaDirectionContext();
        var narrative = narrative(JAVA_LIVE_REPAIRED_EXPLANATION);

        assertThat(validator.validate(narrative, context)).isSameAs(narrative);
    }

    @Test
    void claimsThatReverseTheLiveJavaDeveloperFactorDirectionsStillFail() {
        var context = liveJavaDirectionContext();
        // Java selected lower dependency on human trust, and little physical-world presence, as raising AI Exposure.
        rejects(narrative("Much of the work can be performed digitally. Lower dependency on human trust lowers your "
                + "AI Exposure."), context, "EXPOSURE_FACTOR_DIRECTIONS_MATCH");
        rejects(narrative("Much of the work can be performed digitally. The requirement for little physical-world "
                + "presence lowers your AI Exposure."), context, "EXPOSURE_FACTOR_DIRECTIONS_MATCH");
        // Java selected the LOW levels of physical-world and human-trust dependency as raising exposure, so an
        // unqualified mention (their high state) misdescribes the factor even with the right effect.
        rejects(narrative("Physical presence raises your AI Exposure."), context, "EXPOSURE_FACTOR_LEVELS_MATCH");
        rejects(narrative("Human trust raises your AI Exposure."), context, "EXPOSURE_FACTOR_LEVELS_MATCH");
    }

    /**
     * Only what the live run supplied: Moderate AI Exposure and the three factors Java selected as raising it, with
     * their visitor-facing descriptions, signed effects and ranks. No responsibilities, skills or judgements are
     * invented; the other three measures' bands are placeholders that this rule does not read.
     */
    private static ProfessionalNarrativeContext liveJavaDirectionContext() {
        var raising = com.chikere.futureai.decision.professional.DecisionFactorEffect.INCREASES_AI_EXPOSURE;
        var measures = java.util.Arrays.stream(com.chikere.futureai.decision.professional.ProfessionalMeasure.values())
                .map(measure -> new ProfessionalNarrativeContext.MeasureContext(measure, measure.name(), 5.0, "Moderate"))
                .toList();
        return new ProfessionalNarrativeContext("Java Software Developer", "Work described.", List.of(), List.of(),
                OptionalInt.empty(), com.chikere.futureai.assessment.domain.AssessmentStatus.READY, measures,
                List.of(
                        new ProfessionalNarrativeContext.FactorContext("digital_executability", "Digital executability",
                                "Much of the work can be performed digitally", raising,
                                com.chikere.futureai.decision.professional.ProfessionalFactorDirection.HIGH_DIGITAL_EXECUTABILITY,
                                1, 0.0, 0.124375),
                        new ProfessionalNarrativeContext.FactorContext("physical_world_dependency", "Physical-world dependency",
                                "The work requires little physical-world presence", raising,
                                com.chikere.futureai.decision.professional.ProfessionalFactorDirection.LOW_PHYSICAL_WORLD_DEPENDENCY,
                                2, 0.0, 0.074625),
                        new ProfessionalNarrativeContext.FactorContext("human_trust_dependency", "Human trust dependency",
                                "The work depends less on human trust or relationships", raising,
                                com.chikere.futureai.decision.professional.ProfessionalFactorDirection.LOW_HUMAN_TRUST_DEPENDENCY,
                                3, 0.0, 0.00375)),
                List.of(), AssessmentSource.JEV, "professional-v1", "professional-decision-v1");
    }

    // ---- Management Accountant: no task-automation claims ----

    @Test
    void theLiveManagementAccountantAutomationClaimsAreRejected() {
        var context = accountantContext();
        assertThat(context.measures().getFirst().band()).isEqualTo("High");
        for (String claim : List.of("Many of your tasks can be automated.",
                "Preparing management accounts and analysing financial data can be efficiently handled by AI systems.",
                "Many aspects of your work can be automated or supported by AI.",
                "AI can perform your monthly reporting work.",
                "Budget analysis is highly automatable.")) {
            rejects(with(narrative(accountantExposure()), claim), context, "NO_UNSUPPORTED_TASK_AUTOMATION_CLAIMS");
        }
        rejects(with(narrative(accountantExposure()), "Preparing monthly management accounts has a high exposure rating."),
                context, "NO_RESPONSIBILITY_LEVEL_SCORING");
    }

    @Test
    void managementAccountantAssistanceWordingAndRoleLevelStatementsAreAccepted() {
        var context = accountantContext();
        for (String safe : List.of("AI may assist with parts of financial analysis and reporting.",
                "The assessment indicates substantial scope for AI assistance in your work.",
                "Much of the work is performed digitally, which contributes to the High AI Exposure result.",
                "AI assistance may support some reporting and analysis activities while human review and "
                        + "accountability remain important.",
                "Your work as a Management Accountant has high AI Exposure.",
                "Preparing management accounts illustrates how much of the work takes place through digital systems.",
                "This assessment does not mean that your tasks can be automated.")) {
            var narrative = with(narrative(accountantExposure()), safe);
            assertThat(validator.validate(narrative, context)).as(safe).isSameAs(narrative);
        }
    }

    @Test
    void eachLiveRoleIsGroundedInItsDominantFactorAfterTheProviderChange() {
        assertThat(DominantExposureFactor.of(new ProfessionalNarrativeContextFactory().create(stored("registered_nurse",
                "Registered Nurse", List.of("Monitor patients"))))).containsExactly("digital_executability");
        assertThat(DominantExposureFactor.of(javaContext())).containsExactly("digital_executability");
        assertThat(DominantExposureFactor.of(accountantContext())).containsExactly("digital_executability");
        rejects(with(narrative("Your work relies on accountability for decisions."), "Summary."), accountantContext(),
                "EXPOSURE_EXPLANATION");
    }

    // ---- Helpers ----

    private void rejects(ProfessionalNarrative narrative, ProfessionalNarrativeContext context, String rule) {
        assertThatThrownBy(() -> validator.validate(narrative, context)).as(narrative.summary())
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation ->
                        assertThat(violation.rule()).startsWith(rule));
    }

    private static String javaExposure() {
        return "Much of the work you described can be performed digitally, which raises your AI Exposure.";
    }

    private static String accountantExposure() {
        return "Much of the work you described takes place through digital systems, which raises your AI Exposure.";
    }

    private static ProfessionalNarrativeContext javaContext() {
        return new ProfessionalNarrativeContextFactory().create(stored("senior_software_architect",
                "Java Software Developer", List.of("Design and develop backend services", "Review pull requests",
                        "Write unit tests", "Collaborate with product teams")));
    }

    private static ProfessionalNarrativeContext accountantContext() {
        return new ProfessionalNarrativeContextFactory().create(stored("routine_software_developer",
                "Management Accountant", List.of("Prepare monthly management accounts", "Analyse financial data",
                        "Produce budget reports", "Support month-end reporting")));
    }

    /** A safe narrative with the given AI Exposure explanation. */
    private static ProfessionalNarrative narrative(String exposureExplanation) {
        return new ProfessionalNarrative("Your assessment", "Your result reflects the work you described.",
                "The measures describe the work as you described it today.", exposureExplanation,
                "The work relies on judgement, context and accountability for decisions.",
                "There may be scope for AI assistance with parts of the work, subject to suitable review.",
                "The structure of the role combines contextual work and human responsibility.",
                List.of("Review where approved tools could assist with drafting, checking each output yourself.",
                        "Keep prioritising the parts of the work that depend on your judgement.",
                        "Note the decisions you are accountable for so any assistance stays under your review."),
                "This explains a structured assessment of the work described, not a prediction.");
    }

    private static ProfessionalNarrative with(ProfessionalNarrative n, String summary) {
        return new ProfessionalNarrative(n.headline(), summary, n.whatThisMeans(), n.exposureExplanation(),
                n.humanAdvantageExplanation(), n.complementarityExplanation(), n.adaptabilityExplanation(),
                n.practicalActions(), n.cautionOrLimitations());
    }

    private static StoredProfessionalAssessment stored(String fixture, String profession, List<String> responsibilities) {
        var state = new ProfessionalAssessmentState(profession, "The work I described in my own words.",
                responsibilities, List.of("Communication"), OptionalInt.of(8));
        var judgments = new LocalFixtureProfessionalJudgmentProvider(fixture).judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        return new StoredProfessionalAssessment(UUID.nameUUIDFromBytes((fixture + profession).getBytes()),
                Instant.parse("2026-10-01T00:00:00Z"), state, judgments, new ProfessionalAssessmentResult(decision,
                        AssessmentSource.JEV, judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }

    private static ProfessionalNarrativeService service(StoredProfessionalAssessment assessment,
            ProfessionalNarrativeGenerator generator) {
        Map<UUID, StoredProfessionalNarrative> saved = new HashMap<>();
        ProfessionalAssessmentStore assessments = new ProfessionalAssessmentStore() {
            @Override public void save(StoredProfessionalAssessment value) { throw new AssertionError("not re-saved"); }
            @Override public Optional<StoredProfessionalAssessment> findById(UUID id) { return Optional.of(assessment); }
        };
        ProfessionalNarrativeStore narratives = new ProfessionalNarrativeStore() {
            @Override public void save(StoredProfessionalNarrative value) { saved.put(value.assessmentId(), value); }
            @Override public Optional<StoredProfessionalNarrative> findByAssessmentId(UUID id) {
                return Optional.ofNullable(saved.get(id));
            }
        };
        return new ProfessionalNarrativeService(assessments, narratives, generator,
                new ProfessionalNarrativeContextFactory(), new ProfessionalNarrativeValidator(),
                Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC));
    }
}
