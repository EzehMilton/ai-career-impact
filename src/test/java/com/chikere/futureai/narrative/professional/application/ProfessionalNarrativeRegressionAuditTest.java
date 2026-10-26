package com.chikere.futureai.narrative.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.DecisionFactorEffect;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.fixture.LocalFixtureProfessionalNarrativeGenerator;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssembler;
import com.chikere.futureai.report.professional.application.ProfessionalReportSource;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.report.professional.web.ProfessionalReportViewMapper;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceLogCapture;
import com.chikere.futureai.trace.TraceStage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 1 regression audit, across every Professional fixture: the original Registered Nurse failures cannot be
 * accepted, the measures keep their meanings, disclaimers are not mistaken for claims, and a rejected narrative
 * leaves the deterministic assessment intact behind the existing fallback.
 */
class ProfessionalNarrativeRegressionAuditTest {

    private static final String NURSE_FORECAST =
            "Current AI developments are not expected to fundamentally alter the core responsibilities of your role.";
    private static final String NURSE_CAPABILITY =
            "These elements of care are deeply human and cannot be easily replicated by AI.";
    private static final String NURSE_ADAPTABILITY = "The nurse's ability to adjust to changing circumstances, like "
            + "emergencies or varying patient needs, further illustrates this adaptability.";

    private final ProfessionalNarrativeValidator validator = new ProfessionalNarrativeValidator();

    @Test
    void theDeterministicFixtureNeverGeneratesTheOriginalFailuresAndEveryFixtureNarrativeIsAccepted() {
        var generator = new LocalFixtureProfessionalNarrativeGenerator();
        for (String fixture : fixtures()) {
            var context = context(fixture);
            var narrative = generator.generate(context);
            String all = narrative.toString();
            assertThat(all).as(fixture).doesNotContain("not expected to", "cannot be easily replicated", "ability to adjust");
            assertThat(validator.validate(narrative, context)).as(fixture).isSameAs(narrative);
        }
    }

    @Test
    void theThreeRegisteredNurseFailuresAreRejectedForEveryFixtureByTheirOwnRule() {
        for (String fixture : fixtures()) {
            var context = context(fixture);
            var safe = grounded(context);
            rejects(fixture, with(safe, "whatThisMeans", NURSE_FORECAST), context, "not forecast its future");
            rejects(fixture, with(safe, "humanAdvantage", NURSE_CAPABILITY), context, "what AI can or cannot do");
            rejects(fixture, with(safe, "adaptability", NURSE_ADAPTABILITY), context, "structure of the role, not the person");
        }
    }

    @Test
    void eachMeasureKeepsItsMeaningForEveryFixture() {
        for (String fixture : fixtures()) {
            var context = context(fixture);
            var safe = grounded(context);
            assertThat(validator.validate(safe, context)).as(fixture).isSameAs(safe);
            // AI Exposure is neither job-loss probability nor future job security.
            rejects(fixture, with(safe, "exposure", safe.exposureExplanation()
                    + " This means there is a low risk of losing your job."), context, "job loss, replacement or job security");
            rejects(fixture, with(safe, "summary", "Your job is secure from automation."), context,
                    "not forecast its future");
            // Human Advantage is not proof that AI cannot do the work.
            rejects(fixture, with(safe, "humanAdvantage", "AI is unable to do the human parts of your work."),
                    context, "what AI can or cannot do");
            // Role Adaptability describes the role, not the person's personality, motivation, resilience or learning.
            for (String personal : List.of("Your motivation drives this result.", "Your resilience explains the result.",
                    "Your willingness to learn makes the role adaptable.")) {
                rejects(fixture, with(safe, "adaptability", personal), context, "structure of the role, not the person");
            }
            // The AI Exposure explanation must follow Java's factors, in Java's direction.
            rejects(fixture, with(safe, "exposure", contradiction(context)), context, "must not reverse the direction");
            rejects(fixture, with(safe, "exposure", "Your result reflects the overall profile of your role."), context,
                    "explain at least one of the factors Java selected");
        }
        // Complementarity's independence from Exposure is part of the prompt contract (it is not a phrase to reject).
        assertThat(com.chikere.futureai.narrative.professional.openai.OpenAiProfessionalNarrativeGenerator.SYSTEM_INSTRUCTIONS
                .replaceAll("\\s+", " ")).contains("It is distinct from AI Exposure");
    }

    @Test
    void presentationRulesHoldForEveryFixture() {
        for (String fixture : fixtures()) {
            var context = context(fixture);
            var safe = grounded(context);
            assertThat(context.measures()).allSatisfy(measure -> assertThat(measure.band()).isIn("Low", "Moderate", "High"));
            assertThat(context.responsibilities()).containsExactly("Monitor patients", "Respond to deterioration");
            rejects(fixture, with(safe, "summary", "Your AI Exposure is " + context.measures().getFirst().band()
                    .toUpperCase(Locale.ROOT) + "."), context, "natural band words");
            rejects(fixture, with(safe, "summary", "Organize your week around the work the assessment highlights."),
                    context, "British English");
        }
    }

    @Test
    void disclaimersWithDangerousWordsAreAcceptedForEveryFixture() {
        for (String fixture : fixtures()) {
            var context = context(fixture);
            var safe = grounded(context);
            for (String disclaimer : List.of("This assessment does not predict job loss.",
                    "The result does not mean AI cannot assist with parts of your work.",
                    "AI Exposure is not a probability that you will lose your job.",
                    "This assessment does not predict how your role will change in future.")) {
                var narrative = with(safe, "caution", disclaimer);
                assertThat(validator.validate(narrative, context)).as(fixture + ": " + disclaimer).isSameAs(narrative);
            }
            var adaptability = with(safe, "adaptability",
                    "Role Adaptability does not measure your personal ability to adapt.");
            assertThat(validator.validate(adaptability, context)).as(fixture).isSameAs(adaptability);
        }
    }

    @Test
    void aRejectedNarrativeKeepsTheScoresUsesTheFallbackNeverRendersTheProseAndIsTraced() throws Exception {
        var assessment = stored("registered_nurse");
        Map<UUID, StoredProfessionalNarrative> narratives = new HashMap<>();
        ProfessionalAssessmentStore assessments = new ProfessionalAssessmentStore() {
            @Override public void save(StoredProfessionalAssessment value) {
                throw new AssertionError("the assessment must never be saved again");
            }
            @Override public Optional<StoredProfessionalAssessment> findById(UUID id) {
                return Optional.of(assessment);
            }
        };
        ProfessionalNarrativeStore narrativeStore = new ProfessionalNarrativeStore() {
            @Override public void save(StoredProfessionalNarrative value) { narratives.put(value.assessmentId(), value); }
            @Override public Optional<StoredProfessionalNarrative> findByAssessmentId(UUID id) {
                return Optional.ofNullable(narratives.get(id));
            }
        };
        var unsafe = with(grounded(context("registered_nurse")), "adaptability", NURSE_ADAPTABILITY);
        ProfessionalNarrativeGenerator generator = new ProfessionalNarrativeGenerator() {
            @Override public ProfessionalNarrative generate(ProfessionalNarrativeContext context) { return unsafe; }
            @Override public NarrativeSource source() { return NarrativeSource.OPENAI; }
            @Override public String providerIdentifier() { return "openai"; }
        };
        var trace = AssessmentTrace.enabled();

        try (TraceLogCapture log = TraceLogCapture.start()) {
            var stored = new ProfessionalNarrativeService(assessments, narrativeStore, generator,
                    new ProfessionalNarrativeContextFactory(), new ProfessionalNarrativeValidator(trace),
                    Clock.fixed(Instant.parse("2026-10-01T00:00:00Z"), ZoneOffset.UTC),
                    com.chikere.futureai.configuration.JourneyMetrics.disabled(), trace).generateFor(assessment.id());

            assertThat(stored.status()).isEqualTo(NarrativeStatus.FAILED);
            assertThat(stored.narrative()).isEmpty();
            // The single repair attempt (this generator regenerates the same draft) is rejected too, then fallback.
            var validations = log.events(TraceStage.NARRATIVE_VALIDATION);
            assertThat(validations).hasSize(2).allSatisfy(validation -> {
                assertThat(validation.at("/payload/rejectedBy").asText()).isEqualTo("ROLE_ADAPTABILITY_IS_ROLE_LEVEL");
                assertThat(validation.at("/payload/rules/7/triggeringText").asText()).isEqualTo(NURSE_ADAPTABILITY);
            });
            assertThat(log.only(TraceStage.NARRATIVE_OUTCOME).at("/payload/status").asText()).isEqualTo("FAILED");
        }

        var source = new ProfessionalReportSource(assessment, narrativeStore.findByAssessmentId(assessment.id()),
                Optional.empty(), Optional.empty());
        for (ProfessionalReportTier tier : ProfessionalReportTier.values()) {
            var report = new ProfessionalReportAssembler().assemble(source, tier);
            assertThat(report.measures()).extracting(measure -> measure.indicator()).containsExactlyElementsOf(
                    assessment.result().decision().measures().values().stream().map(value -> value.indicator()).toList());
            assertThat(report.narrative().available()).as("the page shows its fallback").isFalse();
            var view = new ProfessionalReportViewMapper().toView(report);
            assertThat(view.narrative().available()).isFalse();
            String rendered = new ObjectMapper().findAndRegisterModules().writeValueAsString(view);
            assertThat(rendered).doesNotContain("ability to adjust", unsafe.summary(), unsafe.exposureExplanation());
        }
    }

    // ---- Helpers ----

    private void rejects(String fixture, ProfessionalNarrative narrative, ProfessionalNarrativeContext context,
            String reason) {
        assertThatThrownBy(() -> validator.validate(narrative, context)).as(fixture + ": " + narrative)
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(reason);
    }

    private static List<String> fixtures() {
        // registered_nurse, senior_software_architect, routine_software_developer and electrician.
        return LocalFixtureProfessionalJudgmentProvider.supportedFixtureIds();
    }

    /** A safe narrative whose AI Exposure explanation names, in plain language, the dominant factor Java selected. */
    private static ProfessionalNarrative grounded(ProfessionalNarrativeContext context) {
        var factor = dominant(context);
        String verb = factor.effect() == DecisionFactorEffect.INCREASES_AI_EXPOSURE ? "raises" : "lowers";
        return new ProfessionalNarrative("Your assessment", "Your result reflects the work you described.",
                "The measures describe the work as you described it today.",
                "In the work you described, " + plain(factor) + " " + verb + " your AI Exposure.",
                "The work relies on trust, physical presence and accountable decisions.",
                "There may be scope for assistance with some tasks while you stay responsible for the outcome.",
                "The structure of the role combines contextual work and human responsibility.",
                List.of("Review tasks that could be drafted with assistance, checking each yourself.",
                        "Keep prioritising the work the assessment highlights.",
                        "Note decisions you are accountable for."),
                "This explains a structured assessment of the work described, not a prediction.");
    }

    /** The same dominant factor, claimed in the opposite direction. */
    private static String contradiction(ProfessionalNarrativeContext context) {
        var factor = dominant(context);
        String verb = factor.effect() == DecisionFactorEffect.INCREASES_AI_EXPOSURE ? "lowers" : "raises";
        return "In the work you described, " + plain(factor) + " " + verb + " your AI Exposure.";
    }

    private static ProfessionalNarrativeContext.FactorContext dominant(ProfessionalNarrativeContext context) {
        String id = DominantExposureFactor.of(context).getFirst();
        return java.util.stream.Stream.concat(context.exposureDrivers().stream(), context.protectiveFactors().stream())
                .filter(factor -> factor.dimensionId().equals(id)).findFirst().orElseThrow();
    }

    /** Plain wording for the factor at the level Java selected: "limited physical presence" for a low selection. */
    private static String plain(ProfessionalNarrativeContext.FactorContext factor) {
        return factor.direction().name().startsWith("LOW_")
                ? PLAIN_LOW.get(factor.dimensionId()) : PLAIN.get(factor.dimensionId());
    }

    private static final Map<String, String> PLAIN_LOW = Map.of(
            "task_repeatability", "limited routine work",
            "digital_executability", "limited digital work",
            "human_trust_dependency", "limited reliance on trust and relationships",
            "physical_world_dependency", "limited physical presence",
            "contextual_complexity", "predictable situations",
            "original_judgment", "limited original judgement",
            "accountability_responsibility", "limited accountability for decisions");

    /** Plain wording for each factor's high state, as the visitor-facing narrative should use. */
    private static final Map<String, String> PLAIN = Map.of(
            "task_repeatability", "routine work",
            "digital_executability", "digital work",
            "human_trust_dependency", "the importance of trust and relationships",
            "physical_world_dependency", "physical presence",
            "contextual_complexity", "changing situations",
            "original_judgment", "original judgement",
            "accountability_responsibility", "accountability for decisions");

    private static ProfessionalNarrative with(ProfessionalNarrative n, String field, String text) {
        return new ProfessionalNarrative(n.headline(),
                field.equals("summary") ? text : n.summary(),
                field.equals("whatThisMeans") ? text : n.whatThisMeans(),
                field.equals("exposure") ? text : n.exposureExplanation(),
                field.equals("humanAdvantage") ? text : n.humanAdvantageExplanation(),
                n.complementarityExplanation(),
                field.equals("adaptability") ? text : n.adaptabilityExplanation(),
                n.practicalActions(),
                field.equals("caution") ? text : n.cautionOrLimitations());
    }

    private static ProfessionalNarrativeContext context(String fixture) {
        return new ProfessionalNarrativeContextFactory().create(stored(fixture));
    }

    private static StoredProfessionalAssessment stored(String fixture) {
        var state = new ProfessionalAssessmentState("Registered Nurse", "I care for patients on a busy ward.",
                List.of("Monitor patients", "Respond to deterioration"), List.of("Clinical observation"),
                OptionalInt.of(9));
        var judgments = new LocalFixtureProfessionalJudgmentProvider(fixture).judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        return new StoredProfessionalAssessment(UUID.nameUUIDFromBytes(fixture.getBytes()),
                Instant.parse("2026-10-01T00:00:00Z"), state, judgments, new ProfessionalAssessmentResult(decision,
                        AssessmentSource.JEV, judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }
}
