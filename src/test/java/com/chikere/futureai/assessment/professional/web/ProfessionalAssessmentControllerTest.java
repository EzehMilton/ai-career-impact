package com.chikere.futureai.assessment.professional.web;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentService;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentWorkflow;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.security.WebSecurityConfiguration;
import com.chikere.futureai.narrative.professional.application.NarrativeSource;
import com.chikere.futureai.narrative.professional.application.NarrativeStatus;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrative;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeService;
import com.chikere.futureai.narrative.professional.application.StoredProfessionalNarrative;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceService;
import com.chikere.futureai.evidence.professional.application.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import java.time.Instant;
import java.net.URI;
import java.math.BigDecimal;

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

    @MockitoBean
    ProfessionalAssessmentWorkflow workflow;

    @MockitoBean
    ProfessionalNarrativeService narrativeService;

    @MockitoBean
    ProfessionalEvidenceService evidenceService;

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
        verifyNoInteractions(assessmentService, workflow, narrativeService);
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
        verifyNoInteractions(assessmentService, workflow, narrativeService);
    }

    @Test
    void validPostCallsServiceOnceAndRedirectsToSavedResult() throws Exception {
        when(workflow.submit(any())).thenReturn(SAVED_ID);

        mvc.perform(validPost().with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/professional-assessment/" + SAVED_ID));
        when(assessmentService.getProfessionalAssessment(SAVED_ID))
                .thenReturn(Optional.of(snapshot(AssessmentSource.JEV, false)));
        when(narrativeService.findFor(SAVED_ID)).thenReturn(Optional.empty());
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
                        org.hamcrest.Matchers.containsString("personalised explanation is temporarily unavailable"),
                        org.hamcrest.Matchers.containsString("does not measure your motivation"),
                        org.hamcrest.Matchers.containsString("Much of the work can be performed digitally"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("DEVELOPMENT MODE")),
                        org.hamcrest.Matchers.containsString("noindex,nofollow"),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("contextual_complexity")),
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("probability_distribution")))));
        mvc.perform(get("/professional-assessment/" + SAVED_ID)).andExpect(status().isOk());
        ArgumentCaptor<ProfessionalAssessmentState> state =
                ArgumentCaptor.forClass(ProfessionalAssessmentState.class);
        verify(workflow, times(1)).submit(state.capture());
        verify(assessmentService, times(2)).getProfessionalAssessment(SAVED_ID);
        verify(evidenceService, times(2)).findFor(SAVED_ID);
        verify(evidenceService, never()).gatherFor(any());
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
    void availableNarrativeRendersStructuredEscapedContentWithoutProviderDetails() throws Exception {
        when(assessmentService.getProfessionalAssessment(SAVED_ID))
                .thenReturn(Optional.of(snapshot(AssessmentSource.JEV, false)));
        var narrative = new ProfessionalNarrative("<script>alert('x')</script>", "Saved summary",
                "Saved meaning", "Saved exposure", "Saved human value", "Saved complementarity",
                "Saved adaptability", List.of("Review repeatable work", "Keep human review", "Document decisions"),
                "Saved caution");
        Instant generated = Instant.parse("2026-01-02T00:00:00Z");
        when(narrativeService.findFor(SAVED_ID)).thenReturn(Optional.of(
                new StoredProfessionalNarrative(SAVED_ID, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                        NarrativeSource.OPENAI, "secret-provider-detail", Optional.of("secret-model"),
                        "professional-narrative-v1", generated, Optional.of(generated))));

        String html = mvc.perform(get("/professional-assessment/" + SAVED_ID))
                .andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/result"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Your personalised explanation", "What this means",
                "Why AI may affect your work", "Where your human advantage remains",
                "How AI could complement your role", "What to focus on next",
                "&lt;script&gt;alert(&#39;x&#39;)&lt;/script&gt;", "Review repeatable work");
        assertThat(html).doesNotContain("<script>alert", "secret-provider-detail", "secret-model",
                "professional-narrative-v1");
    }

    @Test
    void failedNarrativeKeepsDeterministicMeasuresAndRendersSafeMessage() throws Exception {
        when(assessmentService.getProfessionalAssessment(SAVED_ID))
                .thenReturn(Optional.of(snapshot(AssessmentSource.JEV, false)));
        Instant attempted = Instant.parse("2026-01-02T00:00:00Z");
        when(narrativeService.findFor(SAVED_ID)).thenReturn(Optional.of(
                new StoredProfessionalNarrative(SAVED_ID, NarrativeStatus.FAILED, Optional.empty(),
                        NarrativeSource.OPENAI, "openai", Optional.of("hidden-model"),
                        "professional-narrative-v1", attempted, Optional.empty())));

        String html = mvc.perform(get("/professional-assessment/" + SAVED_ID))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("AI Exposure", "Human Advantage",
                "personalised explanation is temporarily unavailable");
        assertThat(html).doesNotContain("hidden-model", "provider exception");
    }

    @Test
    void availableEvidenceRendersAsSeparateEscapedContextWithoutProviderDetails() throws Exception {
        when(assessmentService.getProfessionalAssessment(SAVED_ID))
                .thenReturn(Optional.of(snapshot(AssessmentSource.JEV, false)));
        Instant retrieved = Instant.parse("2026-09-30T12:00:00Z");
        var item = new ProfessionalEvidenceItem(ProfessionalEvidenceCategory.OCCUPATION_OUTLOOK,
                "<script>alert('evidence')</script>", "Saved factual context", "Nomis APS218",
                Optional.of("hidden-ref"), Optional.of(URI.create("https://www.nomisweb.co.uk/datasets/aps218")),
                Optional.of("Office for National Statistics"), Optional.of("United Kingdom"), Optional.empty(),
                retrieved, Optional.of("Estimated employment"), Optional.of(new BigDecimal("123400")),
                Optional.of("people"), EvidenceSourceQuality.OFFICIAL, "Context only; not score validation.");
        when(evidenceService.findFor(SAVED_ID)).thenReturn(Optional.of(new ProfessionalEvidenceBundle(SAVED_ID,
                "professional-evidence-v1", EvidenceSource.LIVE, "secret-adapter-class",
                EvidenceStatus.AVAILABLE, retrieved, List.of(item))));

        String html = mvc.perform(get("/professional-assessment/" + SAVED_ID))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("External career context", "factual context from separate sources",
                "&lt;script&gt;alert(&#39;evidence&#39;)&lt;/script&gt;", "Estimated employment: 123400 people",
                "Office for National Statistics", "https://www.nomisweb.co.uk/datasets/aps218",
                "rel=\"noopener noreferrer\"");
        assertThat(html).doesNotContain("<script>alert", "secret-adapter-class", "professional-evidence-v1",
                "hidden-ref");
        verify(evidenceService, never()).gatherFor(any());
    }

    @Test
    void partialAndFailedEvidenceKeepAssessmentReadableAndShowSafeNotices() throws Exception {
        when(assessmentService.getProfessionalAssessment(SAVED_ID))
                .thenReturn(Optional.of(snapshot(AssessmentSource.JEV, false)));
        Instant retrieved = Instant.parse("2026-09-30T12:00:00Z");
        var item = new ProfessionalEvidenceItem(ProfessionalEvidenceCategory.SKILL_DEMAND,
                "Available item", "One source succeeded.", "Source", Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.of("United Kingdom"), Optional.empty(), retrieved,
                Optional.empty(), Optional.empty(), Optional.empty(), EvidenceSourceQuality.PRIMARY, "Related context.");
        when(evidenceService.findFor(SAVED_ID)).thenReturn(Optional.of(new ProfessionalEvidenceBundle(SAVED_ID,
                "professional-evidence-v1", EvidenceSource.LIVE, "real", EvidenceStatus.PARTIAL,
                retrieved, List.of(item))));
        String partial = mvc.perform(get("/professional-assessment/" + SAVED_ID))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(partial).contains("AI Exposure", "Some external career context is currently unavailable",
                "Available item");

        when(evidenceService.findFor(SAVED_ID)).thenReturn(Optional.of(new ProfessionalEvidenceBundle(SAVED_ID,
                "professional-evidence-v1", EvidenceSource.LIVE, "real", EvidenceStatus.FAILED,
                retrieved, List.of())));
        String failed = mvc.perform(get("/professional-assessment/" + SAVED_ID))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(failed).contains("AI Exposure", "External career context is currently unavailable");
        assertThat(failed).doesNotContain("provider exception", "secret");
    }

    @Test
    void fixtureEvidenceIsUnmistakablyDevelopmentData() throws Exception {
        when(assessmentService.getProfessionalAssessment(SAVED_ID))
                .thenReturn(Optional.of(snapshot(AssessmentSource.LOCAL_FIXTURE, false)));
        var provider = new com.chikere.futureai.evidence.professional.fixture.LocalFixtureProfessionalEvidenceProvider(
                "senior_software_architect");
        when(evidenceService.findFor(SAVED_ID)).thenReturn(Optional.of(provider.gather(
                new ProfessionalEvidenceRequest(SAVED_ID, "Software Architect", List.of("Design"),
                        List.of("Architecture"), "United Kingdom", JourneyType.PROFESSIONAL, Optional.empty()))));

        String html = mvc.perform(get("/professional-assessment/" + SAVED_ID))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("DEVELOPMENT MODE", "Local development evidence — not live official data",
                "Local fixture evidence");
        assertThat(html).doesNotContain("gov.uk");
    }

    @Test
    void providerFailureRendersSafeErrorWithoutLeakingExceptionDetails() throws Exception {
        when(workflow.submit(any())).thenThrow(
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
        when(workflow.submit(any())).thenThrow(
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
