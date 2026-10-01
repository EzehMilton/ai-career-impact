package com.chikere.futureai.assessment.alevel.web;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.JourneyType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class ALevelAssessmentFormMapperTest {

    private final ALevelAssessmentFormMapper mapper = new ALevelAssessmentFormMapper();

    @Test
    void acceptsSubjectsSeparatedByNewLinesOrCommasAndCanonicalisesThem() {
        var form = form("maths, Economics\n  geog  ");

        ALevelAssessmentState state = mapper.toState(form);

        assertThat(state.journey()).isEqualTo(JourneyType.A_LEVEL);
        assertThat(state.selectedSubjects()).containsExactly("Mathematics", "Economics", "Geography");
    }

    @Test
    void parsesPredictedGradesAndIgnoresUnusableLinesRatherThanFailing() {
        var form = form("Mathematics\nPhysics");
        form.setPredictedGrades("maths: A\nPhysics:B\nnot a grade line\nHistory: A*\nChemistry:\n: C");

        ALevelAssessmentState state = mapper.toState(form);

        // Only well-formed lines naming a selected subject survive; nothing else blocks the student.
        assertThat(state.predictedGrades())
                .containsExactly(Map.entry("Mathematics", "A"), Map.entry("Physics", "B"));
    }

    @Test
    void splitsDegreeAndCareerAreasOnNewLinesOnlySoMultiWordAreasSurvive() {
        var form = form("Mathematics\nPhilosophy");
        form.setPreferredDegreeAreas("Philosophy, Politics and Economics\nMathematics");
        form.setPreferredCareerAreas("Research, analysis and policy");
        form.setStrengthsAndInterests("Logic\nWriting");

        ALevelAssessmentState state = mapper.toState(form);

        assertThat(state.preferredDegreeAreas())
                .containsExactly("Philosophy, Politics and Economics", "Mathematics");
        assertThat(state.preferredCareerAreas()).containsExactly("Research, analysis and policy");
        assertThat(state.strengthsAndInterests()).containsExactly("Logic", "Writing");
    }

    @Test
    void anUndecidedStudentSuppliesOnlySubjects() {
        ALevelAssessmentState state = mapper.toState(form("English Literature\nHistory\nPolitics"));

        assertThat(state.selectedSubjects())
                .containsExactly("English Literature", "History", "Politics");
        assertThat(state.predictedGrades()).isEmpty();
        assertThat(state.preferredDegreeAreas()).isEmpty();
        assertThat(state.preferredCareerAreas()).isEmpty();
        assertThat(state.country()).isEmpty();
        assertThat(state.additionalContext()).isEmpty();
    }

    @Test
    void domainRulesStillApplyToTheMappedState() {
        assertThatIllegalArgumentException().isThrownBy(() -> mapper.toState(form("Mathematics")));
        assertThatIllegalArgumentException().isThrownBy(() -> mapper.toState(form("maths, Maths")));
        assertThatNullPointerException().isThrownBy(() -> mapper.toState(null));
    }

    private ALevelAssessmentForm form(String subjects) {
        var form = new ALevelAssessmentForm();
        form.setSelectedSubjects(subjects);
        return form;
    }
}
