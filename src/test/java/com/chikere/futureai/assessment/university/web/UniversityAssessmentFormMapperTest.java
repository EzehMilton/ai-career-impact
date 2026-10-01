package com.chikere.futureai.assessment.university.web;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class UniversityAssessmentFormMapperTest {

    private final UniversityAssessmentFormMapper mapper = new UniversityAssessmentFormMapper();

    @Test
    void trimsFieldsDropsBlankLinesAndOmitsEmptyOptionals() {
        var form = new UniversityAssessmentForm();
        form.setCourseTitle("  Computer Science  ");
        form.setCourseDescription("  Programming, algorithms and a final-year project.  ");
        form.setKeyModules("Algorithms\n\n   \n  Databases  \n");
        form.setSkillsDeveloped("  Programming  \n\nProblem solving");
        form.setPreferredCareerAreas("");
        form.setInstitution("   ");
        form.setCountry("  United Kingdom ");

        UniversityAssessmentState state = mapper.toState(form);

        assertThat(state.journey()).isEqualTo(JourneyType.UNIVERSITY);
        assertThat(state.courseTitle()).isEqualTo("Computer Science");
        assertThat(state.courseDescription()).isEqualTo("Programming, algorithms and a final-year project.");
        assertThat(state.keyModules()).containsExactly("Algorithms", "Databases");
        assertThat(state.skillsDeveloped()).containsExactly("Programming", "Problem solving");
        assertThat(state.preferredCareerAreas()).isEmpty();
        assertThat(state.institution()).isEmpty();
        assertThat(state.country()).contains("United Kingdom");
    }

    @Test
    void keepsSuppliedOptionalContextAndCareerAreas() {
        var form = new UniversityAssessmentForm();
        form.setCourseTitle("History");
        form.setCourseDescription("Sources, historiography and an independent dissertation.");
        form.setKeyModules("Early modern Europe");
        form.setSkillsDeveloped("Argument");
        form.setPreferredCareerAreas("Museums and heritage\nJournalism");
        form.setInstitution("A UK university");
        form.setCountry("United Kingdom");

        UniversityAssessmentState state = mapper.toState(form);

        assertThat(state.preferredCareerAreas()).containsExactly("Museums and heritage", "Journalism");
        assertThat(state.institution()).contains("A UK university");
        assertThatNullPointerException().isThrownBy(() -> mapper.toState(null));
    }
}
