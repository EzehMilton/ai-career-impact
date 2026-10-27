package com.chikere.futureai.responsibility.professional.policy;

import java.util.List;
import java.util.Objects;

/** Every analysed responsibility's classification, with the versions and role-level signal that produced them. */
public record ProfessionalResponsibilityClassificationResult(
        String policyVersion,
        String schemaVersion,
        ResponsibilityLevel regulatedContextLevel,
        double regulatedContextValue,
        List<ResponsibilityClassification> classifications) {

    public ProfessionalResponsibilityClassificationResult {
        Objects.requireNonNull(policyVersion);
        Objects.requireNonNull(schemaVersion);
        Objects.requireNonNull(regulatedContextLevel);
        classifications = List.copyOf(classifications);
    }
}
