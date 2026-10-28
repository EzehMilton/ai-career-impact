package com.chikere.futureai.judgment.typesafe.alevel;

import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The centralised {@code a-level-v2} atomic judgment contract: one Noul and nine Scores (0–4), every one about the
 * <strong>subject combination</strong> only. The request carries the canonical subject names and nothing about the
 * student, so no question can be answered from grades, interests or ambitions.
 *
 * <p>Changes from {@code a-level-v1}, which is untouched and still describes historical assessments:</p>
 * <ul>
 *   <li>The six capability questions and {@code subject_skill_diversity} are kept, reworded to name the subjects.</li>
 *   <li>{@code degree_pathway_breadth}, a product conclusion, is replaced by {@code discipline_family_coverage}: a
 *       countable observation from which Java derives breadth.</li>
 *   <li>{@code skill_transferability} is removed: it was holistic, asked about work contexts and overlapped breadth
 *       and diversity.</li>
 *   <li>{@code ai_era_complementarity} is replaced by {@code ai_learning_support}: how far AI can support
 *       <em>learning</em> in these subjects while the student still develops their own understanding. A materially
 *       different meaning, so it does not reuse the v1 identifier.</li>
 * </ul>
 *
 * <p>Nothing here asks about university entry requirements: those are external facts for the evidence layer.</p>
 */
public final class ALevelJudgmentSchemaV2 {

    public static final String VERSION = ALevelDecisionPolicyV2.SUPPORTED_SCHEMA_VERSION;

    public static final String INFORMATION_SUFFICIENT = ALevelDecisionPolicyV2.INFORMATION_SUFFICIENT;
    public static final String QUANTITATIVE_REASONING = ALevelDecisionPolicyV2.QUANTITATIVE_REASONING;
    public static final String ANALYTICAL_REASONING = ALevelDecisionPolicyV2.ANALYTICAL_REASONING;
    public static final String WRITTEN_VERBAL_COMMUNICATION = ALevelDecisionPolicyV2.WRITTEN_VERBAL_COMMUNICATION;
    public static final String ORIGINAL_JUDGMENT_CREATIVITY = ALevelDecisionPolicyV2.ORIGINAL_JUDGMENT_CREATIVITY;
    public static final String CONTEXTUAL_SOCIAL_UNDERSTANDING = ALevelDecisionPolicyV2.CONTEXTUAL_SOCIAL_UNDERSTANDING;
    public static final String TECHNICAL_DIGITAL_FOUNDATION = ALevelDecisionPolicyV2.TECHNICAL_DIGITAL_FOUNDATION;
    public static final String SUBJECT_SKILL_DIVERSITY = ALevelDecisionPolicyV2.SUBJECT_SKILL_DIVERSITY;
    public static final String DISCIPLINE_FAMILY_COVERAGE = ALevelDecisionPolicyV2.DISCIPLINE_FAMILY_COVERAGE;
    public static final String AI_LEARNING_SUPPORT = ALevelDecisionPolicyV2.AI_LEARNING_SUPPORT;

    private static final Map<String, String> NAMES = names();

    private final Map<String, Question> questions;

    public ALevelJudgmentSchemaV2() {
        LinkedHashMap<String, Question> questions = new LinkedHashMap<>();
        questions.put(INFORMATION_SUFFICIENT, Noul.builder()
                .instructions("Do the subject names identify recognisable A-level (or equivalent) subjects clearly "
                        + "enough to judge what studying them involves?")
                .whenTrue("Each subject is a recognisable A-level or equivalent subject whose content and methods "
                        + "of study can be described.")
                .whenFalse("At least one subject is unrecognisable, too vague to identify (for example only "
                        + "\"Science\" or \"Option B\"), or not a subject whose study can be described.")
                .build());
        questions.put(QUANTITATIVE_REASONING, Score.of(
                "How much mathematical, statistical or quantitative reasoning does studying these subjects "
                        + "together develop?",
                "Studying these subjects develops little mathematical or quantitative reasoning.",
                "Some numerical work appears, but it is not a defining part of these subjects.",
                "Quantitative work sits alongside other kinds of study in roughly equal measure.",
                "These subjects develop substantial mathematical or statistical reasoning.",
                "Rigorous mathematical, statistical or quantitative reasoning runs through these subjects."));
        questions.put(ANALYTICAL_REASONING, Score.of(
                "How much structured analysis, critical reasoning and problem solving does studying these "
                        + "subjects together develop? Count reasoning about evidence and argument as well as "
                        + "reasoning about numbers or systems.",
                "These subjects develop little structured analysis or problem solving.",
                "Some analysis is required, but established answers usually apply.",
                "Analytical work is mixed with other kinds of study in roughly equal measure.",
                "These subjects develop substantial critical, analytical or problem-solving capability.",
                "Disciplined analysis and problem solving define how these subjects are studied and assessed."));
        questions.put(WRITTEN_VERBAL_COMMUNICATION, Score.of(
                "How much extended writing, argument, explanation or presentation does studying these subjects "
                        + "together develop?",
                "These subjects involve little extended writing or argument.",
                "Some writing or explanation occurs, but it is not central.",
                "Communication is developed alongside other priorities in roughly equal measure.",
                "These subjects develop substantial written or verbal argument and explanation.",
                "Sustained extended writing, argument and explanation define these subjects."));
        questions.put(ORIGINAL_JUDGMENT_CREATIVITY, Score.of(
                "How much interpretation, originality, synthesis, creativity or design does studying these "
                        + "subjects together develop?",
                "These subjects are mainly fixed procedure or reproduction of existing content.",
                "Some independent interpretation is expected, but set approaches usually determine the answer.",
                "These subjects regularly mix established methods with independent interpretation.",
                "Interpretation, synthesis, originality or design is important throughout these subjects.",
                "Original interpretation, synthesis, creativity or design defines these subjects."));
        questions.put(CONTEXTUAL_SOCIAL_UNDERSTANDING, Score.of(
                "How much does studying these subjects together develop understanding of human, social, "
                        + "economic, historical or geographical context?",
                "These subjects engage little with human, social, economic or geographical context.",
                "Some contextual material appears, but it is not central.",
                "Contextual understanding is developed alongside other priorities.",
                "These subjects develop substantial understanding of complex human or social systems.",
                "Interpreting complex human, social, economic or geographical systems defines these subjects."));
        questions.put(TECHNICAL_DIGITAL_FOUNDATION, Score.of(
                "How much technical, computational or digital foundation does studying these subjects together "
                        + "develop?",
                "These subjects develop little technical, computational or digital foundation.",
                "Digital tools are used, but they are not central to these subjects.",
                "Technical and non-technical work are developed in roughly equal measure.",
                "These subjects develop a substantial technical or computational foundation.",
                "A strong technical, computational or digital foundation runs through these subjects."));
        questions.put(SUBJECT_SKILL_DIVERSITY, Score.of(
                "How different in kind are the capabilities that these subjects develop from one another?",
                "The subjects develop very similar kinds of capability as one another.",
                "The subjects are mostly similar in kind, with one modest difference.",
                "The subjects develop a moderate mixture of capability types.",
                "The subjects develop meaningfully different kinds of capability.",
                "The subjects develop markedly different kinds of capability from one another."));
        questions.put(DISCIPLINE_FAMILY_COVERAGE, Score.of(
                "In how many distinct academic families do these subjects give a substantive foundation? Families "
                        + "are broad groups such as the mathematical sciences, the natural sciences, computing and "
                        + "technology, the social sciences, the humanities, languages and literature, and the creative "
                        + "and performing arts. Count closely related subjects as one family. Do not apply any "
                        + "university's entry requirements.",
                "The subjects sit within a single academic family.",
                "The subjects span two closely related families.",
                "The subjects give a foundation in two clearly distinct families.",
                "The subjects give a foundation in three distinct families.",
                "The subjects give a foundation in four or more distinct families."));
        questions.put(AI_LEARNING_SUPPORT, Score.of(
                "How much of the learning in these subjects can be productively supported by AI tools, for example "
                        + "through explanation, practice questions, feedback on drafts or exploring sources, while the "
                        + "subjects still develop the student's own understanding?",
                "Little of the learning in these subjects lends itself to productive AI support.",
                "AI tools can support some of the learning, in a narrow part of these subjects.",
                "AI tools can support a reasonable share of the learning in these subjects.",
                "AI tools can productively support much of the learning in these subjects.",
                "AI tools can productively support most of the learning across these subjects."));
        this.questions = Collections.unmodifiableMap(questions);
    }

    public String version() {
        return VERSION;
    }

    public Map<String, Question> questions() {
        return questions;
    }

    /** The human-readable name of an {@code a-level-v2} dimension. */
    public static String nameOf(String dimensionId) {
        String name = NAMES.get(dimensionId);
        if (name == null) {
            throw new IllegalArgumentException("Unknown a-level-v2 question: " + dimensionId);
        }
        return name;
    }

    private static Map<String, String> names() {
        LinkedHashMap<String, String> names = new LinkedHashMap<>();
        names.put(INFORMATION_SUFFICIENT, "Information sufficiency");
        names.put(QUANTITATIVE_REASONING, "Quantitative reasoning");
        names.put(ANALYTICAL_REASONING, "Analytical reasoning");
        names.put(WRITTEN_VERBAL_COMMUNICATION, "Written and verbal communication");
        names.put(ORIGINAL_JUDGMENT_CREATIVITY, "Original judgement and creativity");
        names.put(CONTEXTUAL_SOCIAL_UNDERSTANDING, "Contextual and social understanding");
        names.put(TECHNICAL_DIGITAL_FOUNDATION, "Technical and digital foundation");
        names.put(SUBJECT_SKILL_DIVERSITY, "Subject skill diversity");
        names.put(DISCIPLINE_FAMILY_COVERAGE, "Discipline family coverage");
        names.put(AI_LEARNING_SUPPORT, "AI learning support");
        return Collections.unmodifiableMap(names);
    }
}
