package com.chikere.futureai.narrative.university.paid;

import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ActionPlanNote;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.AiStudyStrategy;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.HumanCapabilityInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.ModuleLearningInsight;
import com.chikere.futureai.narrative.university.paid.UniversityPaidNarrative.SkillInsight;
import com.chikere.futureai.report.university.detailed.UniversityCalibrationChains;
import com.chikere.futureai.trace.NarrativeRuleViolation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The university-paid-narrative-v2 guardrail on the Nursing and Computer Science calibration contexts. No provider. */
class UniversityPaidNarrativeValidatorTest {

    private final UniversityPaidNarrativeValidator validator = new UniversityPaidNarrativeValidator();
    static final UniversityPaidNarrativeContext NURSING = UniversityCalibrationChains.chain(
            UniversityCalibrationChains.NURSING).context();
    static final UniversityPaidNarrativeContext COMPUTER_SCIENCE = UniversityCalibrationChains.chain(
            UniversityCalibrationChains.COMPUTER_SCIENCE).context();

    static UniversityPaidNarrativeContext context() {
        return NURSING;
    }

    static UniversityPaidNarrative clean() {
        return UniversityNarratives.valid(NURSING);
    }

    @Test
    void aValidNarrativeIsAcceptedUnchangedForEitherCourse() {
        assertThat(validator.validate(clean(), NURSING)).isEqualTo(clean());
        var computing = UniversityNarratives.valid(COMPUTER_SCIENCE);
        assertThat(validator.validate(computing, COMPUTER_SCIENCE)).isEqualTo(computing);
        assertThat(NURSING.moduleLearningIds()).isNotEmpty().allMatch(id -> id.matches("module-\\d+"));
        assertThat(NURSING.programmingVocabularySupported()).isFalse();
        assertThat(COMPUTER_SCIENCE.programmingVocabularySupported()).isTrue();
    }

    @Test
    void aMissingNarrativeIsNotRepairableButAMissingFieldIs() {
        assertRule(null, UniversityPaidNarrativeValidator.PRESENT);
        assertRule(new UniversityPaidNarrative(" ", null, null, null, null, null, null, null, ""),
                UniversityPaidNarrativeValidator.PRESENT);
        var base = clean();
        assertRule(new UniversityPaidNarrative(base.executiveSummary(), base.measuresTogether(), " ",
                base.moduleLearningInsights(), base.humanCapabilityInsights(), base.skillInsights(),
                base.actionPlanNotes(), base.aiStudyStrategy(), base.closingPerspective()),
                UniversityPaidNarrativeValidator.WELL_FORMED);
        assertThat(UniversityPaidNarrativeValidator.REPAIRABLE).doesNotContain(UniversityPaidNarrativeValidator.PRESENT)
                .hasSize(UniversityPaidNarrativeValidator.RULES.size() - 1);
    }

    @Test
    void missingExtraReorderedOrInventedModuleSkillAndActionIdsAreRejected() {
        var base = clean();
        var learning = new ArrayList<>(base.moduleLearningInsights());
        Collections.reverse(learning);
        assertRule(learning(learning), UniversityPaidNarrativeValidator.IDS);
        assertRule(learning(base.moduleLearningInsights().subList(1, base.moduleLearningInsights().size())),
                UniversityPaidNarrativeValidator.IDS);
        var invented = new ArrayList<>(base.moduleLearningInsights());
        var first = invented.getFirst();
        invented.set(0, new ModuleLearningInsight("module-99", first.explanation(), first.aiLearningUses(),
                first.independentCapability(), first.studyExperiment()));
        assertRule(learning(invented), UniversityPaidNarrativeValidator.IDS);
        var skills = new ArrayList<>(base.skillInsights());
        Collections.reverse(skills);
        assertRule(with(base, null, null, skills, null, null), UniversityPaidNarrativeValidator.IDS);
        assertRule(with(base, null, null, null, base.actionPlanNotes().subList(1, base.actionPlanNotes().size()), null),
                UniversityPaidNarrativeValidator.IDS);
    }

    @Test
    void listsAreHeldToTheirSizesAndLengths() {
        var base = clean();
        var learning = new ArrayList<>(base.moduleLearningInsights());
        var first = learning.getFirst();
        learning.set(0, new ModuleLearningInsight(first.moduleId(), first.explanation(),
                List.of(first.aiLearningUses().getFirst()), first.independentCapability(), first.studyExperiment()));
        assertRule(learning(learning), UniversityPaidNarrativeValidator.LENGTH);
        learning.set(0, new ModuleLearningInsight(first.moduleId(), first.explanation(), first.aiLearningUses(),
                first.independentCapability(), "x".repeat(UniversityPaidNarrativeValidator.MAX_EXPERIMENT + 1)));
        assertRule(learning(learning), UniversityPaidNarrativeValidator.LENGTH);
        var strategy = base.aiStudyStrategy();
        assertRule(with(base, null, null, null, null, new AiStudyStrategy(List.of(), strategy.learnWithoutAi(),
                strategy.verify(), strategy.demonstrateOwnCapability(), strategy.buildEvidence())),
                UniversityPaidNarrativeValidator.LENGTH);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Your Working Alongside AI score is 6.2 out of ten.|NO_NUMBERS_OR_SCORES",
            "Graduates of this course earn a good salary.|NO_SALARY_OR_PAY",
            "This course will get you a job in the NHS.|NO_EMPLOYMENT_OR_REPLACEMENT_PREDICTION",
            "A nursing degree guarantees a stable career.|NO_EMPLOYMENT_OR_REPLACEMENT_PREDICTION",
            "Your clinical judgement makes you indispensable.|NO_EMPLOYMENT_OR_REPLACEMENT_PREDICTION",
            "Nursing roles will never be replaced; AI cannot care for patients.|NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO",
            "This course is AI-proof.|NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO",
            "This course is future-proof.|NO_CLAIM_ABOUT_WHAT_AI_CANNOT_DO",
            "You are allowed to use AI in your coursework.|NO_AI_PERMISSION_CLAIM",
            "Your university allows AI for drafting essays.|NO_INVENTED_INSTITUTIONAL_RULES",
            "AI tools are banned in assessments.|NO_INVENTED_INSTITUTIONAL_RULES",
            "Unacknowledged AI use will be flagged by your tutors.|NO_INVENTED_INSTITUTIONAL_RULES",
            "Your Future Readiness is strong.|ONLY_THE_THREE_COURSE_PROFILE_MEASURES",
            "Human and Practical Capability is your strongest measure.|NO_OVERALL_SCORE_OR_RANKING",
            "Anatomy is more important than any other module.|NO_OVERALL_SCORE_OR_RANKING",
            "Try ChatGPT for revision questions.|NO_NAMED_PRODUCTS_OR_VENDORS",
            "Flashcards in Quizlet suit this course.|NO_NAMED_PRODUCTS_OR_VENDORS"})
    void rejectsEachForbiddenClaim(String sentence, String rule) {
        assertRule(summary(sentence), rule);
    }

    @Test
    void dataRedundancyIsDatabaseVocabularyButBeingMadeRedundantIsAnEmploymentClaim() {
        var computing = UniversityNarratives.valid(COMPUTER_SCIENCE);
        var schema = Narratives.summary(computing, "Critiquing a schema for redundancy and normalisation problems helps.");
        assertThat(validator.validate(schema, COMPUTER_SCIENCE)).isEqualTo(schema);
        assertRule(summary("Nurses are unlikely to be made redundant."),
                UniversityPaidNarrativeValidator.EMPLOYMENT);
    }

    @Test
    void aBandIsOnlyEverTheStoredOne() {
        String band = NURSING.measures().stream().filter(measure -> measure.name().equals("Transferable Capability"))
                .findFirst().orElseThrow().band();
        String other = band.equals("HIGH") ? "low" : "high";
        assertRule(summary("Your course shows " + other + " Transferable Capability."),
                UniversityPaidNarrativeValidator.BANDS);
        assertThat(validator.validate(summary("Your course shows " + band.toLowerCase() + " Transferable Capability."),
                NURSING)).isNotNull();
    }

    @Test
    void regulationIsMentionedOnlyWhenProfessionalStandardsAreRelevant() {
        assertThat(NURSING.professionalStandardsRelevant()).isTrue();
        var regulated = summary("Because nursing is regulated, professional standards shape much of the course.");
        assertThat(validator.validate(regulated, NURSING)).isEqualTo(regulated);
        assertThat(COMPUTER_SCIENCE.professionalStandardsRelevant()).isFalse();
        assertThatThrownBy(() -> validator.validate(Narratives.summary(UniversityNarratives.valid(COMPUTER_SCIENCE),
                "Computing is a regulated profession."), COMPUTER_SCIENCE))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation ->
                        assertThat(violation.rule()).isEqualTo(UniversityPaidNarrativeValidator.REGULATION));
    }

    @Test
    void aModuleDescribedWithAnotherClassificationIsRejected() {
        var base = clean();
        var learning = new ArrayList<>(base.moduleLearningInsights());
        var first = learning.getFirst();
        String other = NURSING.aiLearningModules().getFirst().classification().equals("Mainly practical or experiential")
                ? "strong technical or quantitative core" : "mainly practical or experiential";
        learning.set(0, new ModuleLearningInsight(first.moduleId(), "This module is " + other + ", so practise it "
                + "with AI-supported rehearsal.", first.aiLearningUses(), first.independentCapability(),
                first.studyExperiment()));
        assertRule(learning(learning), UniversityPaidNarrativeValidator.RECLASSIFICATION);
    }

    @Test
    void programmingVocabularyIsRejectedForNursingButFitsComputerScience() {
        assertRule(summary("Check any AI-generated code in Anatomy and Physiology yourself."),
                UniversityPaidNarrativeValidator.VOCABULARY);
        assertRule(summary("Debugging your understanding of drug interactions helps."),
                UniversityPaidNarrativeValidator.VOCABULARY);
        // The Code and a code of conduct are professional standards, not programming.
        var standards = summary("Read the Code and your code of conduct alongside AI summaries of them.");
        assertThat(validator.validate(standards, NURSING)).isEqualTo(standards);
        var computing = Narratives.summary(UniversityNarratives.valid(COMPUTER_SCIENCE),
                "Checking AI-generated code and SQL queries against the requirements is part of the work.");
        assertThat(validator.validate(computing, COMPUTER_SCIENCE)).isEqualTo(computing);
    }

    @Test
    void identicalOrNearIdenticalModuleUsesAndGenericTemplateCopyAreRejected() {
        var base = clean();
        var learning = new ArrayList<>(base.moduleLearningInsights());
        var first = learning.getFirst();
        var second = learning.get(1);
        // The same two uses with only the module name changed: the template the paid report must leave behind.
        String firstTitle = NURSING.aiLearningModules().getFirst().module();
        String secondTitle = NURSING.aiLearningModules().get(1).module();
        learning.set(1, new ModuleLearningInsight(second.moduleId(), second.explanation(),
                first.aiLearningUses().stream().map(use -> use.replace(firstTitle, secondTitle)).toList(),
                second.independentCapability(), second.studyExperiment()));
        assertRule(learning(learning), UniversityPaidNarrativeValidator.GENERIC);

        learning = new ArrayList<>(base.moduleLearningInsights());
        learning.set(0, new ModuleLearningInsight(first.moduleId(), first.explanation(),
                List.of("AI could support your learning by explaining concepts, generating practice problems and "
                        + "helping you check your own working", first.aiLearningUses().get(1)),
                first.independentCapability(), first.studyExperiment()));
        assertRule(learning(learning), UniversityPaidNarrativeValidator.GENERIC);

        learning = new ArrayList<>(base.moduleLearningInsights());
        learning.set(1, new ModuleLearningInsight(second.moduleId(), second.explanation(), second.aiLearningUses(),
                second.independentCapability(), first.studyExperiment()));
        assertRule(learning(learning), UniversityPaidNarrativeValidator.GENERIC);
    }

    @Test
    void anExplanationOrNoteThatOnlyRestatesTheFixedGuidanceIsRejected() {
        var base = clean();
        var module = NURSING.aiLearningModules().getFirst();
        var learning = new ArrayList<>(base.moduleLearningInsights());
        var first = learning.getFirst();
        learning.set(0, new ModuleLearningInsight(first.moduleId(), module.currentGuidance(), first.aiLearningUses(),
                first.independentCapability(), first.studyExperiment()));
        assertRule(learning(learning), UniversityPaidNarrativeValidator.GENERIC);

        var own = NURSING.ownCapabilityModules().getFirst();
        var capability = new ArrayList<>(base.humanCapabilityInsights());
        capability.set(0, new HumanCapabilityInsight(own.id(), own.module() + ": " + own.classification() + ".",
                capability.getFirst().independentPractice()));
        assertRule(with(base, null, capability, null, null, null), UniversityPaidNarrativeValidator.GENERIC);

        var action = NURSING.actionPlan().getFirst().actions().getFirst();
        var notes = new ArrayList<>(base.actionPlanNotes());
        notes.set(0, new ActionPlanNote(action.id(), action.action()));
        assertRule(with(base, null, null, null, notes, null), UniversityPaidNarrativeValidator.GENERIC);
        notes.set(0, new ActionPlanNote(action.id(), "Start with the topic you found hardest in the last lecture, "
                + "and keep the comparison in your notes."));
        assertThat(validator.validate(with(base, null, null, null, notes, null), NURSING)).isNotNull();
    }

    private void assertRule(UniversityPaidNarrative narrative, String rule) {
        assertThatThrownBy(() -> validator.validate(narrative, NURSING))
                .isInstanceOfSatisfying(NarrativeRuleViolation.class, violation ->
                        assertThat(violation.rule()).isEqualTo(rule));
    }

    static UniversityPaidNarrative summary(String sentence) {
        return Narratives.summary(clean(), sentence);
    }

    static UniversityPaidNarrative withSummary(String summary) {
        var base = clean();
        return new UniversityPaidNarrative(summary, base.measuresTogether(), base.coursePattern(),
                base.moduleLearningInsights(), base.humanCapabilityInsights(), base.skillInsights(),
                base.actionPlanNotes(), base.aiStudyStrategy(), base.closingPerspective());
    }

    private static UniversityPaidNarrative learning(List<ModuleLearningInsight> learning) {
        return with(clean(), learning, null, null, null, null);
    }

    static UniversityPaidNarrative with(UniversityPaidNarrative base, List<ModuleLearningInsight> learning,
            List<HumanCapabilityInsight> capability, List<SkillInsight> skills, List<ActionPlanNote> notes,
            AiStudyStrategy strategy) {
        return new UniversityPaidNarrative(base.executiveSummary(), base.measuresTogether(), base.coursePattern(),
                learning != null ? learning : base.moduleLearningInsights(),
                capability != null ? capability : base.humanCapabilityInsights(),
                skills != null ? skills : base.skillInsights(), notes != null ? notes : base.actionPlanNotes(),
                strategy != null ? strategy : base.aiStudyStrategy(), base.closingPerspective());
    }

    /** Small builders shared with the service test. */
    static final class Narratives {
        static UniversityPaidNarrative summary(UniversityPaidNarrative base, String sentence) {
            return new UniversityPaidNarrative(base.executiveSummary() + " " + sentence, base.measuresTogether(),
                    base.coursePattern(), base.moduleLearningInsights(), base.humanCapabilityInsights(),
                    base.skillInsights(), base.actionPlanNotes(), base.aiStudyStrategy(), base.closingPerspective());
        }
    }
}
