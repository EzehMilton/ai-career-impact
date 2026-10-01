package com.chikere.futureai.judgment.typesafe.university;

import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import org.junit.jupiter.api.Test;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UniversityJudgmentSchemaTest {

    private final UniversityJudgmentSchema schema = new UniversityJudgmentSchema();

    @Test
    void hasStableVersionAndQuestionIdsWithoutDuplicates() {
        List<String> expectedIds = List.of(
                "information_sufficient",
                "technical_digital_depth",
                "analytical_reasoning",
                "original_judgment_creativity",
                "human_relationship_dependency",
                "contextual_complexity",
                "practical_real_world_application",
                "ai_complementarity",
                "skill_transferability",
                "career_breadth");

        assertThat(schema.version()).isEqualTo("university-v1");
        assertThat(schema.questions().keySet()).containsExactlyElementsOf(expectedIds);
        assertThat(Set.copyOf(schema.questions().keySet())).hasSameSizeAs(expectedIds);
    }

    @Test
    void doesNotReuseProfessionalDimensionsBeyondTheGenuinelyShared() {
        Set<String> universityIds = Set.copyOf(schema.questions().keySet());
        Set<String> professionalIds = Set.copyOf(new ProfessionalJudgmentSchema().questions().keySet());

        // Only these two ids are deliberately shared; everything else must be journey-specific.
        assertThat(universityIds).containsAll(Set.of("information_sufficient", "ai_complementarity"));
        assertThat(universityIds).doesNotContain("task_repeatability", "digital_executability",
                "human_trust_dependency", "physical_world_dependency", "original_judgment",
                "accountability_responsibility");
        Set<String> shared = new java.util.HashSet<>(universityIds);
        shared.retainAll(professionalIds);
        assertThat(shared).containsExactlyInAnyOrder("information_sufficient", "ai_complementarity",
                "contextual_complexity");
    }

    @Test
    void usesOneNoulAndNineScoresWithoutChoiceQuestions() {
        Map<String, Question> questions = schema.questions();

        assertThat(questions.get(UniversityJudgmentSchema.INFORMATION_SUFFICIENT)).isInstanceOf(Noul.class);
        assertThat(questions.entrySet())
                .filteredOn(entry -> !entry.getKey().equals(UniversityJudgmentSchema.INFORMATION_SUFFICIENT))
                .hasSize(9)
                .allSatisfy(entry -> assertThat(entry.getValue()).isInstanceOf(Score.class));
        assertThat(questions.values()).noneMatch(question -> question.typeName().equals("choice"));
    }

    @Test
    void informationSufficiencyDefinesBothSidesOfTheSemanticBoundary() {
        Noul noul = (Noul) schema.questions().get(UniversityJudgmentSchema.INFORMATION_SUFFICIENT);

        assertThat(noul.criteria()).isNotNull();
        assertThat(noul.criteria().whenTrue().toDisplayString())
                .contains("course content", "modules", "skills");
        assertThat(noul.criteria().whenFalse().toDisplayString())
                .contains("too vague", "course title");
    }

    @Test
    void asksNoProductQuestionsAboutWhetherTheDegreeIsGood() {
        String everything = schema.questions().values().stream()
                .map(Question::toString)
                .reduce("", (left, right) -> left + " " + right)
                .toLowerCase(java.util.Locale.ROOT);

        assertThat(everything).doesNotContain("good degree", "should someone study", "worth studying",
                "future-proof", "future proof", "will graduates get jobs", "employability", "salary");
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
        assertThat(expectedLevels()).hasSize(9);
    }

    private Map<String, List<String>> expectedLevels() {
        LinkedHashMap<String, List<String>> levels = new LinkedHashMap<>();
        levels.put(UniversityJudgmentSchema.TECHNICAL_DIGITAL_DEPTH, List.of(
                "The course develops little technical or digital capability.",
                "Some technical or digital tools are used, but they are not central to the course.",
                "The course develops a meaningful mixture of technical and non-technical capability.",
                "The course develops substantial technical or digital capability.",
                "Technical or digital capability development is a core, sustained part of the course."));
        levels.put(UniversityJudgmentSchema.ANALYTICAL_REASONING, List.of(
                "The course develops little structured analysis or problem solving.",
                "Some analysis is required, but it is not a defining part of the course.",
                "The course mixes analytical work with other kinds of study in roughly equal measure.",
                "The course develops substantial analytical, quantitative or reasoning capability.",
                "Rigorous analysis, quantitative reasoning or systematic problem solving defines the course."));
        levels.put(UniversityJudgmentSchema.ORIGINAL_JUDGMENT_CREATIVITY, List.of(
                "The course is mostly fixed procedure or reproduction of existing content.",
                "Some independent interpretation is expected, but established answers usually apply.",
                "The course regularly mixes established methods with independent judgment.",
                "Synthesis, independent judgment, design or creativity is important throughout the course.",
                "The course fundamentally depends on original synthesis, judgment, design or creative work."));
        levels.put(UniversityJudgmentSchema.HUMAN_RELATIONSHIP_DEPENDENCY, List.of(
                "The course develops little interpersonal capability.",
                "Some communication or group work occurs, but it is not central.",
                "Interpersonal capability is developed alongside other priorities.",
                "Communication, collaboration, empathy or stakeholder capability is important to the course.",
                "Developing deep interpersonal, communication or relationship capability is central to the course."));
        levels.put(UniversityJudgmentSchema.CONTEXTUAL_COMPLEXITY, List.of(
                "The course works mainly in highly structured, routine contexts with clear right answers.",
                "Contexts are mostly structured, with occasional ambiguity.",
                "The course mixes structured contexts with genuinely ambiguous ones.",
                "The course frequently requires working with ambiguous, multi-factor or changing situations.",
                "Working with ambiguity, competing factors and changing conditions defines the course."));
        levels.put(UniversityJudgmentSchema.PRACTICAL_REAL_WORLD_APPLICATION, List.of(
                "The course is mainly theoretical with little applied component.",
                "There is a limited applied element alongside mostly theoretical study.",
                "Applied and theoretical work are present in roughly equal measure.",
                "The course has a strong applied component such as substantial projects, labs or placements.",
                "Extensive real-world practice, placement, clinical, studio or project work defines the course."));
        levels.put(UniversityJudgmentSchema.AI_COMPLEMENTARITY, List.of(
                "There appears to be little useful AI augmentation for this kind of work.",
                "AI could assist with a small number of peripheral activities.",
                "AI could usefully support several parts of the work this course prepares students for.",
                "AI could substantially increase capability while important human contribution remains.",
                "There is very strong potential for productive AI augmentation while human judgment, "
                        + "relationships, responsibility or practical capability remain valuable."));
        levels.put(UniversityJudgmentSchema.SKILL_TRANSFERABILITY, List.of(
                "The capabilities are narrowly tied to one specific context.",
                "The capabilities apply mainly within one field, with limited wider use.",
                "The capabilities have moderate use beyond the immediate field.",
                "The capabilities are useful across a range of roles or sectors.",
                "The capabilities are broadly useful across many different roles and sectors."));
        levels.put(UniversityJudgmentSchema.CAREER_BREADTH, List.of(
                "The course points towards a very narrow range of plausible pathways.",
                "The course points mainly towards one field with a few adjacent options.",
                "The course opens a moderate range of plausible pathways.",
                "The course opens a broad range of plausible pathways across several fields.",
                "The course opens a very broad range of plausible pathways across many fields."));
        return levels;
    }
}
