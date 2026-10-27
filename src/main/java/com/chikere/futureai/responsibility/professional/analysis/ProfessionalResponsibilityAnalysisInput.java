package com.chikere.futureai.responsibility.professional.analysis;

import java.util.List;
import java.util.Objects;

/** Exactly what was sent to the provider, frozen with the snapshot so its meaning can never drift. */
public record ProfessionalResponsibilityAnalysisInput(
        String profession,
        String workDescription,
        List<String> responsibilities,
        List<String> skills) {

    public ProfessionalResponsibilityAnalysisInput {
        Objects.requireNonNull(profession);
        Objects.requireNonNull(workDescription);
        responsibilities = List.copyOf(responsibilities);
        skills = List.copyOf(skills);
    }
}
