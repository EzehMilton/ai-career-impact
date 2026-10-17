package com.chikere.futureai.pdf.university.web;

import com.chikere.futureai.pdf.university.application.PurchasedUniversityReportPdf;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfAccessDeniedException;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfUseCase;
import com.chikere.futureai.configuration.FailureDescription;
import com.chikere.futureai.configuration.JourneyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
public final class UniversityReportPdfController {

    private static final Logger LOGGER = LoggerFactory.getLogger(UniversityReportPdfController.class);
    private static final CacheControl PRIVATE_NO_STORE = CacheControl.noStore().cachePrivate();
    private final UniversityReportPdfUseCase pdfService;
    private final JourneyMetrics metrics;

    /** Metrics resolve optionally so a sliced web test without a registry still loads this controller. */
    public UniversityReportPdfController(UniversityReportPdfUseCase pdfService,
            ObjectProvider<JourneyMetrics> metrics) {
        this.pdfService = pdfService;
        this.metrics = metrics.getIfAvailable(JourneyMetrics::disabled);
    }

    @GetMapping("/university-assessment/{assessmentId}/report.pdf")
    public ResponseEntity<?> download(@PathVariable String assessmentId) {
        try {
            PurchasedUniversityReportPdf artifact = pdfService.getOrCreate(UUID.fromString(assessmentId));
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .cacheControl(PRIVATE_NO_STORE)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(artifact.filename(), StandardCharsets.UTF_8)
                                    .build().toString())
                    .contentLength(artifact.fileSize())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(artifact.bytes());
        }
        catch (UniversityReportPdfAccessDeniedException denied) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(PRIVATE_NO_STORE).build();
        }
        catch (IllegalArgumentException malformed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(PRIVATE_NO_STORE).build();
        }
        catch (RuntimeException failure) {
            metrics.pdfGenerationFailed("university");
            LOGGER.error("Purchased university report PDF could not be supplied ({})",
                    FailureDescription.of(failure));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(PRIVATE_NO_STORE)
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body("Your report is available online, but the PDF could not be generated right now.");
        }
    }
}
