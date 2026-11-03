package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.payment.professional.application.ProfessionalPurchaseAccess;
import com.chikere.futureai.payment.professional.application.ProfessionalReportPurchaseUseCase;
import com.chikere.futureai.pdf.professional.openhtml.OpenHtmlProfessionalDetailedReportPdfRenderer;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssembler;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssemblerTest;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportUseCase.DetailedReportOutcome;
import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportUseCase.DetailedReportPreparation;
import com.chikere.futureai.report.professional.web.ProfessionalPaidReportController;
import com.chikere.futureai.report.professional.web.ProfessionalReportViewMapper;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisUnavailableException;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisUnavailableException.Reason;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The paid report page: a stored detailed report on the page of its own version (v3 or v4), otherwise the purchased
 * v2 report with the explicit preparation step.
 */
@WebMvcTest(ProfessionalPaidReportController.class)
@Import({WebSecurityConfiguration.class, ProfessionalReportViewMapper.class, ProfessionalDetailedReportViewMapper.class})
class ProfessionalDetailedReportControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalReportPurchaseUseCase purchaseService;
    @MockitoBean ProfessionalDetailedReportUseCase detailed;

    @Test
    void aStoredV4ReportRendersEveryCustomerSectionFromTheSameViewAsThePdf() throws Exception {
        var chain = DetailedReportFixtures.chain("electrician-8");
        UUID id = chain.analysis().assessmentId();
        var snapshot = DetailedReportFixtures.snapshot(chain, DetailedReportFixtures.fallback(chain));
        purchased(id);
        when(detailed.find(id)).thenReturn(Optional.of(snapshot));

        String html = mvc.perform(get("/professional-assessment/" + id + "/report")).andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/paid-report-v4"))
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andReturn().getResponse().getContentAsString();
        assertThat(html).containsSubsequence("Professional AI Career Impact Report", "Executive summary",
                "Your four measures", "What increases AI Exposure", "Your work, responsibility by responsibility",
                "Best opportunities for AI assistance", "Where your human contribution matters most",
                "Skills to strengthen", "Responsible AI use", "Your 90-day plan", "Your AI adaptation strategy",
                "Protect", "Learn", "Experiment", "Strengthen", "Monitor",
                "What this assessment does and does not mean", "Next steps");
        assertThat(html).contains("Electrician", "Install electrical wiring and equipment", "Complete safety documentation",
                "Days 31–60", "Safety-critical limits", "/professional-assessment/" + id + "/report.pdf",
                "class=\"plan-action\"", "class=\"plan-action-content\"")
                .doesNotContainPattern("\\b[A-Z][A-Z0-9]*_[A-Z0-9_]+\\b")
                .doesNotContain("probabilit", "confidence", "professional-paid-narrative", "Prepare your detailed report");

        // The badge is the action's only direct grid item; every textual field shares the main content column.
        assertThat(html).doesNotContainPattern("<li class=\"plan-action\"[^>]*>\\s*<(strong|p)[ >]");
        assertThat(html).containsPattern("(?s)<div class=\"plan-action-content\">.*?"
                + "Formal obligations or close oversight apply to parts of this work\\..*?</div>");

        // The PDF renders the same view: every card and action text the page shows is in the PDF source too.
        var view = new ProfessionalDetailedReportViewMapper().toView(snapshot);
        String pdfSource = new OpenHtmlProfessionalDetailedReportPdfRenderer().html(view);
        for (var card : view.report().responsibilities().cards()) {
            assertThat(html).contains(card.text(), card.categoryLabel());
            assertThat(pdfSource).contains(card.text(), card.categoryLabel());
        }
        for (var note : view.categoryNotes()) {
            assertThat(html).contains(note.explanation());
            assertThat(pdfSource).contains(note.explanation());
        }
        for (var measure : view.measures()) {
            assertThat(html).contains("<span>" + measure.score() + "</span> / 10");
            assertThat(pdfSource).contains(measure.score() + " / 10");
        }
        verify(detailed, never()).prepare(any(), any());
    }

    @Test
    void aStoredV3ReportKeepsItsOwnPageAndIsNeverShownAsV4() throws Exception {
        var snapshot = DetailedReportFixtures.historicalV3Snapshot();
        UUID id = purchased(snapshot.assessmentId());
        when(detailed.find(id)).thenReturn(Optional.of(snapshot));
        String html = mvc.perform(get("/professional-assessment/" + id + "/report")).andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/paid-report-v3")).andReturn().getResponse()
                .getContentAsString();
        assertThat(html).contains("Best opportunities for AI assistance", "Investigate account problems",
                        "Use only tools your organisation has approved for this kind of information.")
                .doesNotContain("Your AI adaptation strategy", "What this could look like", "Also apply, from");
        verify(detailed, never()).prepare(any(), any());
    }

    @Test
    void aV4ReportWithItsNarrativeShowsThePracticalLayerOnThePage() throws Exception {
        var chain = DetailedReportFixtures.chain(PaidNarrativeCalibrationFixtures.ACCOUNTANT);
        var narrative = PaidNarrativeCalibrationFixtures.managementAccountant();
        var snapshot = DetailedReportFixtures.snapshot(chain, DetailedReportFixtures.available(chain, narrative));
        UUID id = purchased(snapshot.assessmentId());
        when(detailed.find(id)).thenReturn(Optional.of(snapshot));
        String html = mvc.perform(get("/professional-assessment/" + id + "/report")).andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/paid-report-v4")).andReturn().getResponse()
                .getContentAsString();
        assertThat(html).containsSubsequence("What this could look like", "Flagging material variances against budget",
                "What stays yours", "Deciding whether forecasts or assumptions should change", "A sensible first experiment",
                "Why you matter here", "What to deliberately strengthen", "In your work, this means",
                "Checking AI-generated variance commentary against the source numbers", "Your AI adaptation strategy",
                "Your commercial judgement about which numbers matter");
        assertThat(html).contains("Also apply, from Responsible AI use: <span>Human review; Approved tools and "
                        + "confidentiality</span>")
                .doesNotContain("Use only tools your organisation has approved for this kind of information.");

        // The experiment callout is a themed class, never an inline style, and that class uses the dark theme's
        // tokens: a light fill under the inherited light text made it unreadable.
        assertThat(html).containsPattern("<div class=\"detailed-experiment\">\\s*<h4>A sensible first experiment</h4>")
                .doesNotContain("style=\"");
        String css = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/resources/static/css/app.css"));
        java.util.regex.Matcher rule = java.util.regex.Pattern.compile("\\.detailed-experiment \\{([^}]*)}").matcher(css);
        assertThat(rule.find()).isTrue();
        assertThat(rule.group(1)).contains("background: var(--surface-sunken)", "color: var(--ink)",
                "border-left: 4px solid var(--brand-accent)").doesNotContainPattern("#[0-9a-fA-F]{3,6}");
    }

    @Test
    void withoutADetailedReportTheV2ReportShowsTheExplicitPreparationStepAndNothingIsGeneratedOnView() throws Exception {
        UUID id = purchased(UUID.randomUUID());
        when(detailed.find(id)).thenReturn(Optional.empty());
        when(detailed.preparation(id)).thenReturn(Optional.of(new DetailedReportPreparation(false, false, false,
                List.of("Write weekly reports", "Meet clients"), 8)));
        String html = mvc.perform(get("/professional-assessment/" + id + "/report")).andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/paid-report")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Prepare your detailed report", "/professional-assessment/" + id + "/report/prepare",
                "name=\"_csrf\"", "Your results at a glance").doesNotContain("Choose up to 8 responsibilities");
        verify(detailed, never()).prepare(any(), any());

        mvc.perform(post("/professional-assessment/" + id + "/report/prepare")).andExpect(status().isForbidden());
        verify(detailed, never()).prepare(any(), any());
        when(detailed.prepare(id, Optional.empty())).thenReturn(outcome());
        mvc.perform(post("/professional-assessment/" + id + "/report/prepare").with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/professional-assessment/" + id + "/report"));
        verify(detailed).prepare(id, Optional.empty());
    }

    @Test
    void moreThanEightShowsAnUncheckedSelectionInOriginalOrderAndTheChoiceIsPassedToTheServiceToValidate() throws Exception {
        UUID id = purchased(UUID.randomUUID());
        List<String> ten = IntStream.rangeClosed(1, 10).mapToObj(i -> "Responsibility " + i).toList();
        when(detailed.find(id)).thenReturn(Optional.empty());
        when(detailed.preparation(id)).thenReturn(Optional.of(new DetailedReportPreparation(false, false, true, ten, 8)));
        String html = mvc.perform(get("/professional-assessment/" + id + "/report")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Choose up to 8 responsibilities for detailed analysis",
                "looks at up to eight responsibilities", "name=\"selectionForm\"", "name=\"_csrf\"")
                .containsSubsequence(ten.toArray(String[]::new)).doesNotContain("checked");

        when(detailed.prepare(eq(id), any())).thenReturn(outcome());
        mvc.perform(post("/professional-assessment/" + id + "/report/prepare").with(csrf())
                        .param("selectionForm", "true").param("responsibility", "2", "5"))
                .andExpect(redirectedUrl("/professional-assessment/" + id + "/report"));
        verify(detailed).prepare(id, Optional.of(List.of(2, 5)));

        // Submitting the screen with nothing chosen is an (invalid) explicit choice, never "analyse the first eight".
        when(detailed.prepare(id, Optional.of(List.of()))).thenThrow(new ResponsibilityAnalysisUnavailableException(
                Reason.INVALID_SELECTION, "choose at least one"));
        mvc.perform(post("/professional-assessment/" + id + "/report/prepare").with(csrf()).param("selectionForm", "true"))
                .andExpect(redirectedUrl("/professional-assessment/" + id + "/report?preparation=invalid-selection"));
        assertThat(mvc.perform(get("/professional-assessment/" + id + "/report").param("preparation", "invalid-selection"))
                .andReturn().getResponse().getContentAsString())
                .contains("Choose between one and eight responsibilities from the list to continue.");
    }

    @Test
    void failuresKeepThePurchasedReportAndShowACalmMessageNeverAnErrorPage() throws Exception {
        UUID id = purchased(UUID.randomUUID());
        when(detailed.find(id)).thenReturn(Optional.empty());
        when(detailed.preparation(id)).thenReturn(Optional.of(new DetailedReportPreparation(false, false, false,
                List.of("Write weekly reports"), 8)));
        when(detailed.prepare(id, Optional.empty())).thenThrow(new IllegalStateException("Jev unavailable"));
        mvc.perform(post("/professional-assessment/" + id + "/report/prepare").with(csrf()))
                .andExpect(redirectedUrl("/professional-assessment/" + id + "/report?preparation=failed"));
        String html = mvc.perform(get("/professional-assessment/" + id + "/report").param("preparation", "failed"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("We could not prepare the detailed analysis just now", "Your results at a glance")
                .doesNotContain("Jev unavailable");
    }

    @Test
    void whileTheNarrativeIsRetryableTheCompleteProvisionalReportIsShownWithAnExplicitRetryAndNoAutomaticRetry()
            throws Exception {
        var chain = DetailedReportFixtures.chain("secondary-school-teacher-8");
        UUID id = purchased(chain.analysis().assessmentId());
        var provisional = new ProfessionalDetailedReportAssembler().assembleProvisional(chain.role(), chain.analysis(),
                chain.guidance());
        when(detailed.find(id)).thenReturn(Optional.empty());
        when(detailed.provisional(id)).thenReturn(Optional.of(provisional));
        String html = mvc.perform(get("/professional-assessment/" + id + "/report")).andExpect(status().isOk())
                .andExpect(view().name("professional-assessment/paid-report-v4")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Your detailed report is ready", "Add the personalised explanation",
                "/professional-assessment/" + id + "/report/prepare", "Plan lessons", "Your 90-day plan")
                .doesNotContain("We still could not add");
        verify(detailed, never()).prepare(any(), any());

        // A retry that is still provisional is acknowledged; viewing never retries by itself.
        when(detailed.prepare(id, Optional.empty())).thenReturn(new DetailedReportOutcome(Optional.empty(), provisional, false));
        mvc.perform(post("/professional-assessment/" + id + "/report/prepare").with(csrf()))
                .andExpect(redirectedUrl("/professional-assessment/" + id + "/report?preparation=failed"));
        assertThat(mvc.perform(get("/professional-assessment/" + id + "/report").param("preparation", "failed"))
                .andReturn().getResponse().getContentAsString()).contains("We still could not add the personalised explanation");
        verify(detailed, org.mockito.Mockito.times(1)).prepare(any(), any());
    }

    @Test
    void withoutAnActiveEntitlementNeitherTheReportNorThePreparationIsAvailable() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchaseService.findPurchasedReport(id)).thenReturn(Optional.empty());
        when(purchaseService.accessFor(id)).thenReturn(new ProfessionalPurchaseAccess(Optional.empty(), false,
                Optional.empty()));
        mvc.perform(get("/professional-assessment/" + id + "/report")).andExpect(status().isForbidden())
                .andExpect(view().name("professional-assessment/paid-report-unavailable"));
        verify(detailed, never()).find(any());
        when(detailed.prepare(id, Optional.empty())).thenThrow(new ResponsibilityAnalysisUnavailableException(
                Reason.NOT_ENTITLED, "not entitled"));
        mvc.perform(post("/professional-assessment/" + id + "/report/prepare").with(csrf()))
                .andExpect(status().isForbidden()).andExpect(view().name("professional-assessment/paid-report-unavailable"));
    }

    private static DetailedReportOutcome outcome() {
        var chain = DetailedReportFixtures.chain("solicitor-8");
        var snapshot = DetailedReportFixtures.snapshot(chain, DetailedReportFixtures.fallback(chain));
        return new DetailedReportOutcome(Optional.of(snapshot), snapshot.report(), false);
    }

    private UUID purchased(UUID id) {
        var report = new ProfessionalReportAssembler().assemble(ProfessionalReportAssemblerTest.source(true, false, false),
                ProfessionalReportTier.PAID);
        when(purchaseService.findPurchasedReport(id)).thenReturn(Optional.of(report));
        return id;
    }
}
