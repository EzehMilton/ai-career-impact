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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(ALevelPaidReportController.class)
@Import({WebSecurityConfiguration.class, ALevelReportViewMapper.class})
class ALevelPaidReportControllerTest {

    private static final UUID ID = UUID.fromString("7e8f9a0b-1c2d-4e3f-8a4b-5c6d7e8f9a0b");

    @Autowired MockMvc mvc;
    @MockitoBean ALevelReportPurchaseUseCase purchaseService;

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
