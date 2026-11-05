package com.chikere.futureai.assessment.university.web;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentUseCase;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.evidence.university.application.EvidenceSource;
import com.chikere.futureai.evidence.university.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.university.application.EvidenceStatus;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceBundle;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceCategory;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceItem;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.university.application.NarrativeSource;
import com.chikere.futureai.narrative.university.application.NarrativeStatus;
import com.chikere.futureai.narrative.university.application.StoredUniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrative;
import com.chikere.futureai.payment.university.application.UniversityPurchaseAccess;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.report.university.application.UniversityReport;
import com.chikere.futureai.report.university.application.UniversityReportAssembler;
import com.chikere.futureai.report.university.application.UniversityReportQuery;
import com.chikere.futureai.report.university.application.UniversityReportSource;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import com.chikere.futureai.report.university.web.UniversityReportViewMapper;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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

@WebMvcTest(UniversityAssessmentController.class)
@Import({WebSecurityConfiguration.class, UniversityAssessmentFormMapper.class,
        UniversityReportViewMapper.class})
class UniversityAssessmentControllerTest {

    private static final UUID ID = UUID.fromString("1d2e3f4a-5b6c-4d8e-9f0a-1b2c3d4e5f6a");

    @Autowired MockMvc mvc;
    @MockitoBean UniversityAssessmentUseCase workflow;
    @MockitoBean UniversityReportQuery reportService;
    @MockitoBean UniversityReportPurchaseUseCase purchaseService;

    @BeforeEach
    void purchaseState() {
        when(purchaseService.accessFor(any()))
                .thenReturn(new UniversityPurchaseAccess(Optional.empty(), false, Optional.empty()));
    }

    @Test
    void theFormIsPublicUsesStudentFriendlyLanguageAndRequiresNoWorkExperience() throws Exception {
        String html = mvc.perform(get("/university-assessment")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("What does the course cover?", "Key modules or topics",
                "Skills the course develops", "Career areas you're considering",
                "not your ability or your chances of getting a job");
        assertThat(html).doesNotContain("Years of experience", "job title", "your work",
                "AI Exposure", "future-proof");
        verifyNoInteractions(workflow, reportService);
    }

    @Test
    void theExamplePageIsPublicAndInformationalOnly() throws Exception {
        mvc.perform(get("/university-assessment/example")).andExpect(status().isOk())
                .andExpect(view().name("university-assessment/university-example"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "What a useful completed course form can look like")));
        verifyNoInteractions(workflow, reportService, purchaseService);
    }

    @Test
    void validationOrMissingCsrfNeverReachesTheWorkflow() throws Exception {
        mvc.perform(validPost()).andExpect(status().isForbidden());
        mvc.perform(post("/university-assessment").with(csrf()).param("courseTitle", " "))
                .andExpect(status().isOk()).andExpect(view().name("university-assessment/form"));
        mvc.perform(post("/university-assessment").with(csrf())
                        .param("courseTitle", "Computer Science").param("courseDescription", "Too short")
                        .param("keyModules", "Algorithms").param("skillsDeveloped", "Programming"))
                .andExpect(status().isOk()).andExpect(view().name("university-assessment/form"));
        verifyNoInteractions(workflow, reportService);
    }

    @Test
    void aValidPostCallsTheWorkflowOnceAndRedirects() throws Exception {
        when(workflow.submit(any())).thenReturn(ID);

        mvc.perform(validPost().with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/university-assessment/" + ID));

        ArgumentCaptor<UniversityAssessmentState> state =
                ArgumentCaptor.forClass(UniversityAssessmentState.class);
        verify(workflow).submit(state.capture());
        assertThat(state.getValue().courseTitle()).isEqualTo("Computer Science");
        assertThat(state.getValue().keyModules()).containsExactly("Algorithms", "Databases");
        assertThat(state.getValue().preferredCareerAreas()).containsExactly("Software engineering");
    }

    @Test
    void theFreeResultIsUsefulButExcludesEveryPaidSectionAndEscapesSavedText() throws Exception {
        when(reportService.assemble(ID, UniversityReportTier.FREE))
                .thenReturn(Optional.of(report(UniversityReportTier.FREE, true, true)));

        String html = mvc.perform(get("/university-assessment/" + ID)).andExpect(status().isOk())
                .andExpect(view().name("university-assessment/result"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("noindex,nofollow")))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Free university course assessment", "Computer Science", "Future Readiness",
                "AI Complementarity", "Human Differentiation", "Skill Transferability",
                "Career Adaptability", "Your results at a glance", "What Future Readiness means",
                "not a guarantee of employment", "Explore your course in more detail",
                "&lt;script&gt;meaning&lt;/script&gt;");
        assertThat(html).doesNotContain("Premium readiness detail", "Premium action one",
                "Skills worth developing alongside the course", "Illustrative evidence title",
                "secret-provider", "/report.pdf", "Download PDF",
                "AI Exposure", "future-proof", "bad degree", "You will struggle");
    }

    @Test
    void aV2AssessmentRendersTheThreeMeasureCourseProfileWithNoOverallHeadline() throws Exception {
        var v2 = com.chikere.futureai.coursemodule.university.analysis.ModuleAnalysisTestData
                .courseProfileAssessment(ID, 4);
        when(reportService.stored(ID)).thenReturn(Optional.of(v2));
        when(reportService.courseProfile(ID)).thenReturn(Optional.of(
                new com.chikere.futureai.report.university.application.UniversityCourseProfileComposer()
                        .compose(v2.result().profile())));

        String html = mvc.perform(get("/university-assessment/" + ID)).andExpect(status().isOk())
                .andExpect(view().name("university-assessment/result-v2"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Your course profile in an AI-shaped world", "BSc Nursing (Adult)",
                "Working Alongside AI", "Human and Practical Capability", "Transferable Capability",
                "Explore your course in more detail", "Development mode");
        assertThat(html.split("university-measure-card\"", -1)).hasSize(4);
        // Each measure is named once, by its heading; the result line shows only the score and the band word.
        var profile = new com.chikere.futureai.report.university.application.UniversityCourseProfileComposer()
                .compose(v2.result().profile());
        for (var measure : profile.measures()) {
            String band = measure.band().charAt(0) + measure.band().substring(1).toLowerCase();
            assertThat(html).doesNotContain(measure.bandLabel()).containsPattern("<h3>" + measure.name()
                    + "</h3>\\s*<p class=\"university-measure-result\"><span class=\"university-measure-score\">"
                    + "<strong>" + java.util.regex.Pattern.quote(measure.score()) + "</strong> / 10</span> "
                    + "<span class=\"university-measure-band-word\">" + band + "</span></p>");
        }
        assertThat(html).doesNotContain("Future Readiness", "AI Complementarity", "Human Differentiation",
                "Skill Transferability", "Career Adaptability", "Overall score", "strongest measure");
        verify(reportService, never()).assemble(any(), any());
    }

    @Test
    void aStoredV1AssessmentKeepsTheHistoricalV1ResultPage() throws Exception {
        when(reportService.stored(ID)).thenReturn(Optional.of(
                com.chikere.futureai.coursemodule.university.analysis.ModuleAnalysisTestData.assessment(ID, 4)));
        when(reportService.assemble(ID, UniversityReportTier.FREE))
                .thenReturn(Optional.of(report(UniversityReportTier.FREE, true, true)));

        String html = mvc.perform(get("/university-assessment/" + ID)).andExpect(status().isOk())
                .andExpect(view().name("university-assessment/result"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Future Readiness", "Career Adaptability")
                .doesNotContain("Your course profile in an AI-shaped world");
    }

    @Test
    void aMissingNarrativeStillShowsTheDeterministicAssessment() throws Exception {
        when(reportService.assemble(ID, UniversityReportTier.FREE))
                .thenReturn(Optional.of(report(UniversityReportTier.FREE, false, false)));

        String html = mvc.perform(get("/university-assessment/" + ID)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Future Readiness", "personalised explanation is currently unavailable");
    }

    @Test
    void unknownAndMalformedIdsReturnNotFoundAndTheGetNeverRerunsAnything() throws Exception {
        mvc.perform(get("/university-assessment/" + ID)).andExpect(status().isNotFound())
                .andExpect(view().name("university-assessment/not-found"));
        mvc.perform(get("/university-assessment/not-a-uuid")).andExpect(status().isNotFound());

        verify(reportService).assemble(ID, UniversityReportTier.FREE);
        verifyNoInteractions(workflow);
    }

    @Test
    void aSubmissionFailureNeverExposesInternalDetail() throws Exception {
        when(workflow.submit(any())).thenThrow(new IllegalStateException("secret provider exception"));

        String html = mvc.perform(validPost().with(csrf())).andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("couldn't complete your assessment right now")
                .doesNotContain("secret provider exception", "IllegalStateException");
    }

    @Test
    void returningFromStripeWhilePendingShowsTheConfirmationPanelAndActivatesNothing() throws Exception {
        when(reportService.assemble(ID, UniversityReportTier.FREE)).thenReturn(Optional.of(report(UniversityReportTier.FREE, true, true)));
        when(purchaseService.accessFor(ID)).thenReturn(new UniversityPurchaseAccess(
                Optional.of(com.chikere.futureai.payment.university.application.UniversityEntitlementStatus.PENDING), false, Optional.empty()));

        String html = mvc.perform(get("/university-assessment/" + ID).param("checkout", "returned"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Confirming your payment",
                "We are securely confirming your payment with Stripe. This normally takes only a few seconds.",
                "data-status-url=\"/university-assessment/" + ID + "/payment-status\"",
                "data-report-url=\"/university-assessment/" + ID + "/report\"",
                "data-auto-poll=\"true\"", "src=\"/js/payment-confirmation.js\"",
                "Payment confirmation is taking longer than expected",
                "You will not be charged again by checking the status.", "Check payment status")
                .doesNotContain("Payment confirmation pending", "Open your purchased report")
                .doesNotContainIgnoringCase("payment completed");
        assertThat(html).containsPattern("data-payment-state=\"delayed\"[^>]*hidden")
                .doesNotContainPattern("data-payment-state=\"confirming\"[^>]*hidden")
                .doesNotContainPattern("<script(?![^>]*\\ssrc=)[^>]*>");
        // Rendering the return page only reads the entitlement; nothing here can confirm a payment.
        org.mockito.Mockito.verify(purchaseService).accessFor(ID);
        org.mockito.Mockito.verifyNoMoreInteractions(purchaseService);
    }

    @Test
    void aPendingPurchaseSeenWithoutAStripeReturnOffersTheManualCheckInsteadOfPolling() throws Exception {
        when(reportService.assemble(ID, UniversityReportTier.FREE)).thenReturn(Optional.of(report(UniversityReportTier.FREE, true, true)));
        when(purchaseService.accessFor(ID)).thenReturn(new UniversityPurchaseAccess(
                Optional.of(com.chikere.futureai.payment.university.application.UniversityEntitlementStatus.PENDING), false, Optional.empty()));

        String html = mvc.perform(get("/university-assessment/" + ID)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("data-auto-poll=\"false\"", "Check payment status",
                "href=\"/university-assessment/" + ID + "\"");
        assertThat(html).containsPattern("data-payment-state=\"confirming\"[^>]*hidden")
                .doesNotContainPattern("data-payment-state=\"delayed\"[^>]*hidden");
    }

    @Test
    void returningFromStripeAfterTheWebhookActivatedGoesStraightToThePaidReport() throws Exception {
        when(reportService.assemble(ID, UniversityReportTier.FREE)).thenReturn(Optional.of(report(UniversityReportTier.FREE, true, true)));
        when(purchaseService.accessFor(ID)).thenReturn(new UniversityPurchaseAccess(
                Optional.of(com.chikere.futureai.payment.university.application.UniversityEntitlementStatus.ACTIVE), false, Optional.empty()));

        mvc.perform(get("/university-assessment/" + ID).param("checkout", "returned"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/university-assessment/" + ID + "/report"));
        org.mockito.Mockito.verify(purchaseService).accessFor(ID);
        org.mockito.Mockito.verifyNoMoreInteractions(purchaseService);
    }

    @Test
    void aForgedStripeReturnWithoutAPurchaseUnlocksNothing() throws Exception {
        when(reportService.assemble(ID, UniversityReportTier.FREE)).thenReturn(Optional.of(report(UniversityReportTier.FREE, true, true)));

        String html = mvc.perform(get("/university-assessment/" + ID).param("checkout", "returned"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("Confirming your payment", "data-payment-confirmation",
                "Open your purchased report", "payment-confirmation.js");
        org.mockito.Mockito.verify(purchaseService).accessFor(ID);
        org.mockito.Mockito.verifyNoMoreInteractions(purchaseService);
    }

    @Test
    void returningThroughStripesCancelLinkOffersASafeRetryAndActivatesNothing() throws Exception {
        when(reportService.assemble(ID, UniversityReportTier.FREE)).thenReturn(Optional.of(report(UniversityReportTier.FREE, true, true)));
        when(purchaseService.accessFor(ID)).thenReturn(new UniversityPurchaseAccess(
                Optional.of(com.chikere.futureai.payment.university.application.UniversityEntitlementStatus.PENDING), false, Optional.empty()));

        String html = mvc.perform(get("/university-assessment/" + ID).param("checkout", "cancelled"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Payment was not completed.", "Your detailed report has not been unlocked.",
                "Try payment again", "action=\"/university-assessment/" + ID + "/checkout\"", "name=\"_csrf\"",
                "data-auto-poll=\"false\"")
                .doesNotContain("Open your purchased report", "Payment confirmation pending")
                .doesNotContainIgnoringCase("payment completed");
        assertThat(html).doesNotContainPattern("data-payment-state=\"cancelled\"[^>]*hidden")
                .containsPattern("data-payment-state=\"confirming\"[^>]*hidden")
                .containsPattern("data-payment-state=\"delayed\"[^>]*hidden");
        org.mockito.Mockito.verify(purchaseService).accessFor(ID);
        org.mockito.Mockito.verifyNoMoreInteractions(purchaseService);
    }

    @Test
    void aForgedCancelReturnUnlocksNothingWhetherOrNotAPurchaseExists() throws Exception {
        when(reportService.assemble(ID, UniversityReportTier.FREE)).thenReturn(Optional.of(report(UniversityReportTier.FREE, true, true)));

        String none = mvc.perform(get("/university-assessment/" + ID).param("checkout", "cancelled"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(none).doesNotContain("Payment was not completed.", "data-payment-confirmation",
                "Open your purchased report");

        when(purchaseService.accessFor(ID)).thenReturn(new UniversityPurchaseAccess(
                Optional.of(com.chikere.futureai.payment.university.application.UniversityEntitlementStatus.ACTIVE), false, Optional.empty()));
        String active = mvc.perform(get("/university-assessment/" + ID).param("checkout", "cancelled"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // Cancelling never takes away a purchase the webhook has already confirmed.
        assertThat(active).contains("Open your purchased report").doesNotContain("Payment was not completed.");
        org.mockito.Mockito.verify(purchaseService, org.mockito.Mockito.times(2)).accessFor(ID);
        org.mockito.Mockito.verifyNoMoreInteractions(purchaseService);
    }

    private UniversityReport report(UniversityReportTier tier, boolean narrative, boolean evidence) {
        return new UniversityReportAssembler().assemble(new UniversityReportSource(snapshot(),
                narrative ? Optional.of(storedNarrative()) : Optional.empty(),
                evidence ? Optional.of(evidenceBundle()) : Optional.empty()), tier);
    }

    private StoredUniversityAssessment snapshot() {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of("Software engineering"),
                Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        return new StoredUniversityAssessment(ID, Instant.parse("2026-10-01T12:00:00Z"), state, judgments,
                new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }

    private StoredUniversityNarrative storedNarrative() {
        var narrative = new UniversityNarrative("Headline", "A useful saved summary",
                "<script>meaning</script>", "Premium readiness detail", "Premium strengths detail",
                "Premium development detail", "Premium complementarity detail",
                "Premium transferability detail", "Premium adaptability detail",
                List.of("Premium action one", "Premium action two", "Premium action three"),
                "Premium caution");
        Instant time = Instant.parse("2026-10-01T12:01:00Z");
        return new StoredUniversityNarrative(ID, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                NarrativeSource.LOCAL_FIXTURE, "secret-provider", Optional.empty(),
                "university-narrative-v1", time, Optional.of(time));
    }

    private UniversityEvidenceBundle evidenceBundle() {
        Instant time = Instant.parse("2026-10-01T12:02:00Z");
        var item = new UniversityEvidenceItem(UniversityEvidenceCategory.GRADUATE_OUTCOMES,
                "Illustrative evidence title", "Saved factual summary", "Local fixture evidence",
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("United Kingdom"),
                Optional.empty(), time, Optional.empty(), Optional.empty(), Optional.empty(),
                EvidenceSourceQuality.DEVELOPMENT_FIXTURE, "Context only");
        return new UniversityEvidenceBundle(ID, "university-evidence-v1", EvidenceSource.LOCAL_FIXTURE,
                "secret-provider", EvidenceStatus.AVAILABLE, time, List.of(item));
    }

    private MockHttpServletRequestBuilder validPost() {
        return post("/university-assessment")
                .param("courseTitle", "Computer Science")
                .param("courseDescription",
                        "Programming, algorithms, databases and a substantial final-year project.")
                .param("keyModules", "Algorithms\nDatabases")
                .param("skillsDeveloped", "Programming\nProblem solving")
                .param("preferredCareerAreas", "Software engineering");
    }
}
