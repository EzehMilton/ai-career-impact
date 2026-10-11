package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentUseCase;
import com.chikere.futureai.cv.professional.application.*;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfessionalCvReviewController.class)
@Import({WebSecurityConfiguration.class, ProfessionalProfileEditMapper.class,
        ProfessionalProfileValidator.class, ProfessionalProfileAssessmentMapper.class})
class ProfessionalCvReviewControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalAssessmentUseCase workflow;

    @Test
    void reviewPageShowsStructuredProfileButNeverRawCv() throws Exception {
        String html = mvc.perform(get("/professional-assessment/cv/review").session(session()))
                .andExpect(status().isOk()).andExpect(view().name("professional-assessment/cv-review"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("We analysed your CV", "Software Architect", "Design platforms",
                "Continue with this profile", "Edit what we understood")
                .doesNotContain("PRIVATE RAW CV");
    }

    @Test
    void editFlowUpdatesOnlyMeaningfulProfileFields() throws Exception {
        var session = session();
        mvc.perform(get("/professional-assessment/cv/edit").session(session))
                .andExpect(status().isOk()).andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Edit what we understood")));

        mvc.perform(post("/professional-assessment/cv/edit").session(session).with(csrf())
                        .param("currentRole", "Principal Architect")
                        .param("roleSummary", "Designs and governs complex software platforms for operational teams.")
                        .param("responsibilities", "Design platforms\nApprove designs")
                        .param("technicalSkills", "Architecture\nJava")
                        .param("humanSkills", "Communication")
                        .param("yearsExperience", "13"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/professional-assessment/cv/review"));

        var profile = (ProfessionalProfile) session.getAttribute(ProfessionalCvSession.PROFILE);
        assertThat(profile.currentRole()).isEqualTo("Principal Architect");
        assertThat(profile.responsibilities()).containsExactly("Design platforms", "Approve designs");
    }

    @Test
    void confirmationRequiresCsrfAndUsesExplicitMapperBeforeExistingWorkflow() throws Exception {
        var session = session();
        UUID id = UUID.randomUUID();
        when(workflow.submit(any(), any())).thenReturn(id);

        mvc.perform(post("/professional-assessment/cv/confirm").session(session))
                .andExpect(status().isForbidden());
        mvc.perform(post("/professional-assessment/cv/confirm").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/professional-assessment/" + id));

        ArgumentCaptor<ProfessionalAssessmentState> state = ArgumentCaptor.forClass(ProfessionalAssessmentState.class);
        verify(workflow).submit(state.capture(), any());
        assertThat(state.getValue().profession()).isEqualTo("Software Architect");
        assertThat(state.getValue().skills()).containsExactly("Architecture", "Communication", "Payments");
        assertThat(session.getAttribute(ProfessionalCvSession.PROFILE)).isNull();
    }

    @Test
    void missingSessionCannotSubmitAnAssessment() throws Exception {
        mvc.perform(post("/professional-assessment/cv/confirm").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/professional-assessment"));
        verifyNoInteractions(workflow);
    }

    private static final String GUIDANCE = "CVs usually list achievements rather than everyday tasks. Add the main things you do in a "
                + "typical week, even if your CV doesn't mention them, and remove one-offs or tasks from earlier jobs.";

    @ParameterizedTest
    @CsvSource(nullValues = "NONE", value = {
            "Northbridge Digital, March 2019, Present, We used your role at Northbridge Digital (March 2019 – Present).",
            "Northbridge Digital, '', '',               We used your role at Northbridge Digital.",
            "Northbridge Digital, March 2019, '',       We used your role at Northbridge Digital.",
            "'', March 2019, Present,                   We used your current role (March 2019 – Present).",
            "'', '', '',                                NONE",
            "'', March 2019, '',                        NONE"})
    void reviewPageNamesTheJobThatWasUsed(String employer, String start, String end, String expected)
            throws Exception {
        String html = mvc.perform(get("/professional-assessment/cv/review")
                        .session(session(profile(employer, start, end))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        if (expected == null) {
            assertThat(html).doesNotContain("We used your");
        }
        else {
            assertThat(html).containsOnlyOnce(expected);
            assertThat(html.split("We used your", -1)).hasSize(2);
        }
    }

    @Test
    void reviewPageEscapesHtmlInTheEmployerName() throws Exception {
        String html = mvc.perform(get("/professional-assessment/cv/review")
                        .session(session(profile("<script>alert(1)</script> & Co", "2019", "Present"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("We used your role at &lt;script&gt;alert(1)&lt;/script&gt; &amp; Co (2019 – Present).")
                .doesNotContain("<script>alert(1)</script>");
    }

    @Test
    void dutiesGuidanceAppearsOnTheReviewAndEditPages() throws Exception {
        String review = mvc.perform(get("/professional-assessment/cv/review").session(session()))
                .andReturn().getResponse().getContentAsString();
        String edit = mvc.perform(get("/professional-assessment/cv/edit").session(session()))
                .andReturn().getResponse().getContentAsString();
        assertThat(review).containsOnlyOnce(GUIDANCE);
        assertThat(review.indexOf(GUIDANCE)).isGreaterThan(review.indexOf("Design platforms"));
        assertThat(edit).containsOnlyOnce(GUIDANCE);
    }

    @Test
    void editRoundTripKeepsEmployerAndDates() throws Exception {
        var session = session(profile("Northbridge Digital", "March 2019", "Present"));
        String form = mvc.perform(get("/professional-assessment/cv/edit").session(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(form).contains("Employer", "Started", "Ended (or Present)",
                "value=\"Northbridge Digital\"", "value=\"March 2019\"", "value=\"Present\"");

        mvc.perform(editRequest(session).param("currentEmployer", " Harbour Lane ")
                        .param("currentRoleStart", "June 2022").param("currentRoleEnd", "Present"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/professional-assessment/cv/review"));

        var profile = (ProfessionalProfile) session.getAttribute(ProfessionalCvSession.PROFILE);
        assertThat(profile.currentEmployer()).isEqualTo("Harbour Lane");
        assertThat(profile.currentRoleStart()).isEqualTo("June 2022");
        assertThat(profile.currentRoleEnd()).isEqualTo("Present");
    }

    @Test
    void editFormRejectsOverLongEmployerAndDates() throws Exception {
        var original = profile("Northbridge Digital", "March 2019", "Present");
        var session = session(original);

        String html = mvc.perform(editRequest(session).param("currentEmployer", "e".repeat(121))
                        .param("currentRoleStart", "s".repeat(41)).param("currentRoleEnd", "n".repeat(41)))
                .andExpect(status().isOk()).andExpect(view().name("professional-assessment/cv-edit"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Keep the employer under 120 characters.",
                "Keep the start date under 40 characters.", "Keep the end date under 40 characters.");
        assertThat(session.getAttribute(ProfessionalCvSession.PROFILE)).isEqualTo(original);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder editRequest(
            MockHttpSession session) {
        return editRequest(session, "13");
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder editRequest(
            MockHttpSession session, String years) {
        return post("/professional-assessment/cv/edit").session(session).with(csrf())
                .param("currentRole", "Principal Architect")
                .param("roleSummary", "Designs and governs complex software platforms for operational teams.")
                .param("responsibilities", "Design platforms\nApprove designs")
                .param("technicalSkills", "Architecture\nJava")
                .param("humanSkills", "Communication")
                .param("yearsExperience", years);
    }

    @Test
    void reviewPageShowsWhichJobsTheYearsAreBasedOnAndEscapesThem() throws Exception {
        String html = mvc.perform(get("/professional-assessment/cv/review").session(session(profile(List.of(
                        "Software Engineer at Push Gaming (08/2025 – Present)",
                        "Developer at <b>Sopra</b> & Co (04/2023 – 08/2025)")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Based on: Software Engineer at Push Gaming (08/2025 – Present)",
                "Based on: Developer at &lt;b&gt;Sopra&lt;/b&gt; &amp; Co (04/2023 – 08/2025)")
                .doesNotContain("<b>Sopra</b>");
        assertThat(html.indexOf("Based on:")).isGreaterThan(html.indexOf("Years of experience"));
    }

    @Test
    void reviewPageShowsNoBasisWhenItIsEmpty() throws Exception {
        String html = mvc.perform(get("/professional-assessment/cv/review").session(session(profile(List.of()))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Years of experience").doesNotContain("Based on:");
    }

    @Test
    void editingTheYearsClearsTheBasis() throws Exception {
        var session = session(profile(List.of("Software Engineer at Push Gaming (08/2025 – Present)")));

        mvc.perform(editRequest(session, "15"))
                .andExpect(redirectedUrl("/professional-assessment/cv/review"));

        var profile = (ProfessionalProfile) session.getAttribute(ProfessionalCvSession.PROFILE);
        assertThat(profile.yearsExperience()).hasValue(15);
        assertThat(profile.experienceBasis()).isEmpty();
        assertThat(mvc.perform(get("/professional-assessment/cv/review").session(session))
                .andReturn().getResponse().getContentAsString()).doesNotContain("Based on:");
    }

    @Test
    void editingOtherFieldsWithTheSameYearsKeepsTheBasis() throws Exception {
        var session = session(profile(List.of("Software Engineer at Push Gaming (08/2025 – Present)")));

        mvc.perform(editRequest(session, "12"))
                .andExpect(redirectedUrl("/professional-assessment/cv/review"));

        var profile = (ProfessionalProfile) session.getAttribute(ProfessionalCvSession.PROFILE);
        assertThat(profile.currentRole()).isEqualTo("Principal Architect");
        assertThat(profile.experienceBasis()).containsExactly("Software Engineer at Push Gaming (08/2025 – Present)");
    }

    @Test
    void clearingTheYearsAlsoClearsTheBasis() throws Exception {
        var session = session(profile(List.of("Software Engineer at Push Gaming (08/2025 – Present)")));

        mvc.perform(editRequest(session, ""))
                .andExpect(redirectedUrl("/professional-assessment/cv/review"));

        var profile = (ProfessionalProfile) session.getAttribute(ProfessionalCvSession.PROFILE);
        assertThat(profile.yearsExperience()).isEmpty();
        assertThat(profile.experienceBasis()).isEmpty();
    }

    private static ProfessionalProfile profile(List<String> basis) {
        return new ProfessionalProfile("Software Architect", "", "", "",
                "Designs dependable software platforms and resolves complex technical trade-offs.",
                List.of("Design platforms"), List.of("Architecture"), List.of("Communication"),
                List.of(), List.of(), List.of("Payments"), OptionalInt.of(12),
                basis, ProfessionalProfileCompleteness.COMPLETE, List.of());
    }

    private static ProfessionalProfile profile(String employer, String start, String end) {
        return new ProfessionalProfile("Software Architect", employer, start, end,
                "Designs dependable software platforms and resolves complex technical trade-offs.",
                List.of("Design platforms"), List.of("Architecture"), List.of("Communication"),
                List.of(), List.of(), List.of("Payments"), OptionalInt.of(12),
                List.of(), ProfessionalProfileCompleteness.COMPLETE, List.of());
    }

    private MockHttpSession session(ProfessionalProfile profile) {
        var session = new MockHttpSession();
        session.setAttribute(ProfessionalCvSession.PROFILE, profile);
        session.setAttribute(ProfessionalCvSession.INPUT_METADATA, ProfessionalAssessmentInputMetadata.cvUpload(
                "cv.pdf", "application/pdf", ProfessionalCvVersions.PARSER_V2, "professional-cv-extractor-v2", Instant.EPOCH));
        return session;
    }

    private MockHttpSession session() {
        var session = new MockHttpSession();
        session.setAttribute(ProfessionalCvSession.PROFILE, new ProfessionalProfile("Software Architect", "", "", "",
                "Designs dependable software platforms and resolves complex technical trade-offs.",
                List.of("Design platforms"), List.of("Architecture"), List.of("Communication"),
                List.of(), List.of(), List.of("Payments"), OptionalInt.of(12),
                List.of(), ProfessionalProfileCompleteness.COMPLETE, List.of("Several roles were present.")));
        session.setAttribute(ProfessionalCvSession.INPUT_METADATA, ProfessionalAssessmentInputMetadata.cvUpload(
                "cv.pdf", "application/pdf", ProfessionalCvVersions.PARSER_V2, "professional-cv-extractor-v2", Instant.EPOCH));
        return session;
    }
}
