package com.chikere.futureai.responsibility.professional.typesafe;

import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import org.springaicommunity.typesafe.question.Noul;
import org.springaicommunity.typesafe.question.Question;
import org.springaicommunity.typesafe.question.Score;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Jev schema {@value #VERSION}: the state shape, question definitions, level descriptions and answer names for
 * responsibility analysis. It covers no thresholds or classification.
 *
 * <p>Responsibility text never appears in a question. Each question names a responsibility only by its stable
 * key ({@code responsibilities.r3} in the state), so the text is always treated as data, never as instruction.
 * Answer names are {@code r<n>__<judgment>}, plus the role-level {@value #REGULATED_CONTEXT}.</p>
 */
public final class ProfessionalResponsibilitySchema {

    public static final String VERSION = "professional-responsibility-v1";

    public static final String REGULATED_CONTEXT = "regulated_context";
    public static final String DESCRIBED_CLEARLY = "described_clearly";
    public static final String ASSISTANCE_SCOPE = "assistance_scope";
    public static final String CONTEXTUAL_JUDGEMENT = "contextual_judgement";
    public static final String HUMAN_INTERACTION = "human_interaction";
    public static final String PHYSICAL_PRESENCE = "physical_presence";
    public static final String OUTCOME_ACCOUNTABILITY = "outcome_accountability";

    /** The five scored judgments asked for every responsibility, in answer order. */
    public static final List<String> SCORED_JUDGMENTS = List.of(ASSISTANCE_SCOPE, CONTEXTUAL_JUDGEMENT,
            HUMAN_INTERACTION, PHYSICAL_PRESENCE, OUTCOME_ACCOUNTABILITY);

    private static final String SEPARATOR = "__";

    public String version() {
        return VERSION;
    }

    /** The answer name for one judgment of one responsibility, for example {@code r2__assistance_scope}. */
    public static String answerName(String responsibilityKey, String judgment) {
        return responsibilityKey + SEPARATOR + judgment;
    }

    /** The role-level question first, then the clarity question and five scored questions per responsibility. */
    public Map<String, Question> questions(ProfessionalResponsibilityAnalysisRequest request) {
        LinkedHashMap<String, Question> questions = new LinkedHashMap<>();
        questions.put(REGULATED_CONTEXT, regulatedContext());
        for (String key : request.keyedResponsibilities().keySet()) {
            questions.put(answerName(key, DESCRIBED_CLEARLY), describedClearly(key));
            questions.put(answerName(key, ASSISTANCE_SCOPE), assistanceScope(key));
            questions.put(answerName(key, CONTEXTUAL_JUDGEMENT), contextualJudgement(key));
            questions.put(answerName(key, HUMAN_INTERACTION), humanInteraction(key));
            questions.put(answerName(key, PHYSICAL_PRESENCE), physicalPresence(key));
            questions.put(answerName(key, OUTCOME_ACCOUNTABILITY), outcomeAccountability(key));
        }
        return Collections.unmodifiableMap(questions);
    }

    private static String subject(String key) {
        return "the responsibility stored under responsibilities." + key + " in the state, as carried out in the "
                + "work described there";
    }

    private static Score regulatedContext() {
        return Score.of(
                "Judging from the work described as a whole, not from the job title alone, how materially is this "
                        + "work governed by professional regulation, legal obligations, safety-critical procedures, "
                        + "confidentiality or privacy duties, security controls or formal employer procedures?",
                "No meaningful formal governance beyond ordinary workplace expectations.",
                "Some formal procedures or confidentiality expectations apply, with limited consequences.",
                "Formal procedures, confidentiality or regulatory requirements meaningfully shape parts of the work.",
                "Much of the work is governed by regulation, safety-critical procedure, confidentiality or "
                        + "security controls.",
                "The work is fundamentally governed by professional regulation, legal duties or safety-critical "
                        + "procedures that constrain how almost every part of it is done.");
    }

    private static Noul describedClearly(String key) {
        return Noul.builder()
                .instructions("Is " + subject(key) + " described specifically enough to make meaningful judgments "
                        + "about what the activity involves? Judge only the specificity of the description, not "
                        + "the activity itself, and do not treat vague wording as evidence for any other judgment.")
                .whenTrue("The description is specific enough to understand what the activity involves.")
                .whenFalse("The description is too vague, generic or ambiguous to judge what the activity involves.")
                .build();
    }

    private static Score assistanceScope(String key) {
        return Score.of(
                "For " + subject(key) + ", how much scope is there for AI tools to support parts of the work, "
                        + "such as drafting, summarising, analysing, organising information or checking, while a "
                        + "person directs and reviews it? Judge the scope for assistance under human direction, "
                        + "not whether the activity could be done without the person.",
                "Very little scope for AI-assisted support with any part of this activity.",
                "AI tools could support a small or peripheral part of this activity.",
                "AI tools could usefully support several parts of this activity under human direction.",
                "AI tools could support much of the preparatory or information work in this activity under human "
                        + "direction and review.",
                "AI tools could support most of the information work in this activity, with the person directing "
                        + "and reviewing the outcome.");
    }

    private static Score contextualJudgement(String key) {
        return Score.of(
                "For " + subject(key) + ", how much does doing it well depend on situational understanding, "
                        + "weighing trade-offs, interpreting incomplete or changing information, or context that "
                        + "explicit rules do not capture?",
                "Doing it well follows clear rules with predictable inputs; little contextual judgement is needed.",
                "Occasional judgement is needed, but explicit rules decide most of the outcome.",
                "A meaningful mixture of rule-following and contextual judgement.",
                "Doing it well often depends on weighing trade-offs or interpreting incomplete or changing "
                        + "situations.",
                "Doing it well is dominated by situational understanding and judgement that rules cannot capture.");
    }

    private static Score humanInteraction(String key) {
        return Score.of(
                "For " + subject(key) + ", how much does success depend on trust, persuasion, care, negotiation, "
                        + "relationship handling or live communication with other people?",
                "Success depends very little on interaction with other people.",
                "Some interaction occurs, but it is not central to success.",
                "Interaction with people matters alongside other parts of the activity.",
                "Trust, communication or relationship handling is important to success.",
                "Success fundamentally depends on trust, care, persuasion or live human communication.");
    }

    private static Score physicalPresence(String key) {
        return Score.of(
                "For " + subject(key) + ", how much does it require direct physical action, handling or "
                        + "manipulating things in the physical world, or being present in person?",
                "No meaningful physical action or in-person presence is required.",
                "Occasional physical action or presence helps but is not central.",
                "A meaningful mixture of physical or in-person work and other work.",
                "Physical action, handling or in-person presence is important to the activity.",
                "The activity fundamentally consists of physical action or in-person presence.");
    }

    private static Score outcomeAccountability(String key) {
        return Score.of(
                "For " + subject(key) + ", how serious are the professional, safety, legal, financial, security or "
                        + "other consequences of an error, and how much human accountability is attached to the "
                        + "outcome?",
                "Errors have minor, easily corrected consequences and little personal accountability.",
                "Errors have limited consequences, normally caught by routine checks.",
                "Errors can have meaningful consequences, with shared or supervised accountability.",
                "Errors can have significant consequences for which the person is accountable.",
                "Errors can cause serious harm or legal, safety or financial consequences, with clear personal "
                        + "or professional accountability.");
    }
}
