package com.chikere.futureai.pdf.alevel.web;

import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfAccessDeniedException;
import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfGenerationException;
import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfUseCase;
import com.chikere.futureai.pdf.alevel.application.PurchasedALevelReportPdf;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ALevelReportPdfController.class)
@Import(WebSecurityConfiguration.class)
class ALevelReportPdfControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ALevelReportPdfUseCase pdfService;

    @Test
    void anAuthorisedDownloadReturnsTheExactPersistedBytes() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] bytes = "%PDF-1.7 purchased a-level report".getBytes(StandardCharsets.US_ASCII);
        when(pdfService.getOrCreate(id)).thenReturn(new PurchasedALevelReportPdf(UUID.randomUUID(), id,
                "a-level-report-v1", "a-level-report-pdf-v1", Instant.parse("2026-10-01T12:00:00Z"),
                "ai-career-impact-mathematics-economics-geography-a-level-report.pdf",
                bytes.length, "a".repeat(64), bytes));

        byte[] body = mvc.perform(get("/a-level-assessment/" + id + "/report.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(
                        "ai-career-impact-mathematics-economics-geography-a-level-report.pdf")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().longValue("Content-Length", bytes.length))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isEqualTo(bytes);
    }

    @Test
    void withoutAnActiveEntitlementTheDownloadIsForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ALevelReportPdfAccessDeniedException()).when(pdfService).getOrCreate(any());

        mvc.perform(get("/a-level-assessment/" + id + "/report.pdf")).andExpect(status().isForbidden());
        mvc.perform(get("/a-level-assessment/not-a-uuid/report.pdf")).andExpect(status().isNotFound());
    }

    @Test
    void aGenerationFailureReturnsASafeMessageWithoutProviderDetail() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new ALevelReportPdfGenerationException("secret renderer stack detail"))
                .when(pdfService).getOrCreate(any());

        String body = mvc.perform(get("/a-level-assessment/" + id + "/report.pdf"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("PDF could not be generated right now")
                .doesNotContain("secret renderer stack detail", "ALevelReportPdfGenerationException");
    }
}
