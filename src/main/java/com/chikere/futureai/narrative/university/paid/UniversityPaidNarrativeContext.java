package com.chikere.futureai.narrative.university.paid;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Everything the {@value UniversityPaidNarrativeVersions#V2} narrative may see, already decided: the course as the
 * student described it, the three measures with their bands and drivers, the analysed modules with their
 * classifications and plain-language signals, and the deterministic guidance items, skills, safeguards and actions,
 * each with the id its narrative entry must carry. No score, probability, rule id or diagnostic is included.
 *
 * @param professionalStandardsRelevant whether any analysed module carries the professional-standards safeguard, so
 *     the narrative may mention professional or regulated standards only when the data says so
 * @param programmingVocabularySupported whether the course as described involves programming or software, so code,
 *     queries or debugging are fitting examples; otherwise that vocabulary would be generic copy, not this course
 */
public record UniversityPaidNarrativeContext(
        UUID assessmentId,
        UUID moduleAnalysisId,
        String courseTitle,
        String courseDescription,
        List<String> skillsYouListed,
        List<Measure> measures,
        List<Module> modules,
        List<GuidanceModule> aiLearningModules,
        List<GuidanceModule> ownCapabilityModules,
        List<String> technicalFoundations,
        List<Skill> skillPriorities,
        List<Safeguard> safeguards,
        List<Phase> actionPlan,
        boolean professionalStandardsRelevant,
        boolean programmingVocabularySupported) {

    /** The provider-view name of a fixed guidance sentence the narrative must never repeat or paraphrase. */
    public static final String GENERIC_FALLBACK_COPY = "genericFallbackCopyDoNotRepeatOrParaphrase";

    /** The longest course description passed to the model; it is the student's own text. */
    static final int MAXIMUM_COURSE_DESCRIPTION = 1200;

    /**
     * A small cue list, not a subject ontology: if the student's course text names any of these, programming
     * vocabulary is grounded in it.
     */
    private static final Pattern PROGRAMMING_CUE = Pattern.compile("(?i)\\b(?:programm\\w*|cod(?:e|ing)|software"
            + "|comput(?:er|ing)|algorithms?|databases?|SQL|data\\s+structures?|machine\\s+learning|artificial\\s+"
            + "intelligence|cybersecurity|web\\s+development)\\b");

    public record Measure(String name, String band, List<String> strengthenedBy, List<String> heldBackBy) {
        public Measure {
            Objects.requireNonNull(name);
            Objects.requireNonNull(band);
            strengthenedBy = List.copyOf(strengthenedBy);
            heldBackBy = List.copyOf(heldBackBy);
        }
    }

    /** One analysed module, in course order, with its stored classification and plain-language signals. */
    public record Module(String title, String classification, List<String> signals) {
        public Module {
            Objects.requireNonNull(title);
            Objects.requireNonNull(classification);
            signals = List.copyOf(signals);
        }
    }

    /**
     * A module the deterministic guidance selected, with the id its narrative entry must carry and the fixed
     * guidance sentence the narrative must add to rather than repeat.
     */
    public record GuidanceModule(String id, String module, String classification, List<String> signals,
            String currentGuidance) {
        public GuidanceModule {
            Objects.requireNonNull(id);
            Objects.requireNonNull(module);
            Objects.requireNonNull(classification);
            signals = List.copyOf(signals);
            Objects.requireNonNull(currentGuidance);
        }
    }

    public record Skill(String id, String skill, String explanation, List<String> modules) {
        public Skill {
            Objects.requireNonNull(id);
            Objects.requireNonNull(skill);
            Objects.requireNonNull(explanation);
            modules = List.copyOf(modules);
        }
    }

    public record Safeguard(String title, String text) {
    }

    public record Phase(String phase, List<Action> actions) {
        public Phase {
            actions = List.copyOf(actions);
        }
    }

    public record Action(String id, String action, List<String> modules) {
        public Action {
            modules = List.copyOf(modules);
        }
    }

    public UniversityPaidNarrativeContext {
        Objects.requireNonNull(assessmentId);
        Objects.requireNonNull(moduleAnalysisId);
        Objects.requireNonNull(courseTitle);
        courseDescription = courseDescription == null ? "" : courseDescription.strip();
        if (courseDescription.length() > MAXIMUM_COURSE_DESCRIPTION) {
            courseDescription = courseDescription.substring(0, MAXIMUM_COURSE_DESCRIPTION).strip();
        }
        skillsYouListed = List.copyOf(skillsYouListed);
        measures = List.copyOf(measures);
        modules = List.copyOf(modules);
        aiLearningModules = List.copyOf(aiLearningModules);
        ownCapabilityModules = List.copyOf(ownCapabilityModules);
        technicalFoundations = List.copyOf(technicalFoundations);
        skillPriorities = List.copyOf(skillPriorities);
        safeguards = List.copyOf(safeguards);
        actionPlan = List.copyOf(actionPlan);
    }

    /** Whether the student's own course text (title, description, listed skills, module titles) involves programming. */
    public static boolean programmingVocabularySupported(String courseTitle, String courseDescription,
            List<String> skillsListed, List<String> moduleTitles) {
        List<String> texts = new ArrayList<>(List.of(courseTitle, courseDescription == null ? "" : courseDescription));
        texts.addAll(skillsListed);
        texts.addAll(moduleTitles);
        return texts.stream().anyMatch(text -> PROGRAMMING_CUE.matcher(text).find());
    }

    public List<String> moduleLearningIds() {
        return aiLearningModules.stream().map(GuidanceModule::id).toList();
    }

    public List<String> humanCapabilityIds() {
        return ownCapabilityModules.stream().map(GuidanceModule::id).toList();
    }

    public List<String> skillIds() {
        return skillPriorities.stream().map(Skill::id).toList();
    }

    public List<String> actionIds() {
        return actionPlan.stream().flatMap(phase -> phase.actions().stream()).map(Action::id).toList();
    }

    /** Every deterministic action by id, for the restatement check. */
    public Map<String, Action> actionsById() {
        Map<String, Action> byId = new LinkedHashMap<>();
        actionPlan.forEach(phase -> phase.actions().forEach(action -> byId.put(action.id(), action)));
        return byId;
    }

    /** The student's own course text, lower case: a term named here is the student's, never invented. */
    public String groundingText() {
        List<String> texts = new ArrayList<>(List.of(courseTitle, courseDescription));
        texts.addAll(skillsYouListed);
        modules.forEach(module -> texts.add(module.title()));
        return String.join(" \n ", texts).toLowerCase(Locale.ROOT);
    }

    /**
     * A guidance module as the provider sees it. The fixed guidance sentence is generic copy the narrative exists to
     * improve on, so an AI-learning module's is withheld: it anchored the model on the very wording it must not
     * repeat. An own-capability module's sentence is shown to the student directly above the narrative, so it is
     * passed under a name that says what it is.
     */
    private static Map<String, Object> view(GuidanceModule module, boolean shownAbove) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", module.id());
        view.put("module", module.module());
        view.put("classification", module.classification());
        view.put("signals", module.signals());
        if (shownAbove) {
            view.put(GENERIC_FALLBACK_COPY, module.currentGuidance());
        }
        return view;
    }

    /** The compact provider view, serialised as JSON data in the user message. */
    public Map<String, Object> prompt() {
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("courseTitle", courseTitle);
        prompt.put("courseDescription", courseDescription);
        prompt.put("skillsYouListed", skillsYouListed);
        prompt.put("courseProfile", measures);
        prompt.put("analysedModules", modules);
        prompt.put("aiLearningModules", aiLearningModules.stream().map(module -> view(module, false)).toList());
        prompt.put("ownCapabilityModules", ownCapabilityModules.stream().map(module -> view(module, true)).toList());
        prompt.put("technicalFoundations", technicalFoundations);
        prompt.put("skillPriorities", skillPriorities);
        prompt.put("safeguards", safeguards);
        prompt.put("actionPlan", actionPlan);
        prompt.put("professionalStandardsRelevant", professionalStandardsRelevant);
        prompt.put("programmingVocabularySupported", programmingVocabularySupported);
        return prompt;
    }
}
