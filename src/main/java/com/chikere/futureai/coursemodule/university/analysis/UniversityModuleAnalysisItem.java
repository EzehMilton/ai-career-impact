package com.chikere.futureai.coursemodule.university.analysis;

import com.chikere.futureai.coursemodule.university.application.UniversityModuleJudgmentSet;
import com.chikere.futureai.coursemodule.university.policy.UniversityModuleClassification;

import java.util.Objects;

/**
 * One analysed module, frozen: its course position, the judgments Jev returned for it, the classification the policy
 * made at the time, and the customer label shown with that classification.
 *
 * @param moduleId the module's 1-based position in the course's stored module list
 */
public record UniversityModuleAnalysisItem(
        int moduleId,
        UniversityModuleJudgmentSet judgments,
        UniversityModuleClassification classification,
        String displayLabel) {

    public UniversityModuleAnalysisItem {
        if (moduleId < 1) {
            throw new IllegalArgumentException("moduleId must be at least 1");
        }
        Objects.requireNonNull(judgments, "judgments must not be null");
        Objects.requireNonNull(classification, "classification must not be null");
        if (displayLabel == null || displayLabel.isBlank()) {
            throw new IllegalArgumentException("displayLabel must not be blank");
        }
        if (!judgments.moduleKey().equals(classification.moduleKey())
                || !judgments.moduleTitle().equals(classification.moduleTitle())) {
            throw new IllegalArgumentException("the classification must belong to the same module as its judgments");
        }
    }
}
