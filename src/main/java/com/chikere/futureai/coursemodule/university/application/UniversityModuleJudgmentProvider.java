package com.chikere.futureai.coursemodule.university.application;

/**
 * Port for the module-level judgments behind the future paid University report. Implementations make the provider
 * call and map its answers onto the vendor-neutral result; they must fail rather than invent or drop a judgment,
 * and must never classify.
 */
public interface UniversityModuleJudgmentProvider {

    /**
     * @throws UniversityModuleJudgmentException when the provider fails or its answers are incomplete or invalid
     */
    UniversityModuleJudgmentResult judge(UniversityModuleAnalysisRequest request);

    UniversityModuleJudgmentSource source();
}
