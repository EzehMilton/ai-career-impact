package com.chikere.futureai.assessment.alevel.web;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentUseCase;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceBundle;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceCategory;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceItem;
import com.chikere.futureai.evidence.alevel.application.EvidenceSource;
import com.chikere.futureai.evidence.alevel.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.alevel.application.EvidenceStatus;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrative;
import com.chikere.futureai.narrative.alevel.application.NarrativeSource;
import com.chikere.futureai.narrative.alevel.application.NarrativeStatus;
import com.chikere.futureai.narrative.alevel.application.StoredALevelNarrative;
import com.chikere.futureai.payment.alevel.application.ALevelPurchaseAccess;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import com.chikere.futureai.report.alevel.application.ALevelReport;
import com.chikere.futureai.report.alevel.application.ALevelReportAssembler;
import com.chikere.futureai.report.alevel.application.ALevelReportQuery;
import com.chikere.futureai.report.alevel.application.ALevelReportSource;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import com.chikere.futureai.report.alevel.web.ALevelReportViewMapper;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ALevelAssessmentController.class)
@Import({WebSecurityConfiguration.class, ALevelAssessmentFormMapper.class, ALevelReportViewMapper.class})
class ALevelAssessmentControllerTest {

    private static final UUID ID = UUID.fromString("5c6d7e8f-9a0b-4c1d-8e2f-3a4b5c6d7e8f");

    @Autowired MockMvc mvc;
    @MockitoBean ALevelAssessmentUseCase workflow;
    @MockitoBean ALevelReportQuery reportService;
    @MockitoBean ALevelReportPurchaseUseCase purchaseService;

    @BeforeEach
    void purchaseState() {
        when(purchaseService.accessFor(any()))
                .thenReturn(new ALevelPurchaseAccess(Optional.empty(), false, Optional.empty()));
    }

    @Test
    void theFormIsPublicUsesStudentFriendlyLanguageAndNeverDemandsCareerCertainty() throws Exception {
        String html = mvc.perform(get("/a-level-assessment")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Your A-level subjects", "Predicted grades",
                "What do you enjoy or feel strongest at?", "Degree areas you are considering",
                "It is completely fine to be undecided",
                "There are no good or bad subjects here",
                "You do not need to know what you want to do yet");
        // "no good or bad subjects here" is deliberate reassurance, so only verdict phrasing is banned.
        assertThat(html).doesNotContain("AI Exposure", "Future Readiness", "future-proof",
                "wrong subjects", "will limit your future", "you won't get");
        verifyNoInteractions(workflow, reportService);
    }

    @Test
    void validationOrMissingCsrfNeverReachesTheWorkflow() throws Exception {
        mvc.perform(validPost()).andExpect(status().isForbidden());
        mvc.perform(post("/a-level-assessment").with(csrf()).param("selectedSubjects", " "))
                .andExpect(status().isOk()).andExpect(view().name("a-level-assessment/form"));
        verifyNoInteractions(workflow, reportService);
    }

    @Test
    void tooFewSubjectsIsAFriendlyFormErrorRatherThanAServerError() throws Exception {
        String html = mvc.perform(post("/a-level-assessment").with(csrf())
                        .param("selectedSubjects", "Mathematics"))
                .andExpect(status().isOk()).andExpect(view().name("a-level-assessment/form"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Please list at least two different A-level subjects");
        assertThat(html).doesNotContain("couldn't complete your check", "IllegalArgumentException");
        verifyNoInteractions(workflow);
    }

    @Test
    void aValidPostCallsTheWorkflowOnceAndRedirects() throws Exception {
        when(workflow.submit(any())).thenReturn(ID);

        mvc.perform(validPost().with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/a-level-assessment/" + ID));

        ArgumentCaptor<ALevelAssessmentState> state = ArgumentCaptor.forClass(ALevelAssessmentState.class);
        verify(workflow).submit(state.capture());
        assertThat(state.getValue().selectedSubjects())
                .containsExactly("Mathematics", "Economics", "Geography");
        assertThat(state.getValue().predictedGrades()).containsEntry("Mathematics", "A");
        assertThat(state.getValue().preferredDegreeAreas()).containsExactly("Economics");
    }

    @Test
    void theFreeResultIsUsefulButExcludesEveryPaidSectionAndEscapesSavedText() throws Exception {
        when(reportService.assemble(ID, ALevelReportTier.FREE))
                .thenReturn(Optional.of(report(ALevelReportTier.FREE, true, true)));

        String html = mvc.perform(get("/a-level-assessment/" + ID)).andExpect(status().isOk())
                .andExpect(view().name("a-level-assessment/result"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("noindex,nofollow")))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Free A-level subject check", "Mathematics, Economics, Geography",
                "Future Optionality", "Degree Pathway Breadth", "Analytical Foundation",
                "Human and Creative Strength", "Skill Transferability", "AI-Era Adaptability",
                "What Future Optionality means", "not a prediction of university admission",
                "Unlock the full A-level report", "&lt;script&gt;meaning&lt;/script&gt;",
                "check that course's own published subject requirements");
        assertThat(html).doesNotContain("Premium optionality detail", "Premium action one",
                "Skills worth developing alongside your subjects", "Illustrative requirement note",
                "secret-provider", "/report.pdf", "Download PDF",
                "AI Exposure", "Future Readiness", "future-proof", "wrong subject",
                "You will struggle", "will limit your future");
    }

    @Test
    void aMissingNarrativeStillShowsTheDeterministicResult() throws Exception {
        when(reportService.assemble(ID, ALevelReportTier.FREE))
                .thenReturn(Optional.of(report(ALevelReportTier.FREE, false, false)));

        String html = mvc.perform(get("/a-level-assessment/" + ID)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Future Optionality", "personalised explanation is currently unavailable");
    }

    @Test
    void unknownAndMalformedIdsReturnNotFoundAndTheGetNeverRerunsAnything() throws Exception {
        mvc.perform(get("/a-level-assessment/" + ID)).andExpect(status().isNotFound())
                .andExpect(view().name("a-level-assessment/not-found"));
        mvc.perform(get("/a-level-assessment/not-a-uuid")).andExpect(status().isNotFound());

        verify(reportService).assemble(ID, ALevelReportTier.FREE);
        verifyNoInteractions(workflow);
    }

    @Test
    void aSubmissionFailureNeverExposesInternalDetail() throws Exception {
        when(workflow.submit(any())).thenThrow(new IllegalStateException("secret provider exception"));

        String html = mvc.perform(validPost().with(csrf())).andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("couldn't complete your check right now")
                .doesNotContain("secret provider exception", "IllegalStateException");
    }

    private ALevelReport report(ALevelReportTier tier, boolean narrative, boolean evidence) {
        return new ALevelReportAssembler().assemble(new ALevelReportSource(snapshot(),
                narrative ? Optional.of(storedNarrative()) : Optional.empty(),
                evidence ? Optional.of(evidenceBundle()) : Optional.empty()), tier);
    }

    private StoredALevelAssessment snapshot() {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of("Mathematics", "A"), List.of(), List.of("Economics"), List.of(),
                Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureALevelJudgmentProvider("maths_economics_geography").judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        return new StoredALevelAssessment(ID, Instant.parse("2026-10-01T12:00:00Z"), state, judgments,
                new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }

    private StoredALevelNarrative storedNarrative() {
        var narrative = new ALevelNarrative("Headline", "A useful saved summary",
                "<script>meaning</script>", "Premium optionality detail", "Premium strengths detail",
                "Premium pathway detail", "Premium analytical detail", "Premium human detail",
                "Premium transferability detail", "Premium adaptability detail",
                "Premium development detail",
                List.of("Premium action one", "Premium action two", "Premium action three"),
                "Premium caution");
        Instant time = Instant.parse("2026-10-01T12:01:00Z");
        return new StoredALevelNarrative(ID, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                NarrativeSource.LOCAL_FIXTURE, "secret-provider", Optional.empty(),
                "a-level-narrative-v1", time, Optional.of(time));
    }

    private ALevelEvidenceBundle evidenceBundle() {
        Instant time = Instant.parse("2026-10-01T12:02:00Z");
        var item = new ALevelEvidenceItem(ALevelEvidenceCategory.DEGREE_PREREQUISITES,
                "Illustrative requirement note", "Saved factual summary", "Local fixture evidence",
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("United Kingdom"),
                Optional.empty(), time, Optional.empty(), Optional.empty(), Optional.empty(),
                EvidenceSourceQuality.DEVELOPMENT_FIXTURE, "Context only");
        return new ALevelEvidenceBundle(ID, "a-level-evidence-v1", EvidenceSource.LOCAL_FIXTURE,
                "secret-provider", EvidenceStatus.AVAILABLE, time, List.of(item));
    }

    private MockHttpServletRequestBuilder validPost() {
        return post("/a-level-assessment")
                .param("selectedSubjects", "Mathematics\nEconomics\nGeography")
                .param("predictedGrades", "Mathematics: A")
                .param("preferredDegreeAreas", "Economics");
    }
}
