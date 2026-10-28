package com.chikere.futureai.pdf.university.web;

import com.chikere.futureai.pdf.university.application.PurchasedUniversityReportPdf;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfAccessDeniedException;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfGenerationException;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfUseCase;
import com.chikere.futureai.configuration.RecordingJourneyMetricsConfiguration;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReport;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportFixtures;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportPdfRenderer;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportPdfService;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportUseCase;
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

@WebMvcTest(UniversityReportPdfController.class)
@Import({WebSecurityConfiguration.class, RecordingJourneyMetricsConfiguration.class,
        UniversityReportPdfControllerTest.DetailedPdf.class})
class UniversityReportPdfControllerTest {

    static final byte[] V2_BYTES = "%PDF-1.7 university report v2".getBytes(StandardCharsets.US_ASCII);

    @Autowired MockMvc mvc;
    @MockitoBean UniversityReportPdfUseCase pdfService;
    @MockitoBean UniversityReportPurchaseUseCase purchases;
    @MockitoBean UniversityDetailedReportUseCase detailedReports;
    @Autowired MeterRegistry registry;

    /** The real v2 PDF service over the mocked report use case, with a renderer that returns fixed bytes. */
    @TestConfiguration
    static class DetailedPdf {
        @Bean
        UniversityDetailedReportPdfService universityDetailedReportPdfService(UniversityDetailedReportUseCase reports) {
            return new UniversityDetailedReportPdfService(reports, new UniversityDetailedReportPdfRenderer() {
                @Override
                public String pdfVersion() {
                    return "university-report-pdf-v2";
                }

                @Override
                public byte[] render(UniversityDetailedReport report) {
                    return V2_BYTES;
                }
            });
        }
    }

    @Test
    void aStoredV2ReportIsRenderedByTheV2PdfAndNeverByTheV1Path() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchases.activePurchaseVersion(id)).thenReturn(Optional.of("university-report-v2"));
        when(detailedReports.find(id)).thenReturn(Optional.of(UniversityDetailedReportFixtures.stored(id)));

        byte[] body = mvc.perform(get("/university-assessment/" + id + "/report.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(
                        "filename=\"university-report-bsc-nursing-adult.pdf\"")))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isEqualTo(V2_BYTES);
        verify(pdfService, never()).getOrCreate(any());
        verify(detailedReports, never()).prepare(any(), any());
    }

    @Test
    void aProvisionalV2ReportHasNoPdfAndIsForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchases.activePurchaseVersion(id)).thenReturn(Optional.of("university-report-v2"));
        when(detailedReports.find(id)).thenReturn(Optional.empty());
        when(detailedReports.provisional(id)).thenReturn(Optional.of(UniversityDetailedReportFixtures.report()));

        mvc.perform(get("/university-assessment/" + id + "/report.pdf")).andExpect(status().isForbidden());
        verify(pdfService, never()).getOrCreate(any());
        verify(detailedReports, never()).prepare(any(), any());
    }

    @Test
    void aHistoricalV1PurchaseStaysOnTheV1PdfPath() throws Exception {
        UUID id = UUID.randomUUID();
        when(purchases.activePurchaseVersion(id)).thenReturn(Optional.of("university-report-v1"));
        byte[] bytes = "%PDF-1.7 v1".getBytes(StandardCharsets.US_ASCII);
        when(pdfService.getOrCreate(id)).thenReturn(new PurchasedUniversityReportPdf(UUID.randomUUID(), id,
                "university-report-v1", "university-report-pdf-v1", Instant.parse("2026-10-01T12:00:00Z"),
                "v1.pdf", bytes.length, "a".repeat(64), bytes));

        byte[] body = mvc.perform(get("/university-assessment/" + id + "/report.pdf"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isEqualTo(bytes);
        verify(detailedReports, never()).find(any());
    }

    @Test
    void anAuthorisedDownloadReturnsTheExactPersistedBytes() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] bytes = "%PDF-1.7 purchased university report".getBytes(StandardCharsets.US_ASCII);
        when(pdfService.getOrCreate(id)).thenReturn(new PurchasedUniversityReportPdf(UUID.randomUUID(), id,
                "university-report-v1", "university-report-pdf-v1", Instant.parse("2026-10-01T12:00:00Z"),
                "ai-career-impact-computer-science-course-report.pdf", bytes.length, "a".repeat(64), bytes));

        byte[] body = mvc.perform(get("/university-assessment/" + id + "/report.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(
                        "attachment; filename=\"ai-career-impact-computer-science-course-report.pdf\"")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", "no-store, private"))
                .andExpect(header().longValue("Content-Length", bytes.length))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(body).isEqualTo(bytes);
    }

    @Test
    void withoutAnActiveEntitlementTheDownloadIsForbidden() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new UniversityReportPdfAccessDeniedException()).when(pdfService).getOrCreate(any());

        mvc.perform(get("/university-assessment/" + id + "/report.pdf"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/university-assessment/not-a-uuid/report.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    void aGenerationFailureReturnsASafeMessageWithoutProviderDetail() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new UniversityReportPdfGenerationException("secret renderer stack detail"))
                .when(pdfService).getOrCreate(any());

        String body = mvc.perform(get("/university-assessment/" + id + "/report.pdf"))
                .andExpect(status().isServiceUnavailable())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("PDF could not be generated right now")
                .doesNotContain("secret renderer stack detail", "UniversityReportPdfGenerationException");
        assertThat(registry.get("pdf.generation.failures").tag("journey", "university").counter().count()).isEqualTo(1);
    }
}
