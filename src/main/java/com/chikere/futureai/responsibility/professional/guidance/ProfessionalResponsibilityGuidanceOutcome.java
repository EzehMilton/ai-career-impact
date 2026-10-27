package com.chikere.futureai.responsibility.professional.guidance;

import java.util.Objects;

/** The stored guidance, whether it already existed, and whether its analysis already existed. */
public record ProfessionalResponsibilityGuidanceOutcome(ProfessionalResponsibilityGuidanceSnapshot snapshot,
        boolean reused, boolean analysisReused) {

    public ProfessionalResponsibilityGuidanceOutcome {
        Objects.requireNonNull(snapshot);
    }
}
