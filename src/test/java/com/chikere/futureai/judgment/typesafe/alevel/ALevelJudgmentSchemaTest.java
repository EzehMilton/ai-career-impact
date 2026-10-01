package com.chikere.futureai.judgment.typesafe.alevel;

import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import com.chikere.futureai.judgment.typesafe.university.UniversityJudgmentSchema;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ALevelJudgmentSchemaTest {

    private final ALevelJudgmentSchema schema = new ALevelJudgmentSchema();

    @Test
    void hasStableVersionAndQuestionIdsWithoutDuplicates() {
        List<String> expectedIds = List.of(
                "information_sufficient",
                "quantitative_reasoning",
                "analytical_reasoning",
                "written_verbal_communication",
                "original_judgment_creativity",
                "contextual_social_understanding",
                "technical_digital_foundation",
                "subject_skill_diversity",
                "degree_pathway_breadth",
                "skill_transferability",
                "ai_era_complementarity");

        assertThat(schema.version()).isEqualTo("a-level-v1");
        assertThat(schema.questions().keySet()).containsExactlyElementsOf(expectedIds);
        assertThat(Set.copyOf(schema.questions().keySet())).hasSameSizeAs(expectedIds);
    }

    @Test
    void doesNotReuseAnotherJourneysScoringDimensions() {
        Set<String> aLevelIds = Set.copyOf(schema.questions().keySet());
        Set<String> professionalIds = Set.copyOf(new ProfessionalJudgmentSchema().questions().keySet());
        Set<String> universityIds = Set.copyOf(new UniversityJudgmentSchema().questions().keySet());

        assertThat(aLevelIds).doesNotContain("task_repeatability", "digital_executability",
                "human_trust_dependency", "physical_world_dependency", "accountability_responsibility",
                "ai_complementarity", "technical_digital_depth", "practical_real_world_application",
                "human_relationship_dependency", "career_breadth", "contextual_complexity");

        Set<String> sharedWithProfessional = new java.util.HashSet<>(aLevelIds);
        sharedWithProfessional.retainAll(professionalIds);
        assertThat(sharedWithProfessional).containsExactly("information_sufficient");

        // Four capability names are genuinely shared with the university schema. The rubrics differ
        // (a course versus a subject combination) and the policies that consume them are unrelated.
        Set<String> sharedWithUniversity = new java.util.HashSet<>(aLevelIds);
        sharedWithUniversity.retainAll(universityIds);
        assertThat(sharedWithUniversity).containsExactlyInAnyOrder("information_sufficient",
                "analytical_reasoning", "original_judgment_creativity", "skill_transferability");
    }

    @Test
    void usesOneNoulAndTenScoresWithoutChoiceQuestions() {
        Map<String, Question> questions = schema.questions();

        assertThat(questions.get(ALevelJudgmentSchema.INFORMATION_SUFFICIENT)).isInstanceOf(Noul.class);
        assertThat(questions.entrySet())
                .filteredOn(entry -> !entry.getKey().equals(ALevelJudgmentSchema.INFORMATION_SUFFICIENT))
                .hasSize(10)
                .allSatisfy(entry -> assertThat(entry.getValue()).isInstanceOf(Score.class));
        assertThat(questions.values()).noneMatch(question -> question.typeName().equals("choice"));
    }

    @Test
    void informationSufficiencyDefinesBothSidesOfTheSemanticBoundary() {
        Noul noul = (Noul) schema.questions().get(ALevelJudgmentSchema.INFORMATION_SUFFICIENT);

        assertThat(noul.criteria()).isNotNull();
        assertThat(noul.criteria().whenTrue().toDisplayString()).contains("subjects", "capabilities");
        assertThat(noul.criteria().whenFalse().toDisplayString()).contains("too vague");
    }

    @Test
    void asksNoProductQuestionsAboutWhetherTheSubjectsAreGood() {
        String everything = everything();

        assertThat(everything).doesNotContain("good a-level", "good subject", "should they drop",
                "should drop", "will this student get", "future-proof", "future proof",
                "employability", "best subject", "worst subject", "right subjects",
                "wrong subjects", "will ai replace");
    }

    @Test
    void neverAsksTheModelAboutUniversityEntryRequirements() {
        String everything = everything();

        // Prerequisites are external facts for the evidence layer, never a model judgment.
        assertThat(everything).doesNotContain("prerequisite", "required subject", "ucas",
                "tariff point", "conditional offer", "aaa", "grade requirement", "grades required");
        // "entry requirements" appears exactly once, and only to forbid the model from using them.
        assertThat(everything.split("entry requirement", -1)).hasSize(2);
        assertThat(schema.questions().get(ALevelJudgmentSchema.DEGREE_PATHWAY_BREADTH).toString()
                .toLowerCase(Locale.ROOT))
                .contains("do not apply any specific university's published entry requirements");
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
        assertThat(expectedLevels()).hasSize(10);
    }

    private String everything() {
        return schema.questions().values().stream()
                .map(Question::toString)
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(Locale.ROOT);
    }

    private Map<String, List<String>> expectedLevels() {
        LinkedHashMap<String, List<String>> levels = new LinkedHashMap<>();
        levels.put(ALevelJudgmentSchema.QUANTITATIVE_REASONING, List.of(
                "The combination develops little mathematical or quantitative capability.",
                "Some numerical work appears, but it is not a defining part of the combination.",
                "Quantitative work sits alongside other kinds of study in roughly equal measure.",
                "The combination develops substantial mathematical or statistical reasoning.",
                "Rigorous mathematical, statistical or quantitative reasoning runs through the combination."));
        levels.put(ALevelJudgmentSchema.ANALYTICAL_REASONING, List.of(
                "The combination develops little structured analysis or problem solving.",
                "Some analysis is required, but established answers usually apply.",
                "The combination mixes analytical work with other kinds of study in roughly equal measure.",
                "The combination develops substantial critical, analytical or problem-solving capability.",
                "Disciplined analysis and problem solving define how the combination is studied and assessed."));
        levels.put(ALevelJudgmentSchema.WRITTEN_VERBAL_COMMUNICATION, List.of(
                "The combination involves little extended communication or argumentation.",
                "Some writing or explanation occurs, but it is not central.",
                "Communication is developed alongside other priorities in roughly equal measure.",
                "The combination develops substantial written or verbal argument and explanation.",
                "Sustained extended writing, argument and explanation define the combination."));
        levels.put(ALevelJudgmentSchema.ORIGINAL_JUDGMENT_CREATIVITY, List.of(
                "The combination is mainly fixed procedure or reproduction of existing content.",
                "Some independent interpretation is expected, but set approaches usually determine the answer.",
                "The combination regularly mixes established methods with independent interpretation.",
                "Interpretation, synthesis, originality or design is important throughout the combination.",
                "Original interpretation, synthesis, creativity or design defines the combination."));
        levels.put(ALevelJudgmentSchema.CONTEXTUAL_SOCIAL_UNDERSTANDING, List.of(
                "The combination engages little with human, social, economic or geographical context.",
                "Some contextual material appears, but it is not central.",
                "Contextual understanding is developed alongside other priorities.",
                "The combination develops substantial understanding of complex human or social systems.",
                "Interpreting complex human, social, economic or geographical systems defines the combination."));
        levels.put(ALevelJudgmentSchema.TECHNICAL_DIGITAL_FOUNDATION, List.of(
                "The combination develops little technical, computational or digital foundation.",
                "Digital tools are used, but they are not central to the combination.",
                "Technical and non-technical capability are developed in roughly equal measure.",
                "The combination develops a substantial technical or computational foundation.",
                "A strong technical, computational or digital foundation runs through the combination."));
        levels.put(ALevelJudgmentSchema.SUBJECT_SKILL_DIVERSITY, List.of(
                "The subjects develop very similar kinds of capability as one another.",
                "The subjects are mostly similar in kind, with one modest difference.",
                "The subjects develop a moderate mixture of capability types.",
                "The subjects develop meaningfully different kinds of capability.",
                "The subjects develop markedly diverse capability types across quantitative, "
                        + "analytical, communicative, creative and practical kinds."));
        levels.put(ALevelJudgmentSchema.DEGREE_PATHWAY_BREADTH, List.of(
                "The combination points towards a very narrow range of plausible degree directions.",
                "The combination points mainly towards one family of degrees with a few adjacent options.",
                "The combination keeps a moderate range of plausible degree directions open.",
                "The combination keeps a broad range of plausible degree directions open across several families.",
                "The combination keeps a very broad range of plausible degree directions open."));
        levels.put(ALevelJudgmentSchema.SKILL_TRANSFERABILITY, List.of(
                "The capabilities are relatively narrow and tied to a particular context.",
                "The capabilities apply mainly within one field, with limited wider use.",
                "The capabilities have moderate use beyond the immediate subjects.",
                "The capabilities transfer usefully across a range of study and work contexts.",
                "The capabilities transfer broadly across many different study and work contexts."));
        levels.put(ALevelJudgmentSchema.AI_ERA_COMPLEMENTARITY, List.of(
                "The combination offers a limited foundation for productive AI-assisted study or work.",
                "The combination offers a modest foundation, concentrated in a narrow area.",
                "The combination offers a reasonable foundation for working alongside AI tools.",
                "The combination offers a strong mix of capabilities likely to combine productively with AI.",
                "The combination offers a particularly strong mix of judgement, reasoning and "
                        + "communication likely to combine productively with AI tools."));
        return levels;
    }
}
