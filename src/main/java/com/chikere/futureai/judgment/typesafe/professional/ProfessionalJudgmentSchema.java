package com.chikere.futureai.judgment.typesafe.professional;

import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProfessionalJudgmentSchema {

    public static final String VERSION = "professional-v1";

    public static final String INFORMATION_SUFFICIENT = "information_sufficient";
    public static final String TASK_REPEATABILITY = "task_repeatability";
    public static final String DIGITAL_EXECUTABILITY = "digital_executability";
    public static final String HUMAN_TRUST_DEPENDENCY = "human_trust_dependency";
    public static final String PHYSICAL_WORLD_DEPENDENCY = "physical_world_dependency";
    public static final String CONTEXTUAL_COMPLEXITY = "contextual_complexity";
    public static final String ORIGINAL_JUDGMENT = "original_judgment";
    public static final String ACCOUNTABILITY_RESPONSIBILITY = "accountability_responsibility";
    public static final String AI_COMPLEMENTARITY = "ai_complementarity";

    private final Map<String, Question> questions;

    public ProfessionalJudgmentSchema() {
        LinkedHashMap<String, Question> questions = new LinkedHashMap<>();
        questions.put(INFORMATION_SUFFICIENT, informationSufficient());
        questions.put(TASK_REPEATABILITY, Score.of(
                "How repetitive, standardised and predictable are the person's core work tasks?",
                "Work is highly variable and case-specific, with very little repeated process.",
                "Work is mostly variable, although some recurring activities exist.",
                "Work contains a meaningful mixture of routine and variable activities.",
                "Most core tasks follow repeatable, predictable or standardised processes.",
                "Core work is highly repetitive, standardised and predictable."));
        questions.put(DIGITAL_EXECUTABILITY, Score.of(
                "To what extent can the person's core work be performed entirely through computers, software "
                        + "or digital information without physical-world interaction?",
                "Core work depends predominantly on physical activity or presence in the real world.",
                "Digital systems support the work, but much of the core activity is physical or location-dependent.",
                "The role contains a substantial mixture of digital and physical/real-world activity.",
                "Most core work can be performed digitally, with some meaningful non-digital requirements.",
                "Almost all core work can be performed entirely through digital systems and information."));
        questions.put(HUMAN_TRUST_DEPENDENCY, Score.of(
                "How dependent is successful performance on genuine human trust, empathy, persuasion, "
                        + "relationship-building or sensitive interpersonal interaction?",
                "Human trust or interpersonal skill contributes very little to successful performance.",
                "Some interpersonal interaction occurs, but it is not central to the work.",
                "Human interaction is meaningful but shares importance with other factors.",
                "Trust, persuasion, empathy or relationship-building is important to successful performance.",
                "The role fundamentally depends on deep human trust, empathy, persuasion or relationships."));
        questions.put(PHYSICAL_WORLD_DEPENDENCY, Score.of(
                "How dependent is the person's core work on skilled physical presence, real-world manipulation "
                        + "or responding physically to changing environments?",
                "The work has essentially no meaningful physical-world requirement.",
                "Occasional physical presence or activity is useful but not central.",
                "The role contains a meaningful mixture of digital/intellectual and physical work.",
                "Physical presence, skilled manipulation or real-world activity is important to the role.",
                "Successful performance fundamentally requires skilled physical presence in real-world and "
                        + "potentially changing conditions."));
        questions.put(CONTEXTUAL_COMPLEXITY, Score.of(
                "How much does the person's work depend on interpreting ambiguous, unusual, incomplete or "
                        + "rapidly changing situations?",
                "Work is highly structured and normally has clear inputs, rules and expected outputs.",
                "Most situations are predictable, with occasional exceptions.",
                "The work contains a meaningful mixture of structured and ambiguous situations.",
                "The role frequently requires interpreting ambiguous, incomplete or changing situations.",
                "The core work is dominated by novel, ambiguous or rapidly changing situations requiring "
                        + "substantial contextual understanding."));
        questions.put(ORIGINAL_JUDGMENT, Score.of(
                "How much does successful performance require original judgment, synthesis, creative "
                        + "problem-solving or developing genuinely new approaches?",
                "Work mainly applies established rules, templates or procedures.",
                "Some discretion is needed, but established approaches normally determine the answer.",
                "The role regularly mixes established methods with original judgment.",
                "Original judgment, synthesis or creative problem-solving is important to successful performance.",
                "The work fundamentally depends on novel judgment, synthesis, creativity or creating new "
                        + "approaches where established answers are insufficient."));
        questions.put(ACCOUNTABILITY_RESPONSIBILITY, Score.of(
                "How much personal responsibility does the person carry for consequential decisions, safety, "
                        + "regulation, financial outcomes, people or organisational results?",
                "The person has little independent responsibility for consequential outcomes.",
                "The person owns limited decisions with relatively contained consequences.",
                "The person has meaningful responsibility, normally with oversight or bounded consequences.",
                "The person regularly makes or owns decisions with significant consequences.",
                "The role carries substantial personal, professional, regulatory, safety, financial or "
                        + "organisational accountability for major outcomes."));
        questions.put(AI_COMPLEMENTARITY, Score.of(
                "To what extent could AI significantly improve this person's productivity or capability while "
                        + "the person continues to provide important human value?",
                "AI appears to provide little meaningful augmentation to the core work.",
                "AI could assist with a small number of peripheral activities.",
                "AI could usefully support several parts of the role.",
                "AI could substantially increase productivity while important human contribution remains.",
                "The role has very strong potential for AI augmentation while important human judgment, "
                        + "responsibility, relationships or physical capability remain valuable."));
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
                .instructions("Does the supplied information provide enough detail about what this person "
                        + "actually does to make meaningful judgments about the nature of their work?")
                .whenTrue("There is enough information about responsibilities, activities, skills or work "
                        + "context to assess the person's actual work.")
                .whenFalse("The information is too vague or generic and is essentially only a job title or an "
                        + "insufficient description of the person's actual work.")
                .build();
    }
}
