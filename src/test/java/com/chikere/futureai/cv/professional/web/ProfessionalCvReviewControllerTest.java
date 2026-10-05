package com.chikere.futureai.cv.professional.web;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentInputMetadata;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentWorkflow;
import com.chikere.futureai.cv.professional.application.*;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
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
    @MockitoBean ProfessionalAssessmentWorkflow workflow;

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

    private MockHttpSession session() {
        var session = new MockHttpSession();
        session.setAttribute(ProfessionalCvSession.PROFILE, new ProfessionalProfile("Software Architect",
                "Designs dependable software platforms and resolves complex technical trade-offs.",
                List.of("Design platforms"), List.of("Architecture"), List.of("Communication"),
                List.of(), List.of(), List.of("Payments"), OptionalInt.of(12),
                ProfessionalProfileCompleteness.COMPLETE, List.of("Several roles were present.")));
        session.setAttribute(ProfessionalCvSession.INPUT_METADATA, ProfessionalAssessmentInputMetadata.cvUpload(
                "cv.pdf", "application/pdf", ProfessionalCvVersions.PARSER_V2, Instant.EPOCH));
        return session;
    }
}
