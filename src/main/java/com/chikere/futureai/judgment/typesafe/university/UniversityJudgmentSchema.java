package com.chikere.futureai.judgment.typesafe.university;

import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The centralised {@code university-v1} atomic judgment contract.
 *
 * <p>Every question describes an observable characteristic of the course. None of them asks whether
 * a degree is "good", "worth studying", "future-proof", or whether graduates will get jobs: those
 * are product conclusions for deterministic Java policy, not atomic judgments.</p>
 */
public final class UniversityJudgmentSchema {

    public static final String VERSION = "university-v1";

    public static final String INFORMATION_SUFFICIENT = "information_sufficient";
    public static final String TECHNICAL_DIGITAL_DEPTH = "technical_digital_depth";
    public static final String ANALYTICAL_REASONING = "analytical_reasoning";
    public static final String ORIGINAL_JUDGMENT_CREATIVITY = "original_judgment_creativity";
    public static final String HUMAN_RELATIONSHIP_DEPENDENCY = "human_relationship_dependency";
    public static final String CONTEXTUAL_COMPLEXITY = "contextual_complexity";
    public static final String PRACTICAL_REAL_WORLD_APPLICATION = "practical_real_world_application";
    public static final String AI_COMPLEMENTARITY = "ai_complementarity";
    public static final String SKILL_TRANSFERABILITY = "skill_transferability";
    public static final String CAREER_BREADTH = "career_breadth";

    private final Map<String, Question> questions;

    public UniversityJudgmentSchema() {
        LinkedHashMap<String, Question> questions = new LinkedHashMap<>();
        questions.put(INFORMATION_SUFFICIENT, informationSufficient());
        questions.put(TECHNICAL_DIGITAL_DEPTH, Score.of(
                "How much technical or digital capability does this course appear to develop?",
                "The course develops little technical or digital capability.",
                "Some technical or digital tools are used, but they are not central to the course.",
                "The course develops a meaningful mixture of technical and non-technical capability.",
                "The course develops substantial technical or digital capability.",
                "Technical or digital capability development is a core, sustained part of the course."));
        questions.put(ANALYTICAL_REASONING, Score.of(
                "How much structured analysis, quantitative work or systematic problem solving does this "
                        + "course appear to develop?",
                "The course develops little structured analysis or problem solving.",
                "Some analysis is required, but it is not a defining part of the course.",
                "The course mixes analytical work with other kinds of study in roughly equal measure.",
                "The course develops substantial analytical, quantitative or reasoning capability.",
                "Rigorous analysis, quantitative reasoning or systematic problem solving defines the course."));
        questions.put(ORIGINAL_JUDGMENT_CREATIVITY, Score.of(
                "How much synthesis, independent judgment, originality, design or creativity does this "
                        + "course appear to develop?",
                "The course is mostly fixed procedure or reproduction of existing content.",
                "Some independent interpretation is expected, but established answers usually apply.",
                "The course regularly mixes established methods with independent judgment.",
                "Synthesis, independent judgment, design or creativity is important throughout the course.",
                "The course fundamentally depends on original synthesis, judgment, design or creative work."));
        questions.put(HUMAN_RELATIONSHIP_DEPENDENCY, Score.of(
                "How much interpersonal capability — communication, persuasion, collaboration, empathy or "
                        + "working with stakeholders — does this course appear to develop?",
                "The course develops little interpersonal capability.",
                "Some communication or group work occurs, but it is not central.",
                "Interpersonal capability is developed alongside other priorities.",
                "Communication, collaboration, empathy or stakeholder capability is important to the course.",
                "Developing deep interpersonal, communication or relationship capability is central to the course."));
        questions.put(CONTEXTUAL_COMPLEXITY, Score.of(
                "How much does this course prepare students to work with ambiguous, multi-factor or "
                        + "changing situations rather than clearly structured ones?",
                "The course works mainly in highly structured, routine contexts with clear right answers.",
                "Contexts are mostly structured, with occasional ambiguity.",
                "The course mixes structured contexts with genuinely ambiguous ones.",
                "The course frequently requires working with ambiguous, multi-factor or changing situations.",
                "Working with ambiguity, competing factors and changing conditions defines the course."));
        questions.put(PRACTICAL_REAL_WORLD_APPLICATION, Score.of(
                "How strong is the applied component of this course — projects, laboratory work, clinical or "
                        + "studio practice, placements or other real-world application?",
                "The course is mainly theoretical with little applied component.",
                "There is a limited applied element alongside mostly theoretical study.",
                "Applied and theoretical work are present in roughly equal measure.",
                "The course has a strong applied component such as substantial projects, labs or placements.",
                "Extensive real-world practice, placement, clinical, studio or project work defines the course."));
        questions.put(AI_COMPLEMENTARITY, Score.of(
                "How much scope is there for AI tools to productively augment the kind of work this course "
                        + "prepares students for, while valuable human contribution remains?",
                "There appears to be little useful AI augmentation for this kind of work.",
                "AI could assist with a small number of peripheral activities.",
                "AI could usefully support several parts of the work this course prepares students for.",
                "AI could substantially increase capability while important human contribution remains.",
                "There is very strong potential for productive AI augmentation while human judgment, "
                        + "relationships, responsibility or practical capability remain valuable."));
        questions.put(SKILL_TRANSFERABILITY, Score.of(
                "How broadly useful across different roles and sectors are the capabilities this course "
                        + "appears to develop?",
                "The capabilities are narrowly tied to one specific context.",
                "The capabilities apply mainly within one field, with limited wider use.",
                "The capabilities have moderate use beyond the immediate field.",
                "The capabilities are useful across a range of roles or sectors.",
                "The capabilities are broadly useful across many different roles and sectors."));
        questions.put(CAREER_BREADTH, Score.of(
                "How wide is the range of plausible pathways that this course appears to open?",
                "The course points towards a very narrow range of plausible pathways.",
                "The course points mainly towards one field with a few adjacent options.",
                "The course opens a moderate range of plausible pathways.",
                "The course opens a broad range of plausible pathways across several fields.",
                "The course opens a very broad range of plausible pathways across many fields."));
        this.questions = Collections.unmodifiableMap(questions);
    }

    public String version() {
        return VERSION;
    }

    public Map<String, Question> questions() {
        return questions;
    }

    private Noul informationSufficient() {
        return Noul.builder()
                .instructions("Does the supplied information describe enough about what this course actually "
                        + "covers to make meaningful judgments about the capabilities it develops?")
                .whenTrue("There is enough information about course content, modules, skills or how the course "
                        + "is studied to assess the capabilities it develops.")
                .whenFalse("The information is too vague or generic and is essentially only a course title or an "
                        + "insufficient description of what the course covers.")
                .build();
    }
}
