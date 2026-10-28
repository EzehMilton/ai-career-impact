package com.chikere.futureai.narrative.alevel.paid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Everything the A-level paid narrative may see, already decided: the subjects, the frozen subject-profile measures
 * with their bands and approved drivers and limits, and the titles of the deterministic guidance. No score, rule id,
 * confidence value, predicted grade or free-text context is included. Preferred degree and career areas are passed
 * only so the explanation can acknowledge them, never to add recommendations.
 */
public record ALevelPaidNarrativeContext(
        UUID assessmentId,
        List<String> subjects,
        List<Measure> measures,
        List<String> strengths,
        List<String> areasToStrengthen,
        List<String> skillPriorities,
        List<String> actions,
        List<String> safeguards,
        List<String> preferredDegreeAreas,
        List<String> preferredCareerAreas) {

    /** @param band the measure's band in its own words, for example "Balanced" or "Strong emphasis" */
    public record Measure(String name, String band, List<String> strengthenedBy, List<String> heldBackBy) {
        public Measure {
            Objects.requireNonNull(name);
            Objects.requireNonNull(band);
            strengthenedBy = List.copyOf(strengthenedBy);
            heldBackBy = List.copyOf(heldBackBy);
        }
    }

    public ALevelPaidNarrativeContext {
        Objects.requireNonNull(assessmentId);
        subjects = List.copyOf(subjects);
        measures = List.copyOf(measures);
        strengths = List.copyOf(strengths);
        areasToStrengthen = List.copyOf(areasToStrengthen);
        skillPriorities = List.copyOf(skillPriorities);
        actions = List.copyOf(actions);
        safeguards = List.copyOf(safeguards);
        preferredDegreeAreas = List.copyOf(preferredDegreeAreas);
        preferredCareerAreas = List.copyOf(preferredCareerAreas);
    }

    /** Text the narrative may quote verbatim: names the student or the report supplied, not claims. */
    public List<String> quotableNames() {
        List<String> names = new java.util.ArrayList<>(subjects);
        names.addAll(preferredDegreeAreas);
        names.addAll(preferredCareerAreas);
        return List.copyOf(names);
    }

    /** The compact provider view, serialised as JSON data in the user message. */
    public Map<String, Object> prompt() {
        Map<String, Object> prompt = new LinkedHashMap<>();
        prompt.put("subjects", subjects);
        prompt.put("subjectProfile", measures);
        prompt.put("strengthsToDevelop", strengths);
        prompt.put("areasToStrengthen", areasToStrengthen);
        prompt.put("skillPriorities", skillPriorities);
        prompt.put("actionTitles", actions);
        prompt.put("safeguards", safeguards);
        prompt.put("preferredDegreeAreas", preferredDegreeAreas);
        prompt.put("preferredCareerAreas", preferredCareerAreas);
        return prompt;
    }
}
