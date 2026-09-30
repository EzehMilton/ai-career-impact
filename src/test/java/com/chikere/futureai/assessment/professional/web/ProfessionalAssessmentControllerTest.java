package com.chikere.futureai.assessment.professional.web;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentService;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Optional;
import java.util.UUID;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.mockito.Mockito.never;

@WebMvcTest(ProfessionalAssessmentController.class)
@Import({ WebSecurityConfiguration.class, ProfessionalAssessmentFormMapper.class,
        ProfessionalAssessmentViewMapper.class })
class ProfessionalAssessmentControllerTest {

    private static final UUID SAVED_ID = UUID.fromString("30eff44e-eaa0-4d2e-a853-90983b1c150a");

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProfessionalAssessmentService assessmentService;

    @Test
    void publicGetRendersAccessibleFormAndCssIsPublic() throws Exception {
        mvc.perform(get("/professional-assessment"))
                .andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/form"))
                .andExpect(model().attributeExists("form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("What do you actually do?")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
        mvc.perform(get("/css/professional-assessment.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(".page-shell")));
        mvc.perform(get("/unrelated-private-page"))
                .andExpect(status().is3xxRedirection());
        verifyNoInteractions(assessmentService);
    }

    @Test
    void postRequiresCsrfAndValidationErrorsDoNotCallService() throws Exception {
        mvc.perform(validPost()).andExpect(status().isForbidden());
        mvc.perform(post("/professional-assessment").with(csrf())
                        .param("profession", " ")
                        .param("workDescription", "Too short")
                        .param("responsibilities", " \n ")
                        .param("skills", " ")
                        .param("yearsOfExperience", "-2"))
                .andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/form"))
                .andExpect(model().attributeHasFieldErrors("form", "profession", "workDescription",
                        "responsibilities", "skills", "yearsOfExperience"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Enter your job title.")));
        verifyNoInteractions(assessmentService);
    }

    @Test
    void validPostCallsServiceOnceAndRedirectsToSavedResult() throws Exception {
        when(assessmentService.submitProfessionalAssessment(any())).thenReturn(SAVED_ID);

        mvc.perform(validPost().with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/professional-assessment/" + SAVED_ID));
        when(assessmentService.getProfessionalAssessment(SAVED_ID))
                .thenReturn(Optional.of(snapshot(AssessmentSource.JEV, false)));
        mvc.perform(get("/professional-assessment/" + SAVED_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/result"))
                .andExpect(content().string(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("Software Architect"),
                        org.hamcrest.Matchers.containsString("AI Exposure"),
                        org.hamcrest.Matchers.containsString("Human Advantage"),
                        org.hamcrest.Matchers.containsString("AI Complementarity"),
                        org.hamcrest.Matchers.containsString("Role Adaptability"),
                        org.hamcrest.Matchers.containsString("not a probability that your job will disappear"),
                        org.hamcrest.Matchers.containsString("does not measure your motivation"),
                        org.hamcrest.Matchers.containsString("Much of the work can be performed digitally"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("DEVELOPMENT MODE")),
                        org.hamcrest.Matchers.containsString("noindex,nofollow"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("contextual_complexity")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("probability_distribution")))));
        mvc.perform(get("/professional-assessment/" + SAVED_ID)).andExpect(status().isOk());
        ArgumentCaptor<ProfessionalAssessmentState> state =
                ArgumentCaptor.forClass(ProfessionalAssessmentState.class);
        verify(assessmentService, times(1)).submitProfessionalAssessment(state.capture());
        verify(assessmentService, times(2)).getProfessionalAssessment(SAVED_ID);
        assertThat(state.getValue().profession()).isEqualTo("Software Architect");
        assertThat(state.getValue().responsibilities()).containsExactly("Design systems", "Resolve trade-offs");
    }

    @Test
    void localFixtureShowsWarningAndInsufficientInformationShowsNoNumericMeasures() throws Exception {
        when(assessmentService.getProfessionalAssessment(SAVED_ID))
                .thenReturn(Optional.of(snapshot(AssessmentSource.LOCAL_FIXTURE, true)));

        String html = mvc.perform(get("/professional-assessment/" + SAVED_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/result"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("DEVELOPMENT MODE", "not a live AI assessment",
                "More information is needed before numeric measures can be provided");
        assertThat(html).doesNotContain("/ 10", "measure-value");
    }

    @Test
    void providerFailureRendersSafeErrorWithoutLeakingExceptionDetails() throws Exception {
        when(assessmentService.submitProfessionalAssessment(any())).thenThrow(
                new IllegalStateException("Vendor failure with secret API key"));

        String html = mvc.perform(validPost().with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(view().name("professional-assessment/error"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("couldn't complete your assessment right now");
        assertThat(html).doesNotContain("Vendor failure", "secret API key", "IllegalStateException");
    }

    @Test
    void persistenceFailureIsSafeAndUnknownOrMalformedIdsReturn404() throws Exception {
        when(assessmentService.submitProfessionalAssessment(any())).thenThrow(
                new IllegalStateException("PostgreSQL secret detail"));
        mvc.perform(validPost().with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("PostgreSQL secret detail"))));
        mvc.perform(get("/professional-assessment/" + SAVED_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Assessment not found")));
        mvc.perform(get("/professional-assessment/not-a-uuid"))
                .andExpect(status().isNotFound());
        verify(assessmentService, times(1)).getProfessionalAssessment(SAVED_ID);
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validPost() {
        return post("/professional-assessment")
                .param("profession", "Software Architect")
                .param("workDescription", "I design complex software systems, resolve ambiguous constraints and guide engineers.")
                .param("responsibilities", "Design systems\nResolve trade-offs")
                .param("skills", "Architecture\nStakeholder negotiation")
                .param("yearsOfExperience", "14");
    }

    private ProfessionalAssessmentResult result(AssessmentSource source, boolean insufficient) {
        var state = new com.chikere.futureai.assessment.domain.ProfessionalAssessmentState(
                "Software Architect", "I design complex systems and guide engineers.",
                java.util.List.of("Design systems"), java.util.List.of("Architecture"), OptionalInt.of(14));
        AssessmentJudgmentSet fixture = new LocalFixtureProfessionalJudgmentProvider(
                "senior_software_architect").judge(state);
        AssessmentJudgmentSet judgments = fixture;
        if (insufficient) {
            ArrayList<DimensionJudgment> modified = new ArrayList<>(fixture.judgments());
            modified.set(0, new DimensionJudgment("information_sufficient", "Information sufficiency",
                    OptionalDouble.of(0.2), Optional.empty(), Optional.empty(), OptionalDouble.empty(), Map.of()));
            judgments = new AssessmentJudgmentSet(JourneyType.PROFESSIONAL, fixture.schemaVersion(), modified);
        }
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        return new ProfessionalAssessmentResult(decision, source, judgments.schemaVersion(), decision.policyVersion());
    }

    private StoredProfessionalAssessment snapshot(AssessmentSource source, boolean insufficient) {
        var state = new ProfessionalAssessmentState("Software Architect",
                "I design complex software systems and guide engineers.",
                java.util.List.of("Design systems"), java.util.List.of("Architecture"), OptionalInt.of(14));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        return new StoredProfessionalAssessment(SAVED_ID, Instant.parse("2026-01-01T00:00:00Z"),
                state, judgments, result(source, insufficient), Optional.empty());
    }
}
