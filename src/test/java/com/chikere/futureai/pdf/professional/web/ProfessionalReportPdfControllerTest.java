package com.chikere.futureai.pdf.professional.web;

import com.chikere.futureai.pdf.professional.application.*;
import com.chikere.futureai.security.WebSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ProfessionalReportPdfController.class)
@Import(WebSecurityConfiguration.class)
class ProfessionalReportPdfControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean ProfessionalReportPdfUseCase pdfService;

    @Test
    void activeDownloadHasPdfAttachmentAndPrivateNoStoreHeaders() throws Exception {
        UUID assessmentId = UUID.randomUUID(); byte[] bytes = "%PDF-stored".getBytes(StandardCharsets.US_ASCII);
        when(pdfService.getOrCreate(assessmentId)).thenReturn(new PurchasedProfessionalReportPdf(UUID.randomUUID(),
                assessmentId, "professional-report-v1", "professional-report-pdf-v1", Instant.now(),
                "ai-career-impact-software-engineer-report.pdf", bytes.length, "a".repeat(64), bytes));
        var response = mvc.perform(get("/professional-assessment/" + assessmentId + "/report.pdf"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition",
                        org.hamcrest.Matchers.containsString("ai-career-impact-software-engineer-report.pdf")))
                .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                .andReturn().getResponse();
        assertThat(response.getContentAsByteArray()).isEqualTo(bytes);
    }

    @Test
    void absentOrInactiveEntitlementIsDenied() throws Exception {
        UUID id = UUID.randomUUID();
        when(pdfService.getOrCreate(id)).thenThrow(new ProfessionalReportPdfAccessDeniedException());
        mvc.perform(get("/professional-assessment/" + id + "/report.pdf")).andExpect(status().isForbidden());
    }

    @Test
    void rendererFailureReturnsSafeMessageWithoutInternalDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(pdfService.getOrCreate(id)).thenThrow(new ProfessionalReportPdfGenerationException("secret renderer failure"));
        String text = mvc.perform(get("/professional-assessment/" + id + "/report.pdf"))
                .andExpect(status().isServiceUnavailable()).andReturn().getResponse().getContentAsString();
        assertThat(text).contains("report is available online").doesNotContain("secret", "renderer failure");
    }
}
