package com.chikere.futureai.responsibility.professional.analysis;

import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet;
import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityClassificationResult;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityClassification;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityJudgmentKind;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds the immutable snapshot from one request, its provider judgments and their classification. */
public final class ResponsibilityAnalysisSnapshots {

    private ResponsibilityAnalysisSnapshots() {
    }

    public static ProfessionalResponsibilityAnalysisSnapshot create(UUID snapshotId, UUID assessmentId,
            ProfessionalResponsibilityAnalysisRequest request, ProfessionalResponsibilityJudgmentResult judgments,
            ProfessionalResponsibilityClassificationResult classification, Instant createdAt, Instant completedAt) {
        if (classification.classifications().size() != judgments.responsibilities().size()) {
            throw new IllegalStateException("every responsibility must be classified exactly once");
        }
        List<ResponsibilityAnalysisItem> items = new ArrayList<>();
        for (int index = 0; index < judgments.responsibilities().size(); index++) {
            ResponsibilityJudgmentSet set = judgments.responsibilities().get(index);
            ResponsibilityClassification classified = classification.classifications().get(index);
            if (!classified.responsibilityKey().equals(set.responsibilityKey())) {
                throw new IllegalStateException("classification order does not match the judgments");
            }
            var raw = new EnumMap<ResponsibilityJudgmentKind, ResponsibilityScoredJudgment>(
                    ResponsibilityJudgmentKind.class);
            raw.put(ResponsibilityJudgmentKind.ASSISTANCE_SCOPE, set.assistanceScope());
            raw.put(ResponsibilityJudgmentKind.CONTEXTUAL_JUDGEMENT, set.contextualJudgement());
            raw.put(ResponsibilityJudgmentKind.HUMAN_INTERACTION, set.humanInteraction());
            raw.put(ResponsibilityJudgmentKind.PHYSICAL_PRESENCE, set.physicalPresence());
            raw.put(ResponsibilityJudgmentKind.OUTCOME_ACCOUNTABILITY, set.outcomeAccountability());
            items.add(new ResponsibilityAnalysisItem(set.responsibilityIndex(), set.responsibilityKey(),
                    set.responsibilityText(), set.describedClearly(), raw, classified.levels(), classified.category(),
                    classified.displayLabel(), classified.needsCloseHumanOversight(), classified.oversightRule(),
                    classified.ruleId(), classified.closeCall()));
        }
        Map<String, Double> parameters = new LinkedHashMap<>();
        parameters.put("moderateFrom", ProfessionalResponsibilityPolicyV1.MODERATE_FROM);
        parameters.put("highFrom", ProfessionalResponsibilityPolicyV1.HIGH_FROM);
        parameters.put("dominanceMargin", ProfessionalResponsibilityPolicyV1.DOMINANCE_MARGIN);
        parameters.put("veryHighAccountabilityFrom", ProfessionalResponsibilityPolicyV1.VERY_HIGH_ACCOUNTABILITY_FROM);
        parameters.put("closeCallBand", ProfessionalResponsibilityPolicyV1.CLOSE_CALL_BAND);
        return new ProfessionalResponsibilityAnalysisSnapshot(snapshotId, assessmentId,
                ResponsibilityAnalysisStatus.COMPLETED, judgments.schemaVersion(), classification.policyVersion(),
                judgments.source(), judgments.model(), judgments.providerRequestId(), judgments.inputTokens(),
                judgments.outputTokens(), new ProfessionalResponsibilityAnalysisInput(request.profession(),
                        request.workDescription(), request.responsibilities(), request.skills()),
                judgments.regulatedContext(), classification.regulatedContextLevel(), parameters, items, createdAt,
                completedAt);
    }

}
