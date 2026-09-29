package com.chikere.futureai.judgment.typesafe.professional;

import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalJudgmentSchemaTest {

    private final ProfessionalJudgmentSchema schema = new ProfessionalJudgmentSchema();

    @Test
    void hasStableVersionAndQuestionIdsWithoutDuplicates() {
        List<String> expectedIds = List.of(
                "information_sufficient",
                "task_repeatability",
                "digital_executability",
                "human_trust_dependency",
                "physical_world_dependency",
                "contextual_complexity",
                "original_judgment",
                "accountability_responsibility",
                "ai_complementarity");

        assertThat(schema.version()).isEqualTo("professional-v1");
        assertThat(schema.questions().keySet()).containsExactlyElementsOf(expectedIds);
        assertThat(Set.copyOf(schema.questions().keySet())).hasSameSizeAs(expectedIds);
    }

    @Test
    void usesOneNoulAndEightScoresWithoutChoiceQuestions() {
        Map<String, Question> questions = schema.questions();

        assertThat(questions.get(ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT))
                .isInstanceOf(Noul.class);
        assertThat(questions.entrySet())
                .filteredOn(entry -> !entry.getKey().equals(ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT))
                .hasSize(8)
                .allSatisfy(entry -> assertThat(entry.getValue()).isInstanceOf(Score.class));
        assertThat(questions.values()).noneMatch(question -> question.typeName().equals("choice"));
    }

    @Test
    void informationSufficiencyDefinesBothSidesOfTheSemanticBoundary() {
        Noul noul = (Noul) schema.questions().get(ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT);

        assertThat(noul.criteria()).isNotNull();
        assertThat(noul.criteria().whenTrue().toDisplayString())
                .contains("responsibilities", "activities", "skills", "work context");
        assertThat(noul.criteria().whenFalse().toDisplayString())
                .contains("too vague", "job title");
    }

    @Test
    void scoreRubricsHaveExactlyFiveCorrectlyOrderedLevels() {
        expectedLevels().forEach((questionId, expected) -> {
            Score score = (Score) schema.questions().get(questionId);
            assertThat(score.criteria())
                    .extracting(level -> level.toDisplayString())
                    .containsExactlyElementsOf(expected);
            assertThat(score.maxLevel()).isEqualTo(4);
        });
    }

    private Map<String, List<String>> expectedLevels() {
        LinkedHashMap<String, List<String>> levels = new LinkedHashMap<>();
        levels.put(ProfessionalJudgmentSchema.TASK_REPEATABILITY, List.of(
                "Work is highly variable and case-specific, with very little repeated process.",
                "Work is mostly variable, although some recurring activities exist.",
                "Work contains a meaningful mixture of routine and variable activities.",
                "Most core tasks follow repeatable, predictable or standardised processes.",
                "Core work is highly repetitive, standardised and predictable."));
        levels.put(ProfessionalJudgmentSchema.DIGITAL_EXECUTABILITY, List.of(
                "Core work depends predominantly on physical activity or presence in the real world.",
                "Digital systems support the work, but much of the core activity is physical or location-dependent.",
                "The role contains a substantial mixture of digital and physical/real-world activity.",
                "Most core work can be performed digitally, with some meaningful non-digital requirements.",
                "Almost all core work can be performed entirely through digital systems and information."));
        levels.put(ProfessionalJudgmentSchema.HUMAN_TRUST_DEPENDENCY, List.of(
                "Human trust or interpersonal skill contributes very little to successful performance.",
                "Some interpersonal interaction occurs, but it is not central to the work.",
                "Human interaction is meaningful but shares importance with other factors.",
                "Trust, persuasion, empathy or relationship-building is important to successful performance.",
                "The role fundamentally depends on deep human trust, empathy, persuasion or relationships."));
        levels.put(ProfessionalJudgmentSchema.PHYSICAL_WORLD_DEPENDENCY, List.of(
                "The work has essentially no meaningful physical-world requirement.",
                "Occasional physical presence or activity is useful but not central.",
                "The role contains a meaningful mixture of digital/intellectual and physical work.",
                "Physical presence, skilled manipulation or real-world activity is important to the role.",
                "Successful performance fundamentally requires skilled physical presence in real-world and "
                        + "potentially changing conditions."));
        levels.put(ProfessionalJudgmentSchema.CONTEXTUAL_COMPLEXITY, List.of(
                "Work is highly structured and normally has clear inputs, rules and expected outputs.",
                "Most situations are predictable, with occasional exceptions.",
                "The work contains a meaningful mixture of structured and ambiguous situations.",
                "The role frequently requires interpreting ambiguous, incomplete or changing situations.",
                "The core work is dominated by novel, ambiguous or rapidly changing situations requiring "
                        + "substantial contextual understanding."));
        levels.put(ProfessionalJudgmentSchema.ORIGINAL_JUDGMENT, List.of(
                "Work mainly applies established rules, templates or procedures.",
                "Some discretion is needed, but established approaches normally determine the answer.",
                "The role regularly mixes established methods with original judgment.",
                "Original judgment, synthesis or creative problem-solving is important to successful performance.",
                "The work fundamentally depends on novel judgment, synthesis, creativity or creating new "
                        + "approaches where established answers are insufficient."));
        levels.put(ProfessionalJudgmentSchema.ACCOUNTABILITY_RESPONSIBILITY, List.of(
                "The person has little independent responsibility for consequential outcomes.",
                "The person owns limited decisions with relatively contained consequences.",
                "The person has meaningful responsibility, normally with oversight or bounded consequences.",
                "The person regularly makes or owns decisions with significant consequences.",
                "The role carries substantial personal, professional, regulatory, safety, financial or "
                        + "organisational accountability for major outcomes."));
        levels.put(ProfessionalJudgmentSchema.AI_COMPLEMENTARITY, List.of(
                "AI appears to provide little meaningful augmentation to the core work.",
                "AI could assist with a small number of peripheral activities.",
                "AI could usefully support several parts of the role.",
                "AI could substantially increase productivity while important human contribution remains.",
                "The role has very strong potential for AI augmentation while important human judgment, "
                        + "responsibility, relationships or physical capability remain valuable."));
        return levels;
    }
}
