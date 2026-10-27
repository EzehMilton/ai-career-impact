package com.chikere.futureai.responsibility.professional.policy;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The deterministic outcome for one responsibility.
 *
 * @param ruleId         the first rule in {@link ProfessionalResponsibilityPolicyV1} that matched
 * @param oversightRule  the oversight rule that set the marker, if any
 * @param closeCall      whether a comparison that decided the category was within the policy's close-call band of
 *                       its threshold, so a small change in the provider's judgment could change the category.
 *                       Internal QA metadata, never shown to customers.
 * @param describedClearly the provider's clarity answer, kept as a diagnostic only; it does not affect the outcome
 */
public record ResponsibilityClassification(
        int responsibilityIndex,
        String responsibilityKey,
        ResponsibilityCategory category,
        boolean needsCloseHumanOversight,
        Map<ResponsibilityJudgmentKind, ResponsibilityLevel> levels,
        Map<ResponsibilityJudgmentKind, Double> values,
        String ruleId,
        Optional<String> oversightRule,
        boolean closeCall,
        double describedClearly) {

    public static final String OVERSIGHT_LABEL = "Needs close human oversight";

    public ResponsibilityClassification {
        Objects.requireNonNull(responsibilityKey);
        Objects.requireNonNull(category);
        levels = Collections.unmodifiableMap(new EnumMap<>(levels));
        values = Collections.unmodifiableMap(new EnumMap<>(values));
        Objects.requireNonNull(ruleId);
        Objects.requireNonNull(oversightRule);
        if (needsCloseHumanOversight != oversightRule.isPresent()) {
            throw new IllegalArgumentException("the oversight marker and its rule must agree");
        }
    }

    public String displayLabel() {
        return category.label();
    }
}
