package com.chikere.futureai.report.alevel.web;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrative;
import com.chikere.futureai.narrative.alevel.application.NarrativeSource;
import com.chikere.futureai.narrative.alevel.application.NarrativeStatus;
import com.chikere.futureai.narrative.alevel.application.StoredALevelNarrative;
import com.chikere.futureai.payment.alevel.application.ALevelEntitlementStatus;
import com.chikere.futureai.payment.alevel.application.ALevelPurchaseAccess;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import com.chikere.futureai.report.alevel.application.ALevelReport;
import com.chikere.futureai.report.alevel.application.ALevelReportAssembler;
import com.chikere.futureai.report.alevel.application.ALevelReportSource;
import com.chikere.futureai.report.alevel.application.ALevelReportTier;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportFixtures;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportUseCase;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ALevelPaidReportController.class)
@Import({WebSecurityConfiguration.class, ALevelReportViewMapper.class})
class ALevelPaidReportControllerTest {

    private static final UUID ID = UUID.fromString("7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b");

    @Autowired MockMvc mvc;
    @MockitoBean ALevelReportPurchaseUseCase purchaseService;
    @MockitoBean ALevelDetailedReportUseCase detailedReports;

    // ---- a-level-report-v2 ----

    private void v2Purchase() {
        when(purchaseService.activePurchaseVersion(ID)).thenReturn(Optional.of("a-level-report-v2"));
    }

    @Test
    void aStoredV2ReportRendersEverySectionWithAPdfLinkAndNoDiagnostics() throws Exception {
        v2Purchase();
        when(detailedReports.find(ID)).thenReturn(Optional.of(
                ALevelDetailedReportFixtures.stored(ID)));

        String html = mvc.perform(get("/a-level-assessment/" + ID + "/report"))
                .andExpect(status().isOk()).andExpect(view().name("a-level-assessment/paid-report-v2"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Your A-level subject profile", "Options Kept Open", "Capability Profile",
                "Learning Alongside AI", "What this combination gives you", "Options and applications",
                "What it cannot say", "Using AI to support your learning",
                "does not mean AI use is permitted in assessed work", "Skill priorities", "Your action plan",
                "During Year 13 and the rest of sixth form", "Applications and next-step decisions", "Safeguards",
                "Perspective and limitations", "Download PDF");
        assertThat(html).doesNotContain("Add the personalised explanation", "a-level-paid-narrative", "AVAILABLE",
                "provenance", "Future Optionality", "AI Career Impact Report", "currently unavailable",
                "Course information from published sources");
        verify(detailedReports, never()).prepare(any());
        verify(purchaseService, never()).findPurchasedReport(any());
    }

    @Test
    void aStoredV3ReportUsesPlainCustomerLabelsWithoutExposingLegacyHeadings() throws Exception {
        v2Purchase();
        when(detailedReports.find(ID)).thenReturn(Optional.of(ALevelDetailedReportFixtures.storedV3(ID)));

        String html = mvc.perform(get("/a-level-assessment/" + ID + "/report"))
                .andExpect(status().isOk()).andExpect(view().name("a-level-assessment/paid-report-v2"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Future study options", "Skills your subjects develop", "Maths and problem-solving",
                        "Writing, ideas and the wider world", "How AI can help you learn")
                .doesNotContain("Options Kept Open", "Capability Profile", "Analytical &amp; Quantitative",
                        "Communication, Interpretation &amp; Context", "Learning Alongside AI");
    }

    @Test
    void afterARetryableNarrativeTheDeterministicReportIsShownWithARetryAndNoPdf() throws Exception {
        v2Purchase();
        when(detailedReports.find(ID)).thenReturn(Optional.empty());
        when(detailedReports.provisional(ID)).thenReturn(Optional.of(
                ALevelDetailedReportFixtures.report("mixed_balanced")));

        String html = mvc.perform(get("/a-level-assessment/" + ID + "/report").param("preparation", "provisional"))
                .andExpect(status().isOk()).andExpect(view().name("a-level-assessment/paid-report-v2"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Add the personalised explanation", "Your action plan").doesNotContain("Download PDF");
        verify(detailedReports, never()).prepare(any());
    }

    @Test
    void anActiveV2PurchaseWithoutAReportShowsThePrepareStep() throws Exception {
        v2Purchase();
        when(detailedReports.find(ID)).thenReturn(Optional.empty());
        when(detailedReports.preparation(ID)).thenReturn(Optional.of(
                new ALevelDetailedReportUseCase.Preparation(false)));

        String html = mvc.perform(get("/a-level-assessment/" + ID + "/report"))
                .andExpect(status().isOk()).andExpect(view().name("a-level-assessment/paid-report-v2-prepare"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Prepare my detailed report", "it is prepared only once");
        verify(detailedReports, never()).prepare(any());
        verify(detailedReports, never()).provisional(any());
    }

    @Test
    void preparingRedirectsToTheStoredOrProvisionalReport() throws Exception {
        var stored = ALevelDetailedReportFixtures.stored(ID);
        when(detailedReports.prepare(ID)).thenReturn(new com.chikere.futureai.report.alevel.detailed
                .ALevelDetailedReportUseCase.Outcome(Optional.of(stored), stored.report(), false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/a-level-assessment/" + ID + "/report/prepare").with(org.springframework.security.test
                                .web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/a-level-assessment/" + ID + "/report"));

        when(detailedReports.prepare(ID)).thenReturn(new com.chikere.futureai.report.alevel.detailed
                .ALevelDetailedReportUseCase.Outcome(Optional.empty(), stored.report(), false));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/a-level-assessment/" + ID + "/report/prepare").with(org.springframework.security.test
                                .web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/a-level-assessment/" + ID + "/report?preparation=provisional"));

        when(detailedReports.prepare(ID)).thenThrow(new IllegalStateException("provider detail"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/a-level-assessment/" + ID + "/report/prepare").with(org.springframework.security.test
                                .web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .redirectedUrl("/a-level-assessment/" + ID + "/report?preparation=failed"));
    }

    @Test
    void preparingWithoutAnActiveEntitlementIsForbidden() throws Exception {
        when(detailedReports.prepare(ID)).thenThrow(new com.chikere.futureai.report.alevel.detailed
                .ALevelDetailedReportUnavailableException(com.chikere.futureai.report.alevel.detailed
                .ALevelDetailedReportUnavailableException.Reason.NOT_ENTITLED, "no"));
        when(purchaseService.accessFor(any())).thenReturn(new ALevelPurchaseAccess(
                Optional.of(ALevelEntitlementStatus.PENDING), false, Optional.empty()));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/a-level-assessment/" + ID + "/report/prepare").with(org.springframework.security.test
                                .web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isForbidden())
                .andExpect(view().name("a-level-assessment/paid-report-unavailable"));
    }

    @Test
    void aHistoricalV1PurchaseNeverReachesTheV2Lifecycle() throws Exception {
        when(purchaseService.activePurchaseVersion(ID)).thenReturn(Optional.of("a-level-report-v1"));
        when(purchaseService.findPurchasedReport(ID)).thenReturn(Optional.of(frozenPaidReport()));

        mvc.perform(get("/a-level-assessment/" + ID + "/report"))
                .andExpect(status().isOk()).andExpect(view().name("a-level-assessment/paid-report"));

        verifyNoInteractions(detailedReports);
    }

    @Test
    void anActiveEntitlementRendersOnlyTheFrozenPurchasedSnapshot() throws Exception {
        when(purchaseService.findPurchasedReport(ID)).thenReturn(Optional.of(frozenPaidReport()));

        String html = mvc.perform(get("/a-level-assessment/" + ID + "/report"))
                .andExpect(status().isOk()).andExpect(view().name("a-level-assessment/paid-report"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Your detailed A-level report", "Mathematics, Economics, Geography",
                "Future Optionality", "Saved optionality detail", "Premium action one",
                "Skills worth developing alongside your subjects", "Degree and career interests you mentioned",
                "Download PDF", "a-level-report-v1",
                "not advice to change your A-levels");
        assertThat(html).doesNotContain("future-proof", "wrong subject", "You should become",
                "Role Adaptability", "Human Differentiation");
        // Other journeys' names appear only once each, inside the cross-journey caveat.
        assertThat(html.split("Future Readiness", -1)).hasSize(2);
        assertThat(html.split("AI Exposure", -1)).hasSize(2);
    }

    @Test
    void withoutAnActivePurchaseAccessIsForbiddenAndNoReportIsRendered() throws Exception {
        when(purchaseService.findPurchasedReport(ID)).thenReturn(Optional.empty());
        when(purchaseService.accessFor(any())).thenReturn(new ALevelPurchaseAccess(
                Optional.of(ALevelEntitlementStatus.PENDING), false, Optional.empty()));

        String html = mvc.perform(get("/a-level-assessment/" + ID + "/report"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("a-level-assessment/paid-report-unavailable"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Payment confirmation is pending")
                .doesNotContain("Saved optionality detail", "Premium action one");
    }

    @Test
    void aMalformedIdIsNotFound() throws Exception {
        mvc.perform(get("/a-level-assessment/not-a-uuid/report")).andExpect(status().isNotFound());
    }

    private ALevelReport frozenPaidReport() {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of(), List.of(), List.of("Economics"), List.of(), Optional.empty(), Optional.empty());
        var judgments = new LocalFixtureALevelJudgmentProvider("maths_economics_geography").judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        var assessment = new StoredALevelAssessment(ID, Instant.parse("2026-10-01T12:00:00Z"), state,
                judgments, new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        var narrative = new ALevelNarrative("Headline", "Summary", "Meaning", "Saved optionality detail",
                "Saved strengths detail", "Saved pathway detail", "Saved analytical detail",
                "Saved human detail", "Saved transferability detail", "Saved adaptability detail",
                "Saved development detail",
                List.of("Premium action one", "Premium action two", "Premium action three"), "Caution");
        Instant time = Instant.parse("2026-10-01T12:01:00Z");
        return new ALevelReportAssembler().assemble(new ALevelReportSource(assessment,
                Optional.of(new StoredALevelNarrative(ID, NarrativeStatus.AVAILABLE,
                        Optional.of(narrative), NarrativeSource.LOCAL_FIXTURE, "local-fixture",
                        Optional.empty(), "a-level-narrative-v1", time, Optional.of(time))),
                Optional.empty()), ALevelReportTier.PAID);
    }
}
