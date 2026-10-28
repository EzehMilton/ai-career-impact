package com.chikere.futureai.coursemodule.university.typesafe;

import com.chikere.futureai.coursemodule.university.application.UniversityModuleAnalysisRequest;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jev schema {@value #VERSION}: the questions, level descriptions and answer names for University module analysis.
 * It is separate from the course-level {@code university-v1} schema and covers no thresholds or classification.
 *
 * <p>Module titles never appear in a question. Each question names a module only by its stable key
 * ({@code modules.m3} in the state), so the title is always treated as data, never as instruction. Answer names are
 * {@code m<n>__<judgment>}.</p>
 *
 * <p>AI learning assistance describes where AI may support learning or preparation. It does not determine what use
 * is permitted in assessed work, which is for the institution and module to decide.</p>
 */
public final class UniversityModuleSchema {

    public static final String VERSION = "university-module-v1";

    public static final String DESCRIBED_CLEARLY = "described_clearly";
    public static final String AI_LEARNING_ASSISTANCE = "ai_learning_assistance";
    public static final String INDEPENDENT_DEMONSTRATION = "independent_demonstration";
    public static final String HUMAN_JUDGEMENT = "human_judgement";
    public static final String INTERPERSONAL_WORK = "interpersonal_work";
    public static final String PRACTICAL_APPLICATION = "practical_application";
    public static final String TECHNICAL_QUANTITATIVE = "technical_quantitative";
    public static final String PROFESSIONAL_STANDARDS = "professional_standards";

    /** Every judgment asked for each module, in answer order. */
    public static final List<String> JUDGMENTS = List.of(DESCRIBED_CLEARLY, AI_LEARNING_ASSISTANCE,
            INDEPENDENT_DEMONSTRATION, HUMAN_JUDGEMENT, INTERPERSONAL_WORK, PRACTICAL_APPLICATION,
            TECHNICAL_QUANTITATIVE, PROFESSIONAL_STANDARDS);
    /** The truth-valued (0-1) judgments; every other judgment is a 0-4 score. */
    public static final List<String> TRUTH_JUDGMENTS = List.of(DESCRIBED_CLEARLY, PROFESSIONAL_STANDARDS);

    private static final String SEPARATOR = "__";

    public String version() {
        return VERSION;
    }

    /** The answer name for one judgment of one module, for example {@code m2__human_judgement}. */
    public static String answerName(String moduleKey, String judgment) {
        return moduleKey + SEPARATOR + judgment;
    }

    /** Eight questions per module, in module order. */
    public Map<String, Question> questions(UniversityModuleAnalysisRequest request) {
        LinkedHashMap<String, Question> questions = new LinkedHashMap<>();
        for (String key : request.keyedModules().keySet()) {
            questions.put(answerName(key, DESCRIBED_CLEARLY), describedClearly(key));
            questions.put(answerName(key, AI_LEARNING_ASSISTANCE), aiLearningAssistance(key));
            questions.put(answerName(key, INDEPENDENT_DEMONSTRATION), independentDemonstration(key));
            questions.put(answerName(key, HUMAN_JUDGEMENT), humanJudgement(key));
            questions.put(answerName(key, INTERPERSONAL_WORK), interpersonalWork(key));
            questions.put(answerName(key, PRACTICAL_APPLICATION), practicalApplication(key));
            questions.put(answerName(key, TECHNICAL_QUANTITATIVE), technicalQuantitative(key));
            questions.put(answerName(key, PROFESSIONAL_STANDARDS), professionalStandards(key));
        }
        return Collections.unmodifiableMap(questions);
    }

    private static String subject(String key) {
        return "the module stored under modules." + key + " in the state, as part of the course described there";
    }

    private static Noul describedClearly(String key) {
        return Noul.builder()
                .instructions("Is there enough information, from the title of " + subject(key) + " together with "
                        + "the course context, to make meaningful judgments about what studying this module involves? "
                        + "Judge only whether the module can be understood, not the module itself, and do not treat "
                        + "a short title as evidence for any other judgment.")
                .whenTrue("The module title and course context are enough to understand what the module involves.")
                .whenFalse("The module title is too vague, generic or ambiguous to understand what it involves, even "
                        + "with the course context.")
                .build();
    }

    private static Score aiLearningAssistance(String key) {
        return Score.of(
                "For " + subject(key) + ", to what extent could AI tools assist a student with preparation, "
                        + "research, analysis, practice, drafting, exploration or project work related to the module? "
                        + "Judge the scope for AI to support learning. Do not judge whether AI may complete assessed "
                        + "work, whether any AI use is permitted, or what academic-integrity rules apply.",
                "AI tools could do little to support learning in this module.",
                "AI tools could support a small or peripheral part of learning in this module.",
                "AI tools could usefully support several parts of learning in this module.",
                "AI tools could support much of the preparation, research or practice in this module.",
                "AI tools could support most of the preparation, research, analysis and practice in this module.");
    }

    private static Score independentDemonstration(String key) {
        return Score.of(
                "For " + subject(key) + ", to what extent does the module appear to require the student to "
                        + "demonstrate their own knowledge, reasoning, judgement, practical skill or authorship "
                        + "directly?",
                "The module asks for little direct demonstration of the student's own capability.",
                "Some direct demonstration is expected, but it is a small part of the module.",
                "Direct demonstration of the student's own capability matters alongside other work.",
                "The module substantially depends on the student directly demonstrating their own capability.",
                "The module fundamentally consists of the student directly demonstrating their own knowledge, "
                        + "reasoning, skill or authorship.");
    }

    private static Score humanJudgement(String key) {
        return Score.of(
                "For " + subject(key) + ", how much does the module depend on interpretation, problem framing, "
                        + "judgement under ambiguity or context-sensitive decision-making?",
                "The module follows clear methods with definite answers; little judgement is needed.",
                "Occasional interpretation is needed, but set methods decide most outcomes.",
                "A meaningful mixture of set methods and interpretation or judgement.",
                "The module often depends on interpretation, framing problems or judging ambiguous situations.",
                "The module is dominated by interpretation and context-sensitive judgement.");
    }

    private static Score interpersonalWork(String key) {
        return Score.of(
                "For " + subject(key) + ", how much does the module involve communication, teamwork, care, "
                        + "clients, patients, stakeholders, collaboration or other interpersonal activity?",
                "The module involves very little interpersonal activity.",
                "Some interpersonal activity occurs, but it is not central.",
                "Interpersonal activity matters alongside other parts of the module.",
                "Communication, teamwork or working with people is important to the module.",
                "The module fundamentally consists of interpersonal activity such as care, collaboration or "
                        + "working with clients or stakeholders.");
    }

    private static Score practicalApplication(String key) {
        return Score.of(
                "For " + subject(key) + ", how much does the module involve laboratory, clinical, studio, workshop, "
                        + "field, placement, physical, design-build or other hands-on activity?",
                "The module involves no meaningful hands-on activity.",
                "Occasional hands-on activity supports mainly non-practical study.",
                "A meaningful mixture of hands-on activity and other study.",
                "Hands-on activity is an important part of the module.",
                "The module fundamentally consists of hands-on, practical or placement activity.");
    }

    private static Score technicalQuantitative(String key) {
        return Score.of(
                "For " + subject(key) + ", how much does the module involve technical, mathematical, computational, "
                        + "scientific or quantitatively rigorous work?",
                "The module involves little technical or quantitative work.",
                "Some technical or quantitative work occurs, but it is not central.",
                "Technical or quantitative work matters alongside other kinds of study.",
                "The module substantially involves technical, scientific or quantitative work.",
                "The module is defined by rigorous technical, mathematical, computational or scientific work.");
    }

    private static Noul professionalStandards(String key) {
        return Noul.builder()
                .instructions("Does " + subject(key) + " appear materially shaped by professional, regulated, "
                        + "safety, ethical or accreditation-related standards? Be conservative: answer from what the "
                        + "module and course context support, and do not infer regulation from the course title "
                        + "alone.")
                .whenTrue("The module is materially shaped by professional, regulatory, safety, ethical or "
                        + "accreditation standards.")
                .whenFalse("The module is not materially shaped by such standards, or the context does not show it.")
                .build();
    }
}
