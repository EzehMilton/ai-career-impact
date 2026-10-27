package com.chikere.futureai.responsibility.professional.guidance;

import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisSnapshot;

/** Deterministic guidance from a stored responsibility analysis. {@link ProfessionalGuidancePolicyV1} is v1. */
public interface ProfessionalResponsibilityGuidancePolicy {

    ProfessionalResponsibilityGuidance decide(ProfessionalResponsibilityAnalysisSnapshot analysis);
}
