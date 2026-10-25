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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
    @MockitoBean ProfessionalAssessmentUseCase workflow;
    @MockitoBean ProfessionalReportQuery reportService;
    @MockitoBean ProfessionalReportPurchaseUseCase purchaseService;

    @BeforeEach
    void purchaseState() {
        when(purchaseService.accessFor(any())).thenReturn(new ProfessionalPurchaseAccess(
                Optional.empty(), false, Optional.empty()));
    }

    @Test
    void formIsPublicAndValidationOrCsrfFailureNeverCallsWorkflow() throws Exception {
        mvc.perform(get("/professional-assessment")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("what you actually do")));
        mvc.perform(validPost()).andExpect(status().isForbidden());
        mvc.perform(post("/professional-assessment").with(csrf()).param("profession", " "))
                .andExpect(status().isOk()).andExpect(view().name("professional-assessment/form"));
        verifyNoInteractions(workflow, reportService);
    }

    @Test
    void examplePageIsPublicAndInformationalOnly() throws Exception {
        mvc.perform(get("/professional-assessment/example")).andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/professional-example"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "What a useful completed form can look like")));
        verifyNoInteractions(workflow, reportService, purchaseService);
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
    void freeResultIsUsefulButExcludesPaidNarrativeActionsAndEvidence() throws Exception {
        when(reportService.assemble(ID, ProfessionalReportTier.FREE)).thenReturn(Optional.of(report(
                ProfessionalReportTier.FREE, true, true)));
        String html = mvc.perform(get("/professional-assessment/" + ID)).andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/result"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Free professional assessment", "Software Architect", "AI Exposure",
                "Human Advantage", "AI Complementarity", "Role Adaptability", "What increases AI Exposure",
                "What reduces AI Exposure", "A useful saved summary",
                "not a probability that you will lose your job",
                "Go further with your personalised report", "Paid report checkout is not currently available",
                "&lt;script&gt;meaning&lt;/script&gt;");
        assertThat(html).doesNotContain("Why AI may affect your work", "What to focus on next",
                "Skill-development priorities", "Illustrative evidence title", "Premium action one",
                "secret-provider", "/report.pdf", "Download PDF");
        // Customer-facing copy: a plain status, "Score" rather than "Indicator", natural band labels, the
        // neutral measure descriptions, and no confidence values or generated caution while confidence is redesigned.
        assertThat(html).contains("Your results at a glance", "Your assessment scores", "Why you received these scores",
                "Each score describes a different characteristic of your work.",
                "<dd>Assessment complete</dd>", "<dt>Score</dt>",
                ">Moderate<", ">High<",
                "How much the work overlaps with characteristics AI can perform through digital systems.");
        assertThat(html).doesNotContain("Assessment confidence", "<dt>Confidence</dt>", "Confidence describes",
                "<dt>Indicator</dt>", "Each indicator", ">MODERATE<", ">HIGH<", ">Ready<", "Premium caution",
                "protective factors", "Where human skills remain important", "ratings</", "rating describes",
                "What increases AI exposure");
        assertThat(html).doesNotContainPattern("<dd>\\d+%</dd>");
        // The free-versus-paid comparison promises only what the detailed report contains today.
        assertThat(html).containsSubsequence("Free assessment", "Your four assessment scores",
                "The main factors behind them", "A short explanation of what they mean", "Detailed report — £4.99",
                "Everything in your free assessment", "A deeper explanation of your results", "Practical next steps",
                "Skills to prioritise", "Guidance for your work", "Downloadable PDF");
        assertThat(html).doesNotContain("career context", "Verified career context", "External career context",
                "Free assessment — included");
        verify(reportService).assemble(ID, ProfessionalReportTier.FREE);
    }

    @Test
    void theCheckoutButtonShowsTheProfessionalPriceAndTheAccessCodeIsASecondaryDisclosure() throws Exception {
        when(purchaseService.accessFor(any())).thenReturn(new ProfessionalPurchaseAccess(
                Optional.empty(), true, Optional.empty(), true));
        when(reportService.assemble(ID, ProfessionalReportTier.FREE)).thenReturn(Optional.of(report(
                ProfessionalReportTier.FREE, true, true)));

        String html = mvc.perform(get("/professional-assessment/" + ID)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<button type=\"submit\">Get my detailed report — £4.99</button>");
        // Collapsed by default behind "Have an access code?"; the form itself, its field and route are unchanged.
        assertThat(html).containsSubsequence("<details class=\"access-code-disclosure\">",
                "<summary>Have an access code?</summary>",
                "action=\"/professional-assessment/" + ID + "/complimentary-access\"", "name=\"_csrf\"",
                "name=\"accessCode\"", "</details>");
        assertThat(html).doesNotContain("Price:");
    }

    @Test
    void aReadyWithCautionAssessmentIsShownToTheVisitorSimplyAsComplete() throws Exception {
        var source = com.chikere.futureai.report.professional.application.ProfessionalReportAssemblerTest.source(
                true, true, true, Map.of("information_sufficient", 0.7));
        assertThat(source.assessment().result().decision().assessmentDecision().status())
                .isEqualTo(AssessmentStatus.READY_WITH_CAUTION);
        when(reportService.assemble(source.assessment().id(), ProfessionalReportTier.FREE)).thenReturn(Optional.of(
                new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.FREE)));

        String html = mvc.perform(get("/professional-assessment/" + source.assessment().id()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<dd>Assessment complete</dd>");
        assertThat(html).doesNotContain("Ready with caution", "READY_WITH_CAUTION", "Assessment confidence");
    }

    @Test
    void missingNarrativeStillShowsDeterministicFreeAssessment() throws Exception {
        when(reportService.assemble(ID, ProfessionalReportTier.FREE)).thenReturn(Optional.of(report(
                ProfessionalReportTier.FREE, false, false)));
        String html = mvc.perform(get("/professional-assessment/" + ID)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("AI Exposure", "personalised explanation is currently unavailable",
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

    @Test
    void returningFromStripeWhilePendingShowsTheConfirmationPanelAndActivatesNothing() throws Exception {
        when(reportService.assemble(ID, ProfessionalReportTier.FREE)).thenReturn(Optional.of(report(ProfessionalReportTier.FREE, true, true)));
        when(purchaseService.accessFor(ID)).thenReturn(new ProfessionalPurchaseAccess(
                Optional.of(com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus.PENDING), false, Optional.empty()));

        String html = mvc.perform(get("/professional-assessment/" + ID).param("checkout", "returned"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Confirming your payment",
                "We are securely confirming your payment with Stripe. This normally takes only a few seconds.",
                "data-status-url=\"/professional-assessment/" + ID + "/payment-status\"",
                "data-report-url=\"/professional-assessment/" + ID + "/report\"",
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
        when(reportService.assemble(ID, ProfessionalReportTier.FREE)).thenReturn(Optional.of(report(ProfessionalReportTier.FREE, true, true)));
        when(purchaseService.accessFor(ID)).thenReturn(new ProfessionalPurchaseAccess(
                Optional.of(com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus.PENDING), false, Optional.empty()));

        String html = mvc.perform(get("/professional-assessment/" + ID)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("data-auto-poll=\"false\"", "Check payment status",
                "href=\"/professional-assessment/" + ID + "\"");
        assertThat(html).containsPattern("data-payment-state=\"confirming\"[^>]*hidden")
                .doesNotContainPattern("data-payment-state=\"delayed\"[^>]*hidden");
    }

    @Test
    void returningFromStripeAfterTheWebhookActivatedGoesStraightToThePaidReport() throws Exception {
        when(reportService.assemble(ID, ProfessionalReportTier.FREE)).thenReturn(Optional.of(report(ProfessionalReportTier.FREE, true, true)));
        when(purchaseService.accessFor(ID)).thenReturn(new ProfessionalPurchaseAccess(
                Optional.of(com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus.ACTIVE), false, Optional.empty()));

        mvc.perform(get("/professional-assessment/" + ID).param("checkout", "returned"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/professional-assessment/" + ID + "/report"));
        org.mockito.Mockito.verify(purchaseService).accessFor(ID);
        org.mockito.Mockito.verifyNoMoreInteractions(purchaseService);
    }

    @Test
    void aForgedStripeReturnWithoutAPurchaseUnlocksNothing() throws Exception {
        when(reportService.assemble(ID, ProfessionalReportTier.FREE)).thenReturn(Optional.of(report(ProfessionalReportTier.FREE, true, true)));

        String html = mvc.perform(get("/professional-assessment/" + ID).param("checkout", "returned"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("Confirming your payment", "data-payment-confirmation",
                "Open your purchased report", "payment-confirmation.js");
        org.mockito.Mockito.verify(purchaseService).accessFor(ID);
        org.mockito.Mockito.verifyNoMoreInteractions(purchaseService);
    }

    @Test
    void returningThroughStripesCancelLinkOffersASafeRetryAndActivatesNothing() throws Exception {
        when(reportService.assemble(ID, ProfessionalReportTier.FREE)).thenReturn(Optional.of(report(ProfessionalReportTier.FREE, true, true)));
        when(purchaseService.accessFor(ID)).thenReturn(new ProfessionalPurchaseAccess(
                Optional.of(com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus.PENDING), false, Optional.empty()));

        String html = mvc.perform(get("/professional-assessment/" + ID).param("checkout", "cancelled"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Payment was not completed.", "Your detailed report has not been unlocked.",
                "Try payment again", "action=\"/professional-assessment/" + ID + "/checkout\"", "name=\"_csrf\"",
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
        when(reportService.assemble(ID, ProfessionalReportTier.FREE)).thenReturn(Optional.of(report(ProfessionalReportTier.FREE, true, true)));

        String none = mvc.perform(get("/professional-assessment/" + ID).param("checkout", "cancelled"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(none).doesNotContain("Payment was not completed.", "data-payment-confirmation",
                "Open your purchased report");

        when(purchaseService.accessFor(ID)).thenReturn(new ProfessionalPurchaseAccess(
                Optional.of(com.chikere.futureai.payment.professional.application.ProfessionalEntitlementStatus.ACTIVE), false, Optional.empty()));
        String active = mvc.perform(get("/professional-assessment/" + ID).param("checkout", "cancelled"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // Cancelling never takes away a purchase the webhook has already confirmed.
        assertThat(active).contains("Open your purchased report").doesNotContain("Payment was not completed.");
        org.mockito.Mockito.verify(purchaseService, org.mockito.Mockito.times(2)).accessFor(ID);
        org.mockito.Mockito.verifyNoMoreInteractions(purchaseService);
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
