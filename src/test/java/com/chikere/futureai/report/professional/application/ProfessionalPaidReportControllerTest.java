package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.report.professional.web.ProfessionalPaidReportController;
import com.chikere.futureai.report.professional.web.ProfessionalReportViewMapper;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfessionalPaidReportController.class)
@Import({WebSecurityConfiguration.class, ProfessionalReportViewMapper.class})
class ProfessionalPaidReportControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalReportPurchaseUseCase purchaseService;

    @Test
    void activeEntitlementRendersFrozenPurchasedReport() throws Exception {
        var source = ProfessionalReportAssemblerTest.source(true, true, true);
        var report = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        when(purchaseService.findPurchasedReport(source.assessment().id())).thenReturn(Optional.of(report));

        String html = mvc.perform(get("/professional-assessment/" + source.assessment().id() + "/report"))
                .andExpect(status().isOk()).andExpect(view().name("professional-assessment/paid-report"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("DEVELOPMENT MODE — LOCALLY PURCHASED REPORT",
                "Detailed exposure", "What to focus on next", "Action one", "Skill-development priorities",
                "AI-tool fluency", "Guidance for your day-to-day work", "Local fixture evidence",
                "About your assessment", "Download PDF",
                "/professional-assessment/" + source.assessment().id() + "/report.pdf")
                .doesNotContain("provider exception");
        // Sections in reading order, with plain-language guidance and career context in place of technical wording.
        assertThat(html).containsSubsequence("Your results at a glance", "Your four assessment scores",
                "What increases AI Exposure", "What reduces AI Exposure", "What your results mean for your work",
                "What to focus on next", "Skill-development priorities", "Guidance for your day-to-day work",
                "About this guidance", "Career context", "About your assessment");
        assertThat(html).contains("These suggestions are based on the assessment you completed. They are intended to "
                        + "help you decide what to explore next, not to predict your career.",
                "Where reliable public information is available, we show it separately from your assessment scores.");
        // Each measure's detailed explanation appears once, in its card, not again in the narrative section.
        assertThat(html.split("Detailed exposure", -1)).hasSize(2);
        assertThat(html.split("Detailed adaptability", -1)).hasSize(2);
        // The visitor's own responsibilities are not presented back as paid insight, nor is internal wording.
        assertThat(html).doesNotContain("YOUR RESPONSIBILITIES TO REVIEW", "Review your day-to-day responsibilities",
                "did not judge each responsibility", "does not assign task-level scores", "Design systems",
                "deterministic presentation guidance", "does not validate or determine", "technical details",
                "Occupation classification", "professional-report-v1", "professional-report-v2",
                "External career context");
        // Skill cards: high priority first, otherwise in their selected order.
        assertThat(html).containsSubsequence("AI-tool fluency", "Domain expertise", "Human communication",
                "Process automation", "Complementary technical depth");
        // PDF first, the assessment second, home as a lower-priority text link.
        assertThat(html).containsSubsequence(
                "<a class=\"button-link\" href=\"/professional-assessment/" + source.assessment().id() + "/report.pdf\">Download PDF</a>",
                "<a class=\"button-link button-secondary\" href=\"/professional-assessment/" + source.assessment().id()
                        + "\">Back to my assessment</a>",
                "<a class=\"text-link\" href=\"/\">Back to home</a>");
        // Presentation-only copy: confidence and the generated caution are hidden while they are redesigned.
        assertThat(html).contains("Your results at a glance", "<dd>Assessment complete</dd>", "<dt>Score</dt>",
                "How to read the 0–10 scores", "What reduces AI Exposure",
                "Each score describes a different characteristic of your work. A higher score is not automatically "
                        + "better or worse.",
                "Characteristics of the work that contribute to its AI Exposure score.", "<strong>Moderate</strong>", "<strong>High</strong>",
                "How strongly the work relies on human trust, context, physical presence, judgement and accountability.")
                .doesNotContain("career outlook", "Assessment confidence", "Confidence indicator", "Assessment indicator",
                        "narrative-caution", ">Caution<", ">MODERATE<", ">HIGH<", "protective factors",
                        "Where human skills remain important",
                        "Your four assessment ratings", "What increases AI exposure",
                        "rating describes", "higher rating", "AI Exposure rating")
                .doesNotContainPattern("<dd>\\d+%</dd>");
        verify(purchaseService).findPurchasedReport(source.assessment().id());
        verifyNoMoreInteractions(purchaseService);
    }

    @Test
    void withoutVerifiedEvidenceTheCareerContextSectionIsReplacedByOneQuietSentence() throws Exception {
        var source = ProfessionalReportAssemblerTest.source(true, false, true);
        var report = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        when(purchaseService.findPurchasedReport(source.assessment().id())).thenReturn(Optional.of(report));

        String html = mvc.perform(get("/professional-assessment/" + source.assessment().id() + "/report"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(html).contains("Verified external career information was not available for this assessment.");
        assertThat(html).doesNotContain("Career context", "External career context is currently unavailable");
    }

    @Test
    void absentOrPendingEntitlementNeverGrantsPaidAccess() throws Exception {
        var id = java.util.UUID.randomUUID();
        when(purchaseService.accessFor(id)).thenReturn(new ProfessionalPurchaseAccess(
                Optional.of(ProfessionalEntitlementStatus.PENDING), false, Optional.empty()));
        String html = mvc.perform(get("/professional-assessment/" + id + "/report"))
                .andExpect(status().isForbidden())
                .andExpect(view().name("professional-assessment/paid-report-unavailable"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Payment confirmation is pending").doesNotContain("Detailed exposure");
        verify(purchaseService).findPurchasedReport(id);
        verify(purchaseService).accessFor(id);
    }
}
