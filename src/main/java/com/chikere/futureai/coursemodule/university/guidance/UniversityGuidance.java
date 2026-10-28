package com.chikere.futureai.coursemodule.university.guidance;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic paid guidance for one University module-analysis snapshot. Every part comes from the snapshot's
 * judgments and classifications through fixed rules and templates; nothing here is written by a model.
 */
public record UniversityGuidance(
        String policyVersion,
        UUID assessmentId,
        UUID moduleAnalysisSnapshotId,
        String moduleAnalysisPolicyVersion,
        List<UniversityGuidanceItem> aiLearningOpportunities,
        List<UniversityGuidanceItem> ownCapabilityAreas,
        List<UniversityGuidanceItem> technicalFoundations,
        List<UniversityGuidanceItem> safeguards,
        List<UniversitySkillPriority> skillPriorities,
        List<UniversityGuidanceAction> next30Days,
        List<UniversityGuidanceAction> thisTerm,
        List<UniversityGuidanceAction> next6To12Months) {

    public UniversityGuidance {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        Objects.requireNonNull(assessmentId, "assessmentId must not be null");
        Objects.requireNonNull(moduleAnalysisSnapshotId, "moduleAnalysisSnapshotId must not be null");
        Objects.requireNonNull(moduleAnalysisPolicyVersion, "moduleAnalysisPolicyVersion must not be null");
        aiLearningOpportunities = List.copyOf(aiLearningOpportunities);
        ownCapabilityAreas = List.copyOf(ownCapabilityAreas);
        technicalFoundations = List.copyOf(technicalFoundations);
        safeguards = List.copyOf(safeguards);
        skillPriorities = List.copyOf(skillPriorities);
        next30Days = List.copyOf(next30Days);
        thisTerm = List.copyOf(thisTerm);
        next6To12Months = List.copyOf(next6To12Months);
    }
}
