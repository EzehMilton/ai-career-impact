package com.chikere.futureai.assessment.domain;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ALevelAssessmentStateTest {

    @Test
    void belongsToTheALevelJourney() {
        assertThat(state(List.of("Mathematics", "Physics")).journey()).isEqualTo(JourneyType.A_LEVEL);
        assertThat(JourneyType.values()).contains(JourneyType.A_LEVEL);
    }

    @Test
    void canonicalisesAndDeduplicatesSubjectsOnConstruction() {
        var state = new ALevelAssessmentState(List.of("maths", "Maths", " comp sci ", "Physics"),
                Map.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());

        assertThat(state.selectedSubjects())
                .containsExactly("Mathematics", "Computer Science", "Physics");
    }

    @Test
    void requiresBetweenTwoAndFiveDistinctSubjects() {
        assertThatIllegalArgumentException().isThrownBy(() -> new ALevelAssessmentState(List.of(),
                Map.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty()))
                .withMessageContaining("at least 2");
        assertThatIllegalArgumentException().isThrownBy(() -> new ALevelAssessmentState(
                List.of("Maths", "Mathematics"), Map.of(), List.of(), List.of(), List.of(),
                Optional.empty(), Optional.empty()))
                .withMessageContaining("at least 2");
        assertThatIllegalArgumentException().isThrownBy(() -> new ALevelAssessmentState(
                List.of("Mathematics", "Physics", "Chemistry", "Biology", "Economics", "History"),
                Map.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty()))
                .withMessageContaining("no more than 5");
        assertThat(state(List.of("Mathematics", "Physics", "Chemistry", "Biology", "Economics"))
                .selectedSubjects()).hasSize(5);
    }

    @Test
    void predictedGradesAreOptionalAndMustReferToSelectedSubjects() {
        var graded = new ALevelAssessmentState(List.of("Maths", "Physics"),
                new LinkedHashMap<>(Map.of("maths", "A")), List.of(), List.of(), List.of(),
                Optional.empty(), Optional.empty());

        assertThat(graded.predictedGrades()).containsExactly(Map.entry("Mathematics", "A"));
        assertThat(state(List.of("Mathematics", "Physics")).predictedGrades()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> new ALevelAssessmentState(
                List.of("Mathematics", "Physics"), Map.of("History", "A"), List.of(), List.of(),
                List.of(), Optional.empty(), Optional.empty()))
                .withMessageContaining("only contain selected subjects");
        assertThatIllegalArgumentException().isThrownBy(() -> new ALevelAssessmentState(
                List.of("Mathematics", "Physics"), Map.of("Mathematics", "a very long grade value"),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty()))
                .withMessageContaining("must be short");
    }

    @Test
    void aStudentIsNeverRequiredToKnowTheirDegreeOrCareer() {
        var undecided = new ALevelAssessmentState(List.of("English Literature", "History", "Politics"),
                Map.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());

        assertThat(undecided.preferredDegreeAreas()).isEmpty();
        assertThat(undecided.preferredCareerAreas()).isEmpty();
        assertThat(undecided.strengthsAndInterests()).isEmpty();
        assertThat(undecided.country()).isEmpty();
        assertThat(undecided.additionalContext()).isEmpty();
    }

    @Test
    void collectionsAreDefensivelyCopiedAndUnmodifiable() {
        ArrayList<String> subjects = new ArrayList<>(List.of("Mathematics", "Physics"));
        LinkedHashMap<String, String> grades = new LinkedHashMap<>(Map.of("Mathematics", "A"));
        var state = new ALevelAssessmentState(subjects, grades, List.of(), List.of(), List.of(),
                Optional.empty(), Optional.empty());

        subjects.add("Chemistry");
        grades.put("Physics", "B");

        assertThat(state.selectedSubjects()).containsExactly("Mathematics", "Physics");
        assertThat(state.predictedGrades()).containsExactly(Map.entry("Mathematics", "A"));
        assertThatThrownBy(() -> state.selectedSubjects().add("Chemistry"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> state.predictedGrades().put("Physics", "B"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatNullPointerException().isThrownBy(() -> new ALevelAssessmentState(
                List.of("Mathematics", "Physics"), Map.of(), List.of(), List.of(), List.of(),
                null, Optional.empty()));
    }

    private ALevelAssessmentState state(List<String> subjects) {
        return new ALevelAssessmentState(subjects, Map.of(), List.of(), List.of(), List.of(),
                Optional.empty(), Optional.empty());
    }
}
