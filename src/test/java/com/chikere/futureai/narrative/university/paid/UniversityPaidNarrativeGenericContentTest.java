package com.chikere.futureai.narrative.university.paid;

import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ModuleLearningInsight;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleViolation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The live PPE failure after the id fix: generic module content rejected on both attempts. The rule stays strict;
 * these are hand-written regression drafts, not production mappings. No OpenAI call.
 */
class UniversityPaidNarrativeGenericContentTest {

    private static final String GENERIC = UniversityPaidNarrativeValidator.GENERIC;

    private final UniversityPaidNarrativeValidator validator = new UniversityPaidNarrativeValidator();
    private final ScriptedPaidNarrativeGenerator generator = new ScriptedPaidNarrativeGenerator();
    private final UniversityPaidNarrativeContext ppe = PpeNarrativeContext.context();
    private final UniversityPaidNarrativeContext economics = PpeNarrativeContext.withMacroeconomics();

    /** Materially different economics, philosophy and politics content: what the prompt now asks for. */
    private static final Map<String, ModuleLearningInsight> DIFFERENTIATED = Map.of(
            "module-5", new ModuleLearningInsight("module-5",
                    "Microeconomics turns on reasoning inside a model of choices and markets, so AI is most useful as a "
                            + "sparring partner for that reasoning, not as a source of answers.",
                    List.of("Working through a market scenario yourself, then comparing your reasoning with an AI "
                                    + "walkthrough step by step",
                            "Asking AI to challenge the assumptions behind your solution to a model",
                            "Generating variations of a supply and demand problem to solve without help"),
                    List.of("Explain why a model predicts what it does, in your own words"),
                    "Solve one model problem on paper, then ask AI to find the weakest assumption in your answer."),
            "module-4", new ModuleLearningInsight("module-4",
                    "Macroeconomics asks you to connect indicators across a whole economy, so AI can help you test "
                            + "whether your account of a change hangs together.",
                    List.of("Comparing two explanations of a change in aggregate conditions and judging which fits",
                            "Having AI question your account of how inflation, output and unemployment relate",
                            "Practising your interpretation of a published data series or policy scenario"),
                    List.of("Interpret a chart of economic indicators before reading any commentary"),
                    "Write a short account of a recent policy scenario, then ask AI for the strongest rival account."),
            "module-1", new ModuleLearningInsight("module-1",
                    "Introduction to Philosophy rewards reconstructing and testing arguments, so AI is a useful source "
                            + "of objections for you to answer.",
                    List.of("Reconstructing an argument as numbered premises and a conclusion, then checking it",
                            "Asking AI for objections to an argument and answering each in writing",
                            "Testing whether your conclusion really follows from the premises you stated"),
                    List.of("Identify the premises of an argument without assistance"),
                    "Set out one argument from a seminar, then ask AI which premise a critic would attack first."),
            "module-3", new ModuleLearningInsight("module-3",
                    "Political Theory weighs normative positions against each other, so AI can supply positions to "
                            + "contrast while you judge their consistency.",
                    List.of("Contrasting two normative positions on liberty and equality and stating where they part",
                            "Identifying the assumptions a theoretical position makes about citizens and the state",
                            "Defending a position you hold against counterarguments that AI raises"),
                    List.of("Defend a normative claim and its consistency in a seminar"),
                    "State your view on one political principle, then ask AI for the best case against it."));

    @Test
    void differentiatedEconomicsPhilosophyAndPoliticsContentIsAccepted() {
        var narrative = learning(economics, DIFFERENTIATED);
        assertThat(validator.validate(narrative, economics)).isEqualTo(narrative);
    }

    @Test
    void anExplanationCopyingTheGenericFallbackIsRejectedWithItsFieldAndTheFallback() {
        // Live attempt 2, Microeconomics.
        String copied = "AI tools could support your learning in Microeconomics by explaining concepts, generating "
                + "practice problems and helping you check your own working.";
        assertGeneric(learning(economics, Map.of("module-5", with(DIFFERENTIATED.get("module-5"), copied, null))),
                economics, "moduleLearningInsights[module-5].explanation restates the generic fallback guidance",
                "Compared with: \"AI tools could support your learning in Microeconomics by explaining concepts");
    }

    @Test
    void theGenericTemplateAsAUseIsRejectedEvenForOneModule() {
        assertGeneric(learning(economics, Map.of("module-5", with(DIFFERENTIATED.get("module-5"), null, List.of(
                        "Explaining concepts, generating practice problems and checking your working",
                        "Working through a market scenario yourself, then comparing your reasoning with AI")))),
                economics, "moduleLearningInsights[module-5].aiLearningUses[0] uses only generic study wording");
        assertGeneric(learning(economics, Map.of("module-5", with(DIFFERENTIATED.get("module-5"),
                        "AI can help you with Microeconomics by explaining key concepts and summarising readings.",
                        null))),
                economics, "moduleLearningInsights[module-5].explanation uses only generic study wording");
    }

    @Test
    void microAndMacroeconomicsWithOnlyTheModuleNameChangedAreRejected() {
        List<String> template = List.of("Explaining %s concepts in a different way",
                "Generating %s practice problems to solve", "Checking your %s working with AI");
        assertGeneric(learning(economics, Map.of(
                        "module-5", with(DIFFERENTIATED.get("module-5"), null, filled(template, "Microeconomics")),
                        "module-4", with(DIFFERENTIATED.get("module-4"), null, filled(template, "Macroeconomics")))),
                economics, "moduleLearningInsights[module-4].aiLearningUses is too similar to "
                        + "moduleLearningInsights[module-5].aiLearningUses");
    }

    @Test
    void philosophyAndPoliticalTheoryWithTheSameReadingTemplateAreRejected() {
        // Live attempt 1, Political Theory, beside the same shape for Introduction to Philosophy.
        assertGeneric(learning(ppe, Map.of(
                        "module-3", with(DIFFERENTIATED.get("module-3"), null, List.of(
                                "Request AI to summarise key political theories and concepts.",
                                "Explore different interpretations of political texts with AI support.",
                                "Use AI to generate questions for discussion in Political Theory.")),
                        "module-1", with(DIFFERENTIATED.get("module-1"), null, List.of(
                                "Request AI to summarise key philosophical theories and concepts.",
                                "Explore different interpretations of philosophical texts with AI support.",
                                "Use AI to generate questions for discussion in Introduction to Philosophy.")))),
                ppe, "moduleLearningInsights[module-1].aiLearningUses is too similar to "
                        + "moduleLearningInsights[module-3].aiLearningUses");
    }

    @Test
    void aGenericFailureIsRepairedOnceWithItsFieldAndTheIdsStayExact() {
        var copied = learning(economics, Map.of("module-5", with(DIFFERENTIATED.get("module-5"),
                "AI tools could support your learning in Microeconomics by explaining concepts, generating practice "
                        + "problems and helping you check your own working.", null)));
        generator.then(copied).then(learning(economics, DIFFERENTIATED));

        var outcome = service().generate(economics);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.AVAILABLE);
        assertThat(outcome.narrative().orElseThrow().moduleLearningInsights()).extracting(ModuleLearningInsight::moduleId)
                .containsExactly("module-5", "module-4", "module-1", "module-3");
        assertThat(generator.calls()).isEqualTo(2);
        assertThat(generator.lastFeedback.rule()).isEqualTo(GENERIC);
        assertThat(generator.lastFeedback.reason()).contains("moduleLearningInsights[module-5].explanation");
        assertThat(generator.lastFeedback.rejectedDraft()).contains(copied);
    }

    @Test
    void genericContentTwiceIsAFinalFallbackWithNoThirdCall() {
        var copied = learning(economics, Map.of("module-5", with(DIFFERENTIATED.get("module-5"),
                "AI tools could support your learning in Microeconomics by explaining concepts, generating practice "
                        + "problems and helping you check your own working.", null)));
        generator.then(copied).then(copied);

        var outcome = service().generate(economics);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.FALLBACK);
        assertThat(outcome.narrative()).isEmpty();
        assertThat(outcome.reason()).contains("VALIDATION_FAILED_AFTER_REPAIR:" + GENERIC + ">" + GENERIC);
        assertThat(generator.calls()).isEqualTo(2);
    }

    private UniversityPaidNarrativeService service() {
        return new UniversityPaidNarrativeService(() -> Optional.of(generator), validator, AssessmentTrace.disabled());
    }

    private void assertGeneric(UniversityPaidNarrative narrative, UniversityPaidNarrativeContext context,
            String... reason) {
        assertThatThrownBy(() -> validator.validate(narrative, context))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation -> {
                    assertThat(violation.rule()).isEqualTo(GENERIC);
                    assertThat(violation.getMessage()).contains(reason);
                });
    }

    /** A valid draft for the context with the given module-learning entries in place, every id in order. */
    static UniversityPaidNarrative learning(UniversityPaidNarrativeContext context,
            Map<String, ModuleLearningInsight> replacements) {
        var draft = UniversityNarratives.valid(context);
        List<ModuleLearningInsight> learning = new ArrayList<>();
        for (var insight : draft.moduleLearningInsights()) {
            learning.add(replacements.getOrDefault(insight.moduleId(), insight));
        }
        return new UniversityPaidNarrative(draft.executiveSummary(), draft.measuresTogether(), draft.coursePattern(),
                learning, draft.humanCapabilityInsights(), draft.skillInsights(), draft.actionPlanNotes(),
                draft.aiStudyStrategy(), draft.closingPerspective());
    }

    private static ModuleLearningInsight with(ModuleLearningInsight insight, String explanation, List<String> uses) {
        return new ModuleLearningInsight(insight.moduleId(), explanation == null ? insight.explanation() : explanation,
                uses == null ? insight.aiLearningUses() : uses, insight.independentCapability(),
                insight.studyExperiment());
    }

    private static List<String> filled(List<String> template, String title) {
        return template.stream().map(use -> use.formatted(title)).toList();
    }
}
