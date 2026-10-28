package com.chikere.futureai.coursemodule.university.application;

import java.util.Objects;

/**
 * The judgments for one selected module, exactly as provided.
 *
 * <p>{@code describedClearly} and {@code professionalStandards} are the provider's 0-1 truth answers.
 * {@code describedClearly} is diagnostic only (decided in Stage 5C): it must never exclude a module, downgrade one,
 * or directly determine its classification; it is kept for tracing, QA and possible later wording.</p>
 *
 * <p>{@code aiLearningAssistance} describes where AI may support learning or preparation. It does not determine
 * what use is permitted in assessed work. It was high for almost every probed module, so no module may be
 * classified from it alone.</p>
 */
public record UniversityModuleJudgmentSet(
        int moduleIndex,
        String moduleKey,
        String moduleTitle,
        double describedClearly,
        double professionalStandards,
        ModuleScoredJudgment aiLearningAssistance,
        ModuleScoredJudgment independentDemonstration,
        ModuleScoredJudgment humanJudgement,
        ModuleScoredJudgment interpersonalWork,
        ModuleScoredJudgment practicalApplication,
        ModuleScoredJudgment technicalQuantitative) {

    public UniversityModuleJudgmentSet {
        if (!UniversityModuleAnalysisRequest.key(moduleIndex).equals(moduleKey)) {
            throw new IllegalArgumentException("module key must match its index");
        }
        if (moduleTitle == null || moduleTitle.isBlank()) {
            throw new IllegalArgumentException("moduleTitle must not be blank");
        }
        requireTruth(describedClearly, "describedClearly");
        requireTruth(professionalStandards, "professionalStandards");
        Objects.requireNonNull(aiLearningAssistance, "aiLearningAssistance must not be null");
        Objects.requireNonNull(independentDemonstration, "independentDemonstration must not be null");
        Objects.requireNonNull(humanJudgement, "humanJudgement must not be null");
        Objects.requireNonNull(interpersonalWork, "interpersonalWork must not be null");
        Objects.requireNonNull(practicalApplication, "practicalApplication must not be null");
        Objects.requireNonNull(technicalQuantitative, "technicalQuantitative must not be null");
    }

    private static void requireTruth(double value, String name) {
        if (!Double.isFinite(value) || value < 0 || value > 1) {
            throw new IllegalArgumentException(name + " must be finite and between 0 and 1");
        }
    }
}
