package com.chikere.futureai.pdf.alevel.web;

import com.chikere.futureai.configuration.RecordingJourneyMetricsConfiguration;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfAccessDeniedException;
import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfGenerationException;
import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfUseCase;
import com.chikere.futureai.pdf.alevel.application.PurchasedALevelReportPdf;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReport;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportFixtures;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportPdfRenderer;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportPdfService;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportUseCase;
import com.chikere.futureai.security.WebSecurityConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ALevelReportPdfController.class)
@Import({WebSecurityConfiguration.class, RecordingJourneyMetricsConfiguration.class,
        ALevelReportPdfControllerTest.DetailedPdf.class})
class ALevelReportPdfControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ALevelReportPdfUseCase pdfService;
    @MockitoBean ALevelReportPurchaseUseCase purchases;
    @MockitoBean ALevelDetailedReportUseCase detailedReports;

    static final byte[] V2_BYTES = "%PDF-1.7 a-level report v2".getBytes(StandardCharsets.US_ASCII);
    static final byte[] V3_BYTES = "%PDF-1.7 a-level report v3".getBytes(StandardCharsets.US_ASCII);

    /** The real v2 PDF service over the mocked report use case, with a renderer that returns fixed bytes. */
    @TestConfiguration
    static class DetailedPdf {
        @Bean
        ALevelDetailedReportPdfService pdf(
                ALevelDetailedReportUseCase reports) {
            ALevelDetailedReportPdfRenderer v2 =
                    new ALevelDetailedReportPdfRenderer() {
                        @Override
                        public String pdfVersion() {
                            return "a-level-report-pdf-v2";
                        }

                        @Override
                        public byte[] render(ALevelDetailedReport report) {
                            return V2_BYTES;
                        }
                    };
            ALevelDetailedReportPdfRenderer v3 = new ALevelDetailedReportPdfRenderer() {
                @Override public String pdfVersion() { return "a-level-report-pdf-v3"; }
                @Override public byte[] render(ALevelDetailedReport report) { return V3_BYTES; }
            };
            return new ALevelDetailedReportPdfService(reports, List.of(v2, v3),
                    com.chikere.futureai.trace.AssessmentTrace.disabled());
        }
    }

    @Test
    void aStoredV2ReportIsRenderedOnDemandAndNeverThroughTheV1Path() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchases.activePurchaseVersion(id)).thenReturn(Optional.of("a-level-report-v2"));
        when(detailedReports.find(id)).thenReturn(Optional.of(
                ALevelDetailedReportFixtures.stored(id)));

        byte[] body = mvc.perform(get("/a-level-assessment/" + id + "/report.pdf"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(
                        "a-level-report-mathematics-economics-geography.pdf")))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isEqualTo(V2_BYTES);
        verify(pdfService, never()).getOrCreate(any());
        verify(detailedReports, never()).prepare(any());
    }

    @Test
    void aStoredV3ReportSelectsTheV3Renderer() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchases.activePurchaseVersion(id)).thenReturn(Optional.of("a-level-report-v2"));
        when(detailedReports.find(id)).thenReturn(Optional.of(ALevelDetailedReportFixtures.storedV3(id)));

        byte[] body = mvc.perform(get("/a-level-assessment/" + id + "/report.pdf"))
                .andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isEqualTo(V3_BYTES);
        verify(pdfService, never()).getOrCreate(any());
    }

    @Test
    void aProvisionalV2ReportHasNoPdf() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchases.activePurchaseVersion(id)).thenReturn(Optional.of("a-level-report-v2"));
        when(detailedReports.find(id)).thenReturn(Optional.empty());

        mvc.perform(get("/a-level-assessment/" + id + "/report.pdf")).andExpect(status().isForbidden());
        verify(pdfService, never()).getOrCreate(any());
        verify(detailedReports, never()).prepare(any());
    }
    @Autowired MeterRegistry registry;

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
        assertThat(registry.get("pdf.generation.failures").tag("journey", "a-level").counter().count()).isEqualTo(1);
    }
}
