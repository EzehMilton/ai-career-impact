package com.chikere.futureai.narrative.university.paid;

import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.HumanCapabilityInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ModuleLearningInsight;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.NarrativeRuleViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The second live PPE run on 5a12d2c: "cannot replace your judgement" on the first draft, a generic public-policy use
 * on the repair, and a module-2 entry (Logic and Critical Reasoning) written about International Relations. Every
 * rule stays strict; these are hand-written regression drafts, not production mappings. No OpenAI call.
 */
class UniversityPaidNarrativeSecondPpeRunTest {

    private final UniversityPaidNarrativeValidator validator = new UniversityPaidNarrativeValidator();
    private final ScriptedPaidNarrativeGenerator generator = new ScriptedPaidNarrativeGenerator();
    private final UniversityPaidNarrativeContext ppe = PpeNarrativeContext.secondLiveRun();

    /** Specific, correctly bound content for the four AI-learning modules. */
    private static final Map<String, ModuleLearningInsight> SPECIFIC = Map.of(
            "module-2", new ModuleLearningInsight("module-2",
                    "Logic and Critical Reasoning is about whether conclusions follow, so AI is useful as a source of "
                            + "arguments to formalise and of flaws for you to find.",
                    List.of("Translating an argument from a reading into premises and a conclusion, then asking AI "
                                    + "whether the form is valid",
                            "Asking AI for a deliberately fallacious argument and naming the fallacy before checking",
                            "Having AI propose a counterexample to an argument form you believe is valid"),
                    List.of("Judge whether an argument is valid without assistance"),
                    "Formalise one argument by hand, then ask AI to find a counterexample to its form."),
            "module-9", new ModuleLearningInsight("module-9",
                    "Public Policy asks you to build and defend a case for a course of action, so AI is most useful "
                            + "when it attacks a case you have already made.",
                    List.of("Asking AI to challenge the assumptions behind a policy argument you have already "
                                    + "constructed, then deciding which objections materially weaken it",
                            "Comparing the trade-offs of two policy options against criteria you set yourself",
                            "Having AI identify who bears the costs of a proposal you drafted"),
                    List.of("Weigh the trade-offs of a policy option and defend your recommendation"),
                    "Draft a one-paragraph policy recommendation, then ask AI for its three strongest objections."),
            "module-5", new ModuleLearningInsight("module-5",
                    "Microeconomics turns on reasoning inside a model of choices and markets, so AI is most useful as a "
                            + "sparring partner for that reasoning.",
                    List.of("Working through a market scenario yourself, then comparing your reasoning with an AI "
                                    + "walkthrough step by step",
                            "Asking AI to challenge the assumptions behind your solution to a model",
                            "Generating variations of a supply and demand problem to solve without help"),
                    List.of("Explain why a model predicts what it does, in your own words"),
                    "Solve one model problem on paper, then ask AI to find the weakest assumption in your answer."),
            "module-8", new ModuleLearningInsight("module-8",
                    "International Relations compares explanations of how states and institutions behave, so AI can "
                            + "supply rival accounts of an event for you to weigh.",
                    List.of("Setting a realist and a liberal account of one interstate dispute side by side and "
                                    + "judging which fits the evidence",
                            "Asking AI which evidence would count against your explanation of an alliance",
                            "Mapping the actors and interests in a treaty negotiation before reading commentary"),
                    List.of("Explain an international event using a theory you can defend"),
                    "Write your account of one diplomatic crisis, then ask AI for the strongest rival account."));

    @ParameterizedTest
    @ValueSource(strings = {"AI can assist but cannot replace your judgement.",
            "AI cannot replace human judgement in policy analysis.", "AI cannot replace your reasoning.",
            "AI will not replace the work of forming an argument.", "Your reasoning cannot be replaced by AI."})
    void replacementWordingIsRejected(String sentence) {
        var narrative = UniversityPaidNarrativeValidatorTest.Narratives.summary(UniversityNarratives.valid(ppe), sentence);
        assertThatThrownBy(() -> validator.validate(narrative, ppe))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation -> assertThat(violation.rule())
                        .isIn(UniversityPaidNarrativeValidator.EMPLOYMENT, UniversityPaidNarrativeValidator.AI_CERTAINTY));
        assertThatThrownBy(() -> validator.validate(UniversityPaidNarrativeValidatorTest.Narratives.summary(UniversityNarratives.valid(ppe),
                "AI can assist but cannot replace your judgement."), ppe))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation -> assertThat(violation.rule())
                        .isEqualTo(UniversityPaidNarrativeValidator.EMPLOYMENT));
    }

    @Test
    void positiveResponsibilityWordingIsAccepted() {
        var narrative = UniversityPaidNarrativeValidatorTest.Narratives.summary(UniversityNarratives.valid(ppe),
                "You are responsible for forming and defending the conclusion yourself.");
        assertThat(validator.validate(narrative, ppe)).isEqualTo(narrative);
    }

    @Test
    void specificCorrectlyBoundContentIsAccepted() {
        var narrative = learning(SPECIFIC);
        assertThat(validator.validate(narrative, ppe)).isEqualTo(narrative);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Use AI to explore different perspectives on public policy.",
            "Use AI to clarify complex concepts in public policy.",
            "Generate practice problems on public policy with AI.",
            "Ask AI to summarise key concepts in public policy."})
    void genericPublicPolicyUsesAreRejected(String use) {
        var narrative = learning(Map.of("module-9", uses(SPECIFIC.get("module-9"), use)));
        assertThatThrownBy(() -> validator.validate(narrative, ppe))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation -> {
                    assertThat(violation.rule()).isEqualTo(UniversityPaidNarrativeValidator.GENERIC);
                    assertThat(violation.getMessage()).contains("moduleLearningInsights[module-9].aiLearningUses[0] "
                            + "uses only generic study wording");
                });
    }

    @Test
    void aModule2EntryDescribingInternationalRelationsIsRejected() {
        var misbound = learning(Map.of("module-2", new ModuleLearningInsight("module-2",
                "In International Relations, AI can help you understand complex global issues and rival accounts of "
                        + "them.", SPECIFIC.get("module-2").aiLearningUses(),
                SPECIFIC.get("module-2").independentCapability(), SPECIFIC.get("module-2").studyExperiment())));
        assertThatThrownBy(() -> validator.validate(misbound, ppe))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation -> {
                    assertThat(violation.rule()).isEqualTo(UniversityPaidNarrativeValidator.BINDING);
                    assertThat(violation.getMessage()).isEqualTo("moduleLearningInsights[module-2] is bound to "
                            + "\"Logic and Critical Reasoning\" but explicitly describes \"International Relations\"");
                    assertThat(violation.triggeringText()).startsWith("In International Relations");
                });
    }

    @Test
    void aHumanCapabilityEntryNamingAnotherModuleIsRejected() {
        var draft = learning(SPECIFIC);
        var human = new ArrayList<>(draft.humanCapabilityInsights());
        var first = human.getFirst();
        human.set(0, new HumanCapabilityInsight(first.moduleId(), "Political Theory asks you to defend a position "
                + "against critique in your own words.", first.independentPractice()));
        var misbound = new UniversityPaidNarrative(draft.executiveSummary(), draft.measuresTogether(),
                draft.coursePattern(), draft.moduleLearningInsights(), human, draft.skillInsights(),
                draft.actionPlanNotes(), draft.aiStudyStrategy(), draft.closingPerspective());
        assertThatThrownBy(() -> validator.validate(misbound, ppe))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation -> assertThat(violation.getMessage())
                        .isEqualTo("humanCapabilityInsights[module-2] is bound to \"Logic and Critical Reasoning\" but "
                                + "explicitly describes \"Political Theory\""));
    }

    @Test
    void sectionsThatCompareModulesMayStillNameSeveral() {
        var narrative = UniversityPaidNarrativeValidatorTest.Narratives.summary(learning(SPECIFIC), "Logic and Critical Reasoning and International "
                + "Relations reward different kinds of argument.");
        assertThat(validator.validate(narrative, ppe)).isEqualTo(narrative);
    }

    @Test
    void aMisboundFirstDraftIsRepairedOnceWithEveryIdUnchanged() {
        var misbound = learning(Map.of("module-2", new ModuleLearningInsight("module-2",
                "In International Relations, AI can help you understand rival accounts of global events.",
                SPECIFIC.get("module-2").aiLearningUses(), SPECIFIC.get("module-2").independentCapability(),
                SPECIFIC.get("module-2").studyExperiment())));
        generator.then(misbound).then(learning(SPECIFIC));

        var outcome = service().generate(ppe);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.AVAILABLE);
        assertThat(outcome.narrative().orElseThrow().moduleLearningInsights()).extracting(ModuleLearningInsight::moduleId)
                .containsExactly("module-2", "module-9", "module-5", "module-8");
        assertThat(generator.calls()).isEqualTo(2);
        assertThat(generator.lastFeedback.rule()).isEqualTo(UniversityPaidNarrativeValidator.BINDING);
    }

    @Test
    void theLiveSequenceReplacementThenGenericIsAFinalFallbackAfterTwoCalls() {
        var replacement = UniversityPaidNarrativeValidatorTest.Narratives.summary(learning(SPECIFIC), "AI can assist but cannot replace your judgement.");
        var generic = learning(Map.of("module-9", uses(SPECIFIC.get("module-9"),
                "Use AI to explore different perspectives on public policy.")));
        generator.then(replacement).then(generic);

        var outcome = service().generate(ppe);

        assertThat(outcome.status()).isEqualTo(UniversityPaidNarrativeStatus.FALLBACK);
        assertThat(outcome.narrative()).isEmpty();
        assertThat(outcome.reason()).contains("VALIDATION_FAILED_AFTER_REPAIR:"
                + UniversityPaidNarrativeValidator.EMPLOYMENT + ">" + UniversityPaidNarrativeValidator.GENERIC);
        assertThat(generator.calls()).isEqualTo(2);
    }

    private UniversityPaidNarrativeService service() {
        return new UniversityPaidNarrativeService(() -> Optional.of(generator), validator, AssessmentTrace.disabled());
    }

    private UniversityPaidNarrative learning(Map<String, ModuleLearningInsight> replacements) {
        return UniversityPaidNarrativeGenericContentTest.learning(ppe, Map.copyOf(withSpecific(replacements)));
    }

    /** The specific content everywhere, with the given entries in place. */
    private static Map<String, ModuleLearningInsight> withSpecific(Map<String, ModuleLearningInsight> replacements) {
        var all = new java.util.HashMap<>(SPECIFIC);
        all.putAll(replacements);
        return all;
    }

    private static ModuleLearningInsight uses(ModuleLearningInsight insight, String firstUse) {
        List<String> uses = new ArrayList<>(insight.aiLearningUses());
        uses.set(0, firstUse);
        return new ModuleLearningInsight(insight.moduleId(), insight.explanation(), uses,
                insight.independentCapability(), insight.studyExperiment());
    }
}
