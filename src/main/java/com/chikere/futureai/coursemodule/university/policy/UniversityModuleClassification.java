package com.chikere.futureai.coursemodule.university.policy;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One module's deterministic classification and everything that decided it, so it can be traced and reviewed.
 *
 * @param inputs the module's judgment values exactly as provided, keyed by judgment id
 * @param dominantValue the value of the characteristic the deciding rule tested (for UM4 to UM6, AI learning assistance)
 * @param competingValue the strongest competing value it was compared with, or the independent-demonstration value
 *     for UM4 and UM5
 * @param dominanceGap {@code dominantValue - competingValue}
 * @param closeCallReasons internal QA only, never shown to customers: why a small change in an input could change
 *     the category
 */
public record UniversityModuleClassification(
        String moduleKey,
        String moduleTitle,
        UniversityModuleCategory category,
        UniversityModuleRule rule,
        Map<String, Double> inputs,
        double dominantValue,
        double competingValue,
        double dominanceGap,
        Set<UniversityModuleFlag> flags,
        List<String> closeCallReasons) {

    public UniversityModuleClassification {
        Objects.requireNonNull(moduleKey, "moduleKey must not be null");
        Objects.requireNonNull(moduleTitle, "moduleTitle must not be null");
        Objects.requireNonNull(category, "category must not be null");
        Objects.requireNonNull(rule, "rule must not be null");
        inputs = Map.copyOf(Objects.requireNonNull(inputs, "inputs must not be null"));
        Objects.requireNonNull(flags, "flags must not be null");
        flags = Collections.unmodifiableSet(flags.isEmpty() ? EnumSet.noneOf(UniversityModuleFlag.class)
                : EnumSet.copyOf(flags));
        closeCallReasons = List.copyOf(Objects.requireNonNull(closeCallReasons, "closeCallReasons must not be null"));
    }

    public String customerLabel() {
        return category.customerLabel();
    }

    /** Internal QA only. */
    public boolean closeCall() {
        return !closeCallReasons.isEmpty();
    }

    public boolean hasFlag(UniversityModuleFlag flag) {
        return flags.contains(flag);
    }
}
