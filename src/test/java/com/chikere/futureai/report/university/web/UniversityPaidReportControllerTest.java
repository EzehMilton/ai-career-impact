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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(UniversityPaidReportController.class)
@Import({WebSecurityConfiguration.class, UniversityReportViewMapper.class})
class UniversityPaidReportControllerTest {

    private static final UUID ID = UUID.fromString("2e3f4a5b-6c7d-4e9f-8a0b-1c2d3e4f5a6b");

    @Autowired MockMvc mvc;
    @MockitoBean UniversityReportPurchaseUseCase purchaseService;

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
