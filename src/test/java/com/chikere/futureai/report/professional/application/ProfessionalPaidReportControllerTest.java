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

        assertThat(html).contains("DEVELOPMENT MODE — LOCALLY PURCHASED REPORT", "Personalised explanation",
                "Detailed exposure", "What to focus on next", "Action one", "Skill-development priorities",
                "AI-tool fluency", "Work-focus guidance", "does not assign task-level scores",
                "External career context", "Local fixture evidence", "Occupation classification", "2133",
                "Methodology and limitations", "professional-report-v1", "Download PDF",
                "/professional-assessment/" + source.assessment().id() + "/report.pdf")
                .doesNotContain("provider exception");
        verify(purchaseService).findPurchasedReport(source.assessment().id());
        verifyNoMoreInteractions(purchaseService);
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
