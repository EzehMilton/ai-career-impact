package com.chikere.futureai.decision.openai.poc;

import java.util.List;
import java.util.Objects;

/** Representative role data used only by the Phase 1 OpenAI Decisions proof of concept. */
public record OpenAiDecisionProofOfConceptInput(
        String profession,
        String workDescription,
        String responsibility,
        List<String> skills) {

    public OpenAiDecisionProofOfConceptInput {
        profession = required(profession, "profession");
        workDescription = required(workDescription, "workDescription");
        responsibility = required(responsibility, "responsibility");
        skills = List.copyOf(Objects.requireNonNull(skills, "skills must not be null"));
        if (skills.stream().anyMatch(skill -> skill == null || skill.isBlank())) {
            throw new IllegalArgumentException("skills must not contain blank values");
        }
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
