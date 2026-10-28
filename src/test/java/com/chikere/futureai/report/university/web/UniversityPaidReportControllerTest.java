package com.chikere.futureai.report.university.web;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.university.application.NarrativeSource;
import com.chikere.futureai.narrative.university.application.NarrativeStatus;
import com.chikere.futureai.narrative.university.application.StoredUniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrative;
import com.chikere.futureai.payment.university.application.UniversityEntitlementStatus;
import com.chikere.futureai.payment.university.application.UniversityPurchaseAccess;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.report.university.application.UniversityReport;
import com.chikere.futureai.report.university.application.UniversityReportAssembler;
import com.chikere.futureai.report.university.application.UniversityReportSource;
import com.chikere.futureai.report.university.application.UniversityReportTier;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisUnavailableException;
import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisUnavailableException.Reason;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportFixtures;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportUseCase;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(UniversityPaidReportController.class)
@Import({WebSecurityConfiguration.class, UniversityReportViewMapper.class})
class UniversityPaidReportControllerTest {

    private static final UUID ID = UUID.fromString("2e3f4a5b-6c7d-4e9f-8a0b-1c2d3e4f5a6b");

    @Autowired MockMvc mvc;
    @MockitoBean UniversityReportPurchaseUseCase purchaseService;
    @MockitoBean UniversityDetailedReportUseCase detailedReports;

    @Test
    void anActiveEntitlementRendersOnlyTheFrozenPurchasedSnapshot() throws Exception {
        when(purchaseService.findPurchasedReport(ID)).thenReturn(Optional.of(frozenPaidReport()));

        String html = mvc.perform(get("/university-assessment/" + ID + "/report"))
                .andExpect(status().isOk()).andExpect(view().name("university-assessment/paid-report"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Your detailed university course report", "Computer Science",
                "Future Readiness", "Saved readiness detail", "Premium action one", "Skills worth developing",
                "Career areas you mentioned", "Download PDF", "university-report-v1");
        assertThat(html).doesNotContain("future-proof", "You should become", "bad degree",
                "guaranteed", "Role Adaptability", "Human Advantage");
        // AI Exposure appears exactly once, inside the explicit cross-journey separation caveat.
        assertThat(html.split("AI Exposure", -1)).hasSize(2);
        assertThat(html).contains("University Future Readiness and professional AI Exposure are different "
                + "constructs on different scales");
    }

    @Test
    void withoutAnActivePurchaseAccessIsForbiddenAndNoReportIsRendered() throws Exception {
        when(purchaseService.findPurchasedReport(ID)).thenReturn(Optional.empty());
        when(purchaseService.accessFor(any())).thenReturn(new UniversityPurchaseAccess(
                Optional.of(UniversityEntitlementStatus.PENDING), false, Optional.empty()));

        String html = mvc.perform(get("/university-assessment/" + ID + "/report"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("university-assessment/paid-report-unavailable"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Payment confirmation is pending")
                .doesNotContain("Saved readiness detail", "Premium action one");
    }

    @Test
    void aMalformedIdIsNotFound() throws Exception {
        mvc.perform(get("/university-assessment/not-a-uuid/report")).andExpect(status().isNotFound());
    }

    // ---- university-report-v2 ----

    private void v2Purchase() {
        when(purchaseService.activePurchaseVersion(ID)).thenReturn(Optional.of("university-report-v2"));
    }

    @Test
    void aStoredV2ReportRendersEverySectionWithoutDiagnostics() throws Exception {
        v2Purchase();
        when(detailedReports.find(ID)).thenReturn(Optional.of(UniversityDetailedReportFixtures.stored(ID)));

        String html = mvc.perform(get("/university-assessment/" + ID + "/report"))
                .andExpect(status().isOk()).andExpect(view().name("university-assessment/paid-report-v2"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Your course profile in an AI-shaped world", "Working Alongside AI", "5.20",
                "What this means", "Your modules", "Clinical Practice", "Where AI can help your learning",
                "This does not mean AI use is permitted in assessed work", "Where your own capability matters most",
                "Skills to strengthen", "Safeguards", "Your action plan", "This year",
                "Perspective and limitations", "Download PDF");
        assertThat(html).doesNotContain("Technical and analytical foundations", "Add the personalised explanation",
                "university-paid-narrative", "AVAILABLE", "Future Readiness", "Overall score");
        verify(detailedReports, never()).prepare(any(), any());
        verify(purchaseService, never()).findPurchasedReport(any());
    }

    @Test
    void aProvisionalV2ReportIsShownWithARetryAndNoPdfLink() throws Exception {
        v2Purchase();
        when(detailedReports.find(ID)).thenReturn(Optional.empty());
        when(detailedReports.provisional(ID)).thenReturn(Optional.of(UniversityDetailedReportFixtures.report()));

        String html = mvc.perform(get("/university-assessment/" + ID + "/report").param("preparation", "failed"))
                .andExpect(status().isOk()).andExpect(view().name("university-assessment/paid-report-v2"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Add the personalised explanation", "We still could not add",
                "Your action plan").doesNotContain("Download PDF");
        verify(detailedReports, never()).prepare(any(), any());
    }

    @Test
    void moreThanEightModulesShowTheSelectionFormInCourseOrderWithNothingPreselected() throws Exception {
        v2Purchase();
        var modules = java.util.stream.IntStream.rangeClosed(1, 10).mapToObj(index -> "Module " + index).toList();
        when(detailedReports.preparation(ID)).thenReturn(Optional.of(
                new UniversityDetailedReportUseCase.Preparation(false, false, true, modules, 8)));

        String html = mvc.perform(get("/university-assessment/" + ID + "/report")
                        .param("preparation", "invalid-selection"))
                .andExpect(status().isOk()).andExpect(view().name("university-assessment/paid-report-v2-prepare"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Choose up to 8 modules", "Choose between one and 8 modules",
                "name=\"selectionForm\"").containsSubsequence("Module 1", "Module 2", "Module 9", "Module 10");
        assertThat(html.split("type=\"checkbox\"", -1)).hasSize(11);
        assertThat(html).doesNotContain("checked");
        verify(detailedReports, never()).prepare(any(), any());
    }

    @Test
    void eightOrFewerModulesHaveNoSelectionStep() throws Exception {
        v2Purchase();
        when(detailedReports.preparation(ID)).thenReturn(Optional.of(new UniversityDetailedReportUseCase.Preparation(
                false, false, false, List.of("Module 1", "Module 2"), 8)));

        String html = mvc.perform(get("/university-assessment/" + ID + "/report"))
                .andExpect(view().name("university-assessment/paid-report-v2-prepare"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Prepare your detailed report").doesNotContain("type=\"checkbox\"");
    }

    @Test
    void aValidSelectionIsPassedToTheServiceAndRedirectsToTheReport() throws Exception {
        when(detailedReports.provisional(ID)).thenReturn(Optional.empty());
        when(detailedReports.prepare(eq(ID), any())).thenReturn(new UniversityDetailedReportUseCase.Outcome(
                Optional.of(UniversityDetailedReportFixtures.stored(ID)), UniversityDetailedReportFixtures.report(),
                false));

        mvc.perform(post("/university-assessment/" + ID + "/report/prepare").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("selectionForm", "true").param("module", "9", "2", "5"))
                .andExpect(redirectedUrl("/university-assessment/" + ID + "/report"));

        verify(detailedReports).prepare(ID, Optional.of(List.of(9, 2, 5)));
    }

    @Test
    void anEmptySelectionFormIsAnInvalidSelectionNotNoSelection() throws Exception {
        when(detailedReports.prepare(eq(ID), any())).thenThrow(
                new UniversityModuleAnalysisUnavailableException(Reason.INVALID_SELECTION, "empty"));

        mvc.perform(post("/university-assessment/" + ID + "/report/prepare").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .param("selectionForm", "true"))
                .andExpect(redirectedUrl("/university-assessment/" + ID + "/report?preparation=invalid-selection"));

        verify(detailedReports).prepare(ID, Optional.of(List.of()));
    }

    @Test
    void preparationErrorsMapToTheirPageStatuses() throws Exception {
        when(detailedReports.prepare(eq(ID), any())).thenThrow(
                new UniversityModuleAnalysisUnavailableException(Reason.MODULE_SELECTION_REQUIRED, "choose"));
        mvc.perform(post("/university-assessment/" + ID + "/report/prepare").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(redirectedUrl("/university-assessment/" + ID + "/report?preparation=selection-required"));
        verify(detailedReports).prepare(ID, Optional.empty());

        when(detailedReports.prepare(eq(ID), any())).thenThrow(new IllegalStateException("provider detail"));
        mvc.perform(post("/university-assessment/" + ID + "/report/prepare").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(redirectedUrl("/university-assessment/" + ID + "/report?preparation=failed"));
    }

    @Test
    void preparingWithoutAnActiveEntitlementIsForbidden() throws Exception {
        when(detailedReports.prepare(eq(ID), any())).thenThrow(
                new UniversityModuleAnalysisUnavailableException(Reason.NOT_ENTITLED, "no"));
        when(purchaseService.accessFor(any())).thenReturn(new UniversityPurchaseAccess(
                Optional.of(UniversityEntitlementStatus.PENDING), false, Optional.empty()));

        mvc.perform(post("/university-assessment/" + ID + "/report/prepare").with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(view().name("university-assessment/paid-report-unavailable"));
    }

    @Test
    void aHistoricalV1PurchaseNeverReachesTheV2Lifecycle() throws Exception {
        when(purchaseService.activePurchaseVersion(ID)).thenReturn(Optional.of("university-report-v1"));
        when(purchaseService.findPurchasedReport(ID)).thenReturn(Optional.of(frozenPaidReport()));

        mvc.perform(get("/university-assessment/" + ID + "/report"))
                .andExpect(status().isOk()).andExpect(view().name("university-assessment/paid-report"));

        verify(detailedReports, never()).find(any());
        verify(detailedReports, never()).preparation(any());
        verify(detailedReports, never()).prepare(any(), any());
    }

    private UniversityReport frozenPaidReport() {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of("Software engineering"),
                Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        var assessment = new StoredUniversityAssessment(ID, Instant.parse("2026-10-01T12:00:00Z"), state,
                judgments, new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        var narrative = new UniversityNarrative("Headline", "Summary", "Meaning", "Saved readiness detail",
                "Saved strengths detail", "Saved development detail", "Saved complementarity detail",
                "Saved transferability detail", "Saved adaptability detail",
                List.of("Premium action one", "Premium action two", "Premium action three"), "Caution");
        Instant time = Instant.parse("2026-10-01T12:01:00Z");
        return new UniversityReportAssembler().assemble(new UniversityReportSource(assessment,
                Optional.of(new StoredUniversityNarrative(ID, NarrativeStatus.AVAILABLE,
                        Optional.of(narrative), NarrativeSource.LOCAL_FIXTURE, "local-fixture",
                        Optional.empty(), "university-narrative-v1", time, Optional.of(time))),
                Optional.empty()), UniversityReportTier.PAID);
    }
}
