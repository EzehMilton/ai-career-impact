package com.chikere.futureai.livesmoke;

import com.chikere.futureai.assessment.alevel.web.ALevelAssessmentForm;
import com.chikere.futureai.assessment.alevel.web.ALevelAssessmentFormMapper;
import com.chikere.futureai.assessment.professional.web.ProfessionalAssessmentForm;
import com.chikere.futureai.assessment.professional.web.ProfessionalAssessmentFormMapper;
import com.chikere.futureai.assessment.university.web.UniversityAssessmentForm;
import com.chikere.futureai.assessment.university.web.UniversityAssessmentFormMapper;
import org.junit.jupiter.api.Test;

import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class LiveSmokeCaseCatalogTest {

    private final LiveSmokeCaseCatalog catalog = new LiveSmokeCaseCatalog();

    @Test
    void theProfessionalCasesAreExactlyTheAgreedInputs() {
        var nurse = new ProfessionalAssessmentFormMapper().toState((ProfessionalAssessmentForm) catalog.form(
                catalog.find(LiveSmokeJourney.PROFESSIONAL, "registered-nurse").orElseThrow()));
        assertThat(nurse.profession()).isEqualTo("Registered Nurse");
        assertThat(nurse.responsibilities()).hasSize(8).first().isEqualTo("Monitor patients and assess changes in their condition");
        assertThat(nurse.skills()).hasSize(10).last().isEqualTo("Working under pressure");
        assertThat(nurse.yearsOfExperience()).hasValue(8);

        var java = new ProfessionalAssessmentFormMapper().toState((ProfessionalAssessmentForm) catalog.form(
                catalog.find(LiveSmokeJourney.PROFESSIONAL, "java-software-developer").orElseThrow()));
        assertThat(java.responsibilities()).hasSize(8);
        assertThat(java.skills()).hasSize(11).contains("Spring Boot", "Technical decision making");
        assertThat(java.yearsOfExperience()).hasValue(12);

        var accountant = new ProfessionalAssessmentFormMapper().toState((ProfessionalAssessmentForm) catalog.form(
                catalog.find(LiveSmokeJourney.PROFESSIONAL, "management-accountant").orElseThrow()));
        assertThat(accountant.workDescription()).startsWith("I prepare monthly management accounts")
                .endsWith("need attention.");
        assertThat(accountant.skills()).hasSize(10);
        assertThat(accountant.yearsOfExperience()).hasValue(7);
    }

    @Test
    void everyCaseMapsThroughItsJourneysNormalFormMapper() {
        catalog.forJourney(LiveSmokeJourney.UNIVERSITY).forEach(value ->
                assertThat(new UniversityAssessmentFormMapper().toState((UniversityAssessmentForm) catalog.form(value))
                        .courseTitle()).isNotBlank());
        catalog.forJourney(LiveSmokeJourney.A_LEVEL).forEach(value ->
                assertThat(new ALevelAssessmentFormMapper().toState((ALevelAssessmentForm) catalog.form(value))
                        .selectedSubjects()).hasSizeGreaterThanOrEqualTo(2));
        assertThat(catalog.all().stream().collect(Collectors.groupingBy(LiveSmokeCase::journey, Collectors.counting())))
                .containsEntry(LiveSmokeJourney.PROFESSIONAL, 3L).containsEntry(LiveSmokeJourney.UNIVERSITY, 3L)
                .containsEntry(LiveSmokeJourney.A_LEVEL, 3L);
        assertThat(catalog.all().stream().map(value -> value.journey() + "/" + value.id()).distinct()).hasSize(9);
    }
}
