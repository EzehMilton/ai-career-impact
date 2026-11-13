package com.chikere.futureai.assessment.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ALevelSubjectActivitiesTest {

    @Test
    void programmingApplicabilityUsesTheCanonicalSubjectSet() {
        var computing = state("Mathematics", "Physics", "Computing");
        var nonComputing = state("Mathematics", "Physics", "Chemistry");

        assertThat(computing.selectedSubjects()).containsExactly("Mathematics", "Physics", "Computer Science");
        assertThat(ALevelSubjectActivities.supports(computing.selectedSubjects(),
                ALevelSubjectActivities.Activity.PROGRAMMING)).isTrue();
        assertThat(ALevelSubjectActivities.supports(nonComputing.selectedSubjects(),
                ALevelSubjectActivities.Activity.PROGRAMMING)).isFalse();
    }

    private static ALevelAssessmentState state(String... subjects) {
        return new ALevelAssessmentState(List.of(subjects), Map.of(), List.of(), List.of(), List.of(), Optional.empty(),
                Optional.empty());
    }
}
