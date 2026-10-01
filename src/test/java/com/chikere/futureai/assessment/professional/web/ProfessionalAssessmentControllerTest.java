package com.chikere.futureai.assessment.professional.web;

import com.chikere.futureai.assessment.domain.*;
import com.chikere.futureai.assessment.professional.application.*;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.*;
import com.chikere.futureai.report.professional.application.*;
import com.chikere.futureai.report.professional.web.ProfessionalReportViewMapper;
import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.security.WebSecurityConfiguration;
import com.chikere.futureai.cv.professional.web.ProfessionalCvSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfessionalAssessmentController.class)
@Import({WebSecurityConfiguration.class, ProfessionalAssessmentFormMapper.class, ProfessionalReportViewMapper.class})
class ProfessionalAssessmentControllerTest {
    private static final UUID ID = UUID.fromString("30eff44e-eaa0-4d2e-a853-90983b1c150a");
    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalAssessmentWorkflow workflow;
    @MockitoBean ProfessionalReportService reportService;
    @MockitoBean ProfessionalReportPurchaseService purchaseService;

    @BeforeEach
    void purchaseState() {
        when(purchaseService.accessFor(any())).thenReturn(new ProfessionalPurchaseAccess(
                Optional.empty(), false, Optional.empty()));
    }

    @Test
    void formIsPublicAndValidationOrCsrfFailureNeverCallsWorkflow() throws Exception {
        mvc.perform(get("/professional-assessment")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("What do you actually do?")));
        mvc.perform(validPost()).andExpect(status().isForbidden());
        mvc.perform(post("/professional-assessment").with(csrf()).param("profession", " "))
                .andExpect(status().isOk()).andExpect(view().name("professional-assessment/form"));
        verifyNoInteractions(workflow, reportService);
    }

    @Test
    void validPostUsesWorkflowOnceAndRedirects() throws Exception {
        when(workflow.submit(any(), any())).thenReturn(ID);
        mvc.perform(validPost().with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/professional-assessment/" + ID));
        ArgumentCaptor<ProfessionalAssessmentState> state = ArgumentCaptor.forClass(ProfessionalAssessmentState.class);
        ArgumentCaptor<ProfessionalAssessmentInputMetadata> metadata =
                ArgumentCaptor.forClass(ProfessionalAssessmentInputMetadata.class);
        verify(workflow).submit(state.capture(), metadata.capture());
        assertThat(state.getValue().profession()).isEqualTo("Software Architect");
        assertThat(metadata.getValue()).isEqualTo(ProfessionalAssessmentInputMetadata.manual());
    }

    @Test
    void reviewedCvValuesUseTrustedSessionProvenanceAndStillSubmitOnlyOnce() throws Exception {
        var session = new MockHttpSession();
        var metadata = ProfessionalAssessmentInputMetadata.cvUpload("safe-cv.pdf", "application/pdf",
                "professional-cv-parser-v1", Instant.parse("2026-10-01T08:00:00Z"));
        session.setAttribute(ProfessionalCvSession.INPUT_METADATA, metadata);
        when(workflow.submit(any(), any())).thenReturn(ID);

        mvc.perform(validPost().session(session).with(csrf()).with(request -> {
                    request.setParameter("profession", "Edited Architect");
                    return request;
                }))
                .andExpect(status().is3xxRedirection());

        ArgumentCaptor<ProfessionalAssessmentState> state = ArgumentCaptor.forClass(ProfessionalAssessmentState.class);
        ArgumentCaptor<ProfessionalAssessmentInputMetadata> provenance =
                ArgumentCaptor.forClass(ProfessionalAssessmentInputMetadata.class);
        verify(workflow).submit(state.capture(), provenance.capture());
        assertThat(state.getValue().profession()).isEqualTo("Edited Architect");
        assertThat(provenance.getValue()).isEqualTo(metadata);
        assertThat(session.getAttribute(ProfessionalCvSession.INPUT_METADATA)).isNull();
    }

    @Test
    void freeResultIsUsefulButExcludesPaidNarrativeActionsAndEvidence() throws Exception {
        when(reportService.assemble(ID, ProfessionalReportTier.FREE)).thenReturn(Optional.of(report(
                ProfessionalReportTier.FREE, true, true)));
        String html = mvc.perform(get("/professional-assessment/" + ID)).andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/result"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Free professional assessment", "Software Architect", "AI Exposure",
                "Human Advantage", "AI Complementarity", "Role Adaptability", "What increases AI exposure",
                "What preserves human value", "Short interpretation", "not a probability that you will lose your job",
                "Unlock the full personalised report", "Paid report checkout is not currently available",
                "&lt;script&gt;meaning&lt;/script&gt;");
        assertThat(html).doesNotContain("Why AI may affect your work", "What to focus on next",
                "Skill-development priorities", "Illustrative evidence title", "Premium action one",
                "secret-provider", "/report.pdf", "Download PDF");
        verify(reportService).assemble(ID, ProfessionalReportTier.FREE);
    }

    @Test
    void missingNarrativeStillShowsDeterministicFreeAssessment() throws Exception {
        when(reportService.assemble(ID, ProfessionalReportTier.FREE)).thenReturn(Optional.of(report(
                ProfessionalReportTier.FREE, false, false)));
        String html = mvc.perform(get("/professional-assessment/" + ID)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("AI Exposure", "deterministic assessment is complete",
                "Paid report checkout is not currently available");
    }

    @Test
    void unknownAndMalformedIdsReturnNotFoundAndGetNeverRegeneratesAnything() throws Exception {
        mvc.perform(get("/professional-assessment/" + ID)).andExpect(status().isNotFound());
        mvc.perform(get("/professional-assessment/not-a-uuid")).andExpect(status().isNotFound());
        verify(reportService).assemble(ID, ProfessionalReportTier.FREE);
        verifyNoInteractions(workflow);
    }

    @Test
    void submissionFailureDoesNotExposeInternalDetails() throws Exception {
        when(workflow.submit(any(), any())).thenThrow(new IllegalStateException("secret provider exception"));
        String html = mvc.perform(validPost().with(csrf())).andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("couldn't complete your assessment right now")
                .doesNotContain("secret provider exception", "IllegalStateException");
    }

    private ProfessionalReport report(ProfessionalReportTier tier, boolean narrative, boolean fixture) {
        var source = new ProfessionalReportSource(snapshot(fixture), narrative ? Optional.of(storedNarrative()) : Optional.empty(),
                Optional.of(evidence()), Optional.empty());
        return new ProfessionalReportAssembler().assemble(source, tier);
    }

    private StoredProfessionalAssessment snapshot(boolean fixture) {
        var state = new ProfessionalAssessmentState("Software Architect", "Designs complex software systems.",
                List.of("Design systems", "Resolve trade-offs"), List.of("Architecture"), OptionalInt.of(14));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        return new StoredProfessionalAssessment(ID, Instant.parse("2026-09-30T12:00:00Z"), state, judgments,
                new ProfessionalAssessmentResult(decision, fixture ? AssessmentSource.LOCAL_FIXTURE : AssessmentSource.JEV,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }

    private StoredProfessionalNarrative storedNarrative() {
        var narrative = new ProfessionalNarrative("Headline", "A useful saved summary", "<script>meaning</script>",
                "Premium exposure detail", "Premium human detail", "Premium complementarity detail",
                "Premium adaptability detail", List.of("Premium action one", "Premium action two", "Premium action three"),
                "Premium caution");
        Instant time = Instant.parse("2026-09-30T12:01:00Z");
        return new StoredProfessionalNarrative(ID, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                NarrativeSource.LOCAL_FIXTURE, "secret-provider", Optional.empty(), "professional-narrative-v1",
                time, Optional.of(time));
    }

    private com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceBundle evidence() {
        Instant time = Instant.parse("2026-09-30T12:02:00Z");
        var item = new com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceItem(
                com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceCategory.OCCUPATION_OUTLOOK,
                "Illustrative evidence title", "Saved factual summary", "Local fixture evidence", Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.of("United Kingdom"), Optional.empty(), time,
                Optional.empty(), Optional.empty(), Optional.empty(),
                com.chikere.futureai.evidence.professional.application.EvidenceSourceQuality.DEVELOPMENT_FIXTURE,
                "Context only");
        return new com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceBundle(ID,
                "professional-evidence-v1", com.chikere.futureai.evidence.professional.application.EvidenceSource.LOCAL_FIXTURE,
                "secret-provider", com.chikere.futureai.evidence.professional.application.EvidenceStatus.AVAILABLE,
                time, List.of(item));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder validPost() {
        return post("/professional-assessment").param("profession", "Software Architect")
                .param("workDescription", "I design complex software systems, resolve constraints and guide engineers.")
                .param("responsibilities", "Design systems\nResolve trade-offs")
                .param("skills", "Architecture\nCommunication").param("yearsOfExperience", "14");
    }
}
