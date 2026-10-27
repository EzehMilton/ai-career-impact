package com.chikere.futureai.responsibility.professional.analysis;

import java.util.Objects;

/** The stored snapshot, and whether it already existed (no provider call) or was generated now. */
public record ProfessionalResponsibilityAnalysisOutcome(ProfessionalResponsibilityAnalysisSnapshot snapshot,
        boolean reused) {

    public ProfessionalResponsibilityAnalysisOutcome {
        Objects.requireNonNull(snapshot);
    }
}
