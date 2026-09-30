package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.report.professional.web.ProfessionalPaidReportPreviewController;
import com.chikere.futureai.report.professional.web.ProfessionalReportViewMapper;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfessionalPaidReportPreviewController.class)
@ActiveProfiles("local-fixture")
@Import({WebSecurityConfiguration.class, ProfessionalReportViewMapper.class})
class ProfessionalPaidReportPreviewControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalReportService reportService;

    @Test
    void localOnlyPaidPreviewRendersFullSavedReport() throws Exception {
        var source = ProfessionalReportAssemblerTest.source(true, true, true);
        var report = new ProfessionalReportAssembler().assemble(source, ProfessionalReportTier.PAID);
        when(reportService.assemble(source.assessment().id(), ProfessionalReportTier.PAID))
                .thenReturn(Optional.of(report));

        String html = mvc.perform(get("/professional-assessment/" + source.assessment().id() + "/report"))
                .andExpect(status().isOk()).andExpect(view().name("professional-assessment/paid-report"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("DEVELOPMENT MODE — PAID REPORT PREVIEW", "Personalised explanation",
                "Detailed exposure", "What to focus on next", "Action one", "Skill-development priorities",
                "AI-tool fluency", "Work-focus guidance", "does not assign task-level scores",
                "External career context", "Local fixture evidence", "Occupation classification", "2133",
                "Methodology and limitations", "professional-report-v1");
        assertThat(html).doesNotContain("fixture</dd><dd>", "provider exception");
        verify(reportService).assemble(source.assessment().id(), ProfessionalReportTier.PAID);
    }

    @Test
    void malformedOrUnknownPreviewDoesNotExposeAnInternalListing() throws Exception {
        mvc.perform(get("/professional-assessment/not-a-uuid/report")).andExpect(status().isNotFound());
        var id = java.util.UUID.randomUUID();
        mvc.perform(get("/professional-assessment/" + id + "/report")).andExpect(status().isNotFound());
        verify(reportService).assemble(id, ProfessionalReportTier.PAID);
        verifyNoMoreInteractions(reportService);
    }
}
