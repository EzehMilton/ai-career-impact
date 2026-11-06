package com.chikere.futureai.assessment.web;

import com.chikere.futureai.assessment.alevel.web.ALevelAssessmentForm;
import com.chikere.futureai.assessment.professional.web.ProfessionalAssessmentForm;
import com.chikere.futureai.assessment.university.web.UniversityAssessmentForm;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bounds on what a single submission may contain.
 *
 * <p>A character limit alone does not bound the shape of the input: four thousand characters can
 * arrive as two thousand one-character lines, each of which becomes a list entry that is judged,
 * stored and rendered. These tests cover both dimensions, and check that the limits are generous
 * enough that nobody describing a real job, course or set of subjects will meet them.</p>
 */
class FormInputLimitsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        factory.close();
    }

    @Test
    void aGenerouslyLongButRealisticProfessionalSubmissionIsAccepted() {
        var form = new ProfessionalAssessmentForm();
        form.setProfession("Principal Software Architect, Payments Platform");
        form.setWorkDescription("I design distributed payment systems, resolve architectural "
                + "trade-offs across several teams, and own the technical direction of the platform.");
        form.setResponsibilities(entries("Own the architecture of a large payment platform", 25));
        form.setSkills(entries("Distributed systems design", 30));
        form.setYearsOfExperience(18);

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void tooManyProfessionalEntriesAreRejectedWithAFieldLevelMessage() {
        var form = validProfessional();
        form.setResponsibilities(entries("A responsibility", 41));

        Set<ConstraintViolation<ProfessionalAssessmentForm>> violations = validator.validate(form);

        assertThat(propertiesOf(violations)).containsExactly("responsibilities");
        assertThat(violations.iterator().next().getMessage()).contains("no more than 40");
    }

    @Test
    void anOverlongSingleProfessionalEntryIsRejectedSeparatelyFromTheFieldLength() {
        var form = validProfessional();
        // Comfortably inside the 2,000-character field limit, but one absurd entry.
        form.setSkills("Java\n" + "x".repeat(200));

        Set<ConstraintViolation<ProfessionalAssessmentForm>> violations = validator.validate(form);

        assertThat(propertiesOf(violations)).containsExactly("skills");
        assertThat(violations.iterator().next().getMessage()).contains("under 120 characters");
    }

    @Test
    void theProfessionalFieldLengthLimitsStillApply() {
        var form = validProfessional();
        form.setProfession("x".repeat(121));

        assertThat(propertiesOf(validator.validate(form))).containsExactly("profession");
    }

    @Test
    void universityCollectionsAreBoundedInBothCountAndEntryLength() {
        var tooMany = validUniversity();
        tooMany.setKeyModules(entries("Distributed Systems", 41));
        assertThat(propertiesOf(validator.validate(tooMany))).containsExactly("keyModules");

        var tooLong = validUniversity();
        tooLong.setPreferredCareerAreas("Software engineering\n" + "x".repeat(150));
        assertThat(propertiesOf(validator.validate(tooLong))).containsExactly("preferredCareerAreas");

        assertThat(validator.validate(validUniversity())).isEmpty();
    }

    @Test
    void aUniversityModuleListedTwiceIsRejectedBecauseItsDetailedReportCouldNeverBePrepared() {
        var repeated = validUniversity();
        repeated.setKeyModules("Statistics\nMicroeconomics\n  statistics  ");
        var violations = validator.validate(repeated);
        assertThat(propertiesOf(violations)).containsExactly("keyModules");
        assertThat(violations).extracting(ConstraintViolation::getMessage).containsExactly("Enter each entry only once.");
        // Skills and career areas may still repeat: nothing downstream analyses them one per entry.
        var skills = validUniversity();
        skills.setSkillsDeveloped("Analysis\nAnalysis");
        assertThat(validator.validate(skills)).isEmpty();
    }

    @Test
    void aLevelCollectionsAreBoundedInBothCountAndEntryLength() {
        var tooMany = validALevel();
        tooMany.setPreferredDegreeAreas(entries("Economics", 21));
        assertThat(propertiesOf(validator.validate(tooMany))).containsExactly("preferredDegreeAreas");

        var tooLong = validALevel();
        tooLong.setStrengthsAndInterests("Problem solving\n" + "x".repeat(250));
        assertThat(propertiesOf(validator.validate(tooLong))).containsExactly("strengthsAndInterests");

        assertThat(validator.validate(validALevel())).isEmpty();
    }

    @Test
    void anEmptyOptionalListFieldIsNotTreatedAsAViolation() {
        // Whether a field is required at all is @NotBlank's business. The bound must stay silent
        // about blanks, or every optional textarea becomes mandatory.
        var form = validALevel();
        form.setPreferredCareerAreas("");
        form.setPreferredDegreeAreas("   ");

        assertThat(validator.validate(form)).isEmpty();
    }

    @Test
    void blankLinesBetweenEntriesDoNotCountTowardsTheLimit() {
        // The form mappers drop blank lines, so counting them here would reject a submission that
        // produces a perfectly ordinary list.
        var form = validProfessional();
        form.setSkills("Java\n\n\n\nSQL\n\n");

        assertThat(validator.validate(form)).isEmpty();
    }

    private ProfessionalAssessmentForm validProfessional() {
        var form = new ProfessionalAssessmentForm();
        form.setProfession("Software Architect");
        form.setWorkDescription("I design complex software systems, resolve constraints and guide "
                + "engineers across several teams every week.");
        form.setResponsibilities("Design systems\nResolve trade-offs");
        form.setSkills("Architecture\nCommunication");
        form.setYearsOfExperience(14);
        return form;
    }

    private UniversityAssessmentForm validUniversity() {
        var form = new UniversityAssessmentForm();
        form.setCourseTitle("BSc Computer Science");
        form.setCourseDescription("A three year computer science degree covering algorithms, "
                + "systems, data and software engineering practice.");
        form.setKeyModules("Algorithms\nDatabases");
        form.setSkillsDeveloped("Programming\nMathematics");
        form.setPreferredCareerAreas("Software engineering");
        form.setInstitution("Example University");
        form.setCountry("United Kingdom");
        return form;
    }

    private ALevelAssessmentForm validALevel() {
        var form = new ALevelAssessmentForm();
        form.setSelectedSubjects("Mathematics\nEconomics\nGeography");
        form.setPredictedGrades("Mathematics: A\nEconomics: B");
        form.setStrengthsAndInterests("Problem solving");
        form.setPreferredDegreeAreas("Economics");
        form.setPreferredCareerAreas("Data analysis");
        form.setCountry("United Kingdom");
        form.setAdditionalContext("");
        return form;
    }

    private String entries(String text, int count) {
        return (text + "\n").repeat(count).strip();
    }

    private <T> Set<String> propertiesOf(Set<ConstraintViolation<T>> violations) {
        return violations.stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
    }
}
