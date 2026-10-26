package com.chikere.futureai.responsibility.professional.application;

/**
 * Port for the responsibility-level judgments behind the paid Professional report. Implementations make the
 * provider call and map its answers onto the vendor-neutral result; they must fail rather than invent or drop a
 * judgment, and must never classify.
 */
public interface ProfessionalResponsibilityJudgmentProvider {

    /**
     * @throws ResponsibilityJudgmentException when the provider fails or its answers are incomplete or invalid
     */
    ProfessionalResponsibilityJudgmentResult judge(ProfessionalResponsibilityAnalysisRequest request);

    ResponsibilityJudgmentSource source();
}
