package com.chikere.futureai.assessment.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Canonical subject evidence for guidance that assumes a specific study activity. This is applicability only: it
 * carries no scores, weights or subject ranking, and it is not an assessment input.
 */
public final class ALevelSubjectActivities {

    public enum Activity {
        PROGRAMMING
    }

    private static final Map<Activity, Set<String>> SUBJECTS_BY_ACTIVITY = Map.of(
            Activity.PROGRAMMING, Set.of(ALevelSubjects.COMPUTER_SCIENCE));

    private ALevelSubjectActivities() {
    }

    /** The supplied names must already be canonical, as {@link ALevelAssessmentState} guarantees. */
    public static boolean supports(Collection<String> canonicalSubjects, Activity activity) {
        Objects.requireNonNull(canonicalSubjects, "canonicalSubjects must not be null");
        Set<String> applicable = SUBJECTS_BY_ACTIVITY.get(Objects.requireNonNull(activity, "activity must not be null"));
        return canonicalSubjects.stream().anyMatch(applicable::contains);
    }
}
