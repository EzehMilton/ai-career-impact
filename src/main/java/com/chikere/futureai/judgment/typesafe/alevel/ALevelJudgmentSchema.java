package com.chikere.futureai.judgment.typesafe.alevel;

import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The centralised {@code a-level-v1} atomic judgment contract.
 *
 * <p>Every question describes an observable capability characteristic of the whole subject
 * combination. None asks whether the subjects are "good", whether the student will get a job,
 * whether they should drop a subject, or whether the combination is future-proof: those are
 * product conclusions for deterministic Java, not atomic judgments.</p>
 *
 * <p>Crucially, nothing here asks about university entry requirements. A-level prerequisites for
 * specific courses are <strong>factual external information</strong> and belong in the evidence
 * layer with a traceable source, never in a model judgment.</p>
 */
public final class ALevelJudgmentSchema {

    public static final String VERSION = "a-level-v1";

    public static final String INFORMATION_SUFFICIENT = "information_sufficient";
    public static final String QUANTITATIVE_REASONING = "quantitative_reasoning";
    public static final String ANALYTICAL_REASONING = "analytical_reasoning";
    public static final String WRITTEN_VERBAL_COMMUNICATION = "written_verbal_communication";
    public static final String ORIGINAL_JUDGMENT_CREATIVITY = "original_judgment_creativity";
    public static final String CONTEXTUAL_SOCIAL_UNDERSTANDING = "contextual_social_understanding";
    public static final String TECHNICAL_DIGITAL_FOUNDATION = "technical_digital_foundation";
    public static final String SUBJECT_SKILL_DIVERSITY = "subject_skill_diversity";
    public static final String DEGREE_PATHWAY_BREADTH = "degree_pathway_breadth";
    public static final String SKILL_TRANSFERABILITY = "skill_transferability";
    public static final String AI_ERA_COMPLEMENTARITY = "ai_era_complementarity";

    private final Map<String, Question> questions;

    public ALevelJudgmentSchema() {
        LinkedHashMap<String, Question> questions = new LinkedHashMap<>();
        questions.put(INFORMATION_SUFFICIENT, informationSufficient());
        questions.put(QUANTITATIVE_REASONING, Score.of(
                "How much mathematical, statistical or quantitative reasoning does this subject "
                        + "combination develop?",
                "The combination develops little mathematical or quantitative capability.",
                "Some numerical work appears, but it is not a defining part of the combination.",
                "Quantitative work sits alongside other kinds of study in roughly equal measure.",
                "The combination develops substantial mathematical or statistical reasoning.",
                "Rigorous mathematical, statistical or quantitative reasoning runs through the combination."));
        questions.put(ANALYTICAL_REASONING, Score.of(
                "How much structured analysis, critical reasoning and problem solving does this "
                        + "subject combination develop?",
                "The combination develops little structured analysis or problem solving.",
                "Some analysis is required, but established answers usually apply.",
                "The combination mixes analytical work with other kinds of study in roughly equal measure.",
                "The combination develops substantial critical, analytical or problem-solving capability.",
                "Disciplined analysis and problem solving define how the combination is studied and assessed."));
        questions.put(WRITTEN_VERBAL_COMMUNICATION, Score.of(
                "How much extended writing, argument, explanation or presentation does this subject "
                        + "combination develop?",
                "The combination involves little extended communication or argumentation.",
                "Some writing or explanation occurs, but it is not central.",
                "Communication is developed alongside other priorities in roughly equal measure.",
                "The combination develops substantial written or verbal argument and explanation.",
                "Sustained extended writing, argument and explanation define the combination."));
        questions.put(ORIGINAL_JUDGMENT_CREATIVITY, Score.of(
                "How much interpretation, originality, synthesis, creativity or design does this "
                        + "subject combination develop?",
                "The combination is mainly fixed procedure or reproduction of existing content.",
                "Some independent interpretation is expected, but set approaches usually determine the answer.",
                "The combination regularly mixes established methods with independent interpretation.",
                "Interpretation, synthesis, originality or design is important throughout the combination.",
                "Original interpretation, synthesis, creativity or design defines the combination."));
        questions.put(CONTEXTUAL_SOCIAL_UNDERSTANDING, Score.of(
                "How much does this subject combination develop understanding of human, social, "
                        + "economic, historical or geographical context?",
                "The combination engages little with human, social, economic or geographical context.",
                "Some contextual material appears, but it is not central.",
                "Contextual understanding is developed alongside other priorities.",
                "The combination develops substantial understanding of complex human or social systems.",
                "Interpreting complex human, social, economic or geographical systems defines the combination."));
        questions.put(TECHNICAL_DIGITAL_FOUNDATION, Score.of(
                "How much technical, computational or digital foundation does this subject "
                        + "combination develop?",
                "The combination develops little technical, computational or digital foundation.",
                "Digital tools are used, but they are not central to the combination.",
                "Technical and non-technical capability are developed in roughly equal measure.",
                "The combination develops a substantial technical or computational foundation.",
                "A strong technical, computational or digital foundation runs through the combination."));
        questions.put(SUBJECT_SKILL_DIVERSITY, Score.of(
                "How different in kind are the capabilities that these subjects develop from one another?",
                "The subjects develop very similar kinds of capability as one another.",
                "The subjects are mostly similar in kind, with one modest difference.",
                "The subjects develop a moderate mixture of capability types.",
                "The subjects develop meaningfully different kinds of capability.",
                "The subjects develop markedly diverse capability types across quantitative, "
                        + "analytical, communicative, creative and practical kinds."));
        questions.put(DEGREE_PATHWAY_BREADTH, Score.of(
                "Considered as a combination, how wide is the range of plausible higher-education "
                        + "directions these subjects keep open? Judge only the general capability profile; "
                        + "do not apply any specific university's published entry requirements.",
                "The combination points towards a very narrow range of plausible degree directions.",
                "The combination points mainly towards one family of degrees with a few adjacent options.",
                "The combination keeps a moderate range of plausible degree directions open.",
                "The combination keeps a broad range of plausible degree directions open across several families.",
                "The combination keeps a very broad range of plausible degree directions open."));
        questions.put(SKILL_TRANSFERABILITY, Score.of(
                "How broadly do the capabilities developed by this combination transfer across "
                        + "different study and work contexts?",
                "The capabilities are relatively narrow and tied to a particular context.",
                "The capabilities apply mainly within one field, with limited wider use.",
                "The capabilities have moderate use beyond the immediate subjects.",
                "The capabilities transfer usefully across a range of study and work contexts.",
                "The capabilities transfer broadly across many different study and work contexts."));
        questions.put(AI_ERA_COMPLEMENTARITY, Score.of(
                "How well does the mix of capabilities in this combination provide a foundation for "
                        + "working and studying productively alongside AI tools?",
                "The combination offers a limited foundation for productive AI-assisted study or work.",
                "The combination offers a modest foundation, concentrated in a narrow area.",
                "The combination offers a reasonable foundation for working alongside AI tools.",
                "The combination offers a strong mix of capabilities likely to combine productively with AI.",
                "The combination offers a particularly strong mix of judgement, reasoning and "
                        + "communication likely to combine productively with AI tools."));
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
                .instructions("Does the supplied information identify enough about the subject "
                        + "combination to make meaningful judgments about the capabilities it develops?")
                .whenTrue("The subjects are identified clearly enough, with any supporting detail about "
                        + "interests or context, to assess the capabilities the combination develops.")
                .whenFalse("The information is too vague or incomplete to tell which subjects are being "
                        + "taken or what capabilities the combination would develop.")
                .build();
    }
}
