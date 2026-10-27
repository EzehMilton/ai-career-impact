package com.chikere.futureai.responsibility.professional.analysis;

import com.chikere.futureai.responsibility.professional.application.ResponsibilityScoredJudgment;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityJudgmentKind;
import com.chikere.futureai.responsibility.professional.policy.ResponsibilityLevel;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * One analysed responsibility as it was stored: the exact text, every raw provider judgment, and the classification
 * the policy produced then. The classification is the historical authoritative result and is never recomputed.
 */
public record ResponsibilityAnalysisItem(
        int responsibilityIndex,
        String responsibilityKey,
        String responsibilityText,
        double describedClearly,
        Map<ResponsibilityJudgmentKind, ResponsibilityScoredJudgment> judgments,
        Map<ResponsibilityJudgmentKind, ResponsibilityLevel> levels,
        ResponsibilityCategory category,
        String displayLabel,
        boolean needsCloseHumanOversight,
        Optional<String> oversightRule,
        String classificationRule,
        boolean closeCall) {

    public ResponsibilityAnalysisItem {
        Objects.requireNonNull(responsibilityKey);
        Objects.requireNonNull(responsibilityText);
        if (judgments.size() != ResponsibilityJudgmentKind.values().length
                || levels.size() != ResponsibilityJudgmentKind.values().length) {
            throw new IllegalArgumentException("every responsibility must keep all five judgments and their levels");
        }
        judgments = Collections.unmodifiableMap(new EnumMap<>(judgments));
        levels = Collections.unmodifiableMap(new EnumMap<>(levels));
        Objects.requireNonNull(category);
        Objects.requireNonNull(displayLabel);
        Objects.requireNonNull(oversightRule);
        Objects.requireNonNull(classificationRule);
        if (needsCloseHumanOversight != oversightRule.isPresent()) {
            throw new IllegalArgumentException("the oversight marker and its rule must agree");
        }
    }
}
