package com.chikere.futureai.assessment.professional.web;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfessionalAssessmentFormMapperTest {

    private final ProfessionalAssessmentFormMapper mapper = new ProfessionalAssessmentFormMapper();

    @Test
    void trimsBusinessValuesAndDropsBlankLinesWithoutInterpretingThem() {
        ProfessionalAssessmentForm form = validForm();
        form.setProfession("  Senior Architect  ");
        form.setWorkDescription("  I design complex systems and resolve ambiguous technical and organisational constraints.  ");
        form.setResponsibilities("  Design systems  \n \n\tResolve trade-offs\t\r\n");
        form.setSkills("  Architecture  \n\n  Stakeholder negotiation  ");
        form.setYearsOfExperience(14);

        ProfessionalAssessmentState state = mapper.toState(form);

        assertThat(state.profession()).isEqualTo("Senior Architect");
        assertThat(state.workDescription()).startsWith("I design complex systems");
        assertThat(state.responsibilities()).containsExactly("Design systems", "Resolve trade-offs");
        assertThat(state.skills()).containsExactly("Architecture", "Stakeholder negotiation");
        assertThat(state.yearsOfExperience()).hasValue(14);
    }

    @Test
    void optionalYearsRemainAbsent() {
        assertThat(mapper.toState(validForm()).yearsOfExperience()).isEmpty();
    }

    @Test
    void beanValidationRejectsMissingAndObviousInvalidInputs() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            ProfessionalAssessmentForm form = validForm();
            assertThat(validator.validate(form)).isEmpty();

            form.setProfession("   ");
            form.setWorkDescription("Too little detail");
            form.setResponsibilities(" \n \t ");
            form.setSkills(" ");
            form.setYearsOfExperience(-1);
            List<String> invalidProperties = validator.validate(form).stream()
                    .map(violation -> violation.getPropertyPath().toString()).toList();
            assertThat(invalidProperties).contains("profession", "workDescription", "responsibilities",
                    "skills", "yearsOfExperience");

            form.setYearsOfExperience(71);
            assertThat(validator.validate(form)).anySatisfy(violation ->
                    assertThat(violation.getPropertyPath().toString()).isEqualTo("yearsOfExperience"));
        }
    }

    static ProfessionalAssessmentForm validForm() {
        ProfessionalAssessmentForm form = new ProfessionalAssessmentForm();
        form.setProfession("Software Architect");
        form.setWorkDescription("I design complex software systems, resolve ambiguous constraints and guide engineers.");
        form.setResponsibilities("Design systems\nResolve trade-offs");
        form.setSkills("Architecture\nStakeholder negotiation");
        return form;
    }
}
