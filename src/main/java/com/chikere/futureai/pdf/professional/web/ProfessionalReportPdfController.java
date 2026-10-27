package com.chikere.futureai.pdf.professional.web;

import com.chikere.futureai.pdf.professional.application.*;
import com.chikere.futureai.configuration.FailureDescription;
import com.chikere.futureai.configuration.JourneyMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import com.chikere.futureai.report.professional.detailed.ProfessionalDetailedReportPdfUseCase;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
public final class ProfessionalReportPdfController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalReportPdfController.class);
    private static final CacheControl PRIVATE_NO_STORE = CacheControl.noStore().cachePrivate();
    private final ProfessionalReportPdfUseCase pdfService;
    private final ObjectProvider<ProfessionalDetailedReportPdfUseCase> detailedPdfs;
    private final JourneyMetrics metrics;

    /**
     * Metrics and the detailed-report PDF resolve optionally so a sliced web test without them still loads this
     * controller. When the assessment has a stored professional-report-v3, its v5 PDF is served; otherwise the
     * purchased role-level PDF, exactly as before.
     */
    public ProfessionalReportPdfController(ProfessionalReportPdfUseCase pdfService,
            ObjectProvider<ProfessionalDetailedReportPdfUseCase> detailedPdfs, ObjectProvider<JourneyMetrics> metrics) {
        this.pdfService = pdfService;
        this.detailedPdfs = detailedPdfs;
        this.metrics = metrics.getIfAvailable(JourneyMetrics::disabled);
    }

    @GetMapping("/professional-assessment/{assessmentId}/report.pdf")
    public ResponseEntity<?> download(@PathVariable String assessmentId) {
        try {
            UUID id = UUID.fromString(assessmentId);
            ProfessionalDetailedReportPdfUseCase detailed = detailedPdfs.getIfAvailable();
            var detailedPdf = detailed == null
                    ? java.util.Optional.<ProfessionalDetailedReportPdfUseCase.DetailedReportPdfDownload>empty()
                    : detailed.findOrCreate(id);
            if (detailedPdf.isPresent()) {
                byte[] bytes = detailedPdf.get().bytes();
                return pdf(detailedPdf.get().filename(), bytes.length, bytes);
            }
            PurchasedProfessionalReportPdf artifact = pdfService.getOrCreate(id);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .cacheControl(PRIVATE_NO_STORE)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(artifact.filename(), StandardCharsets.UTF_8)
                                    .build().toString())
                    .contentLength(artifact.fileSize())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(artifact.bytes());
        }
        catch (ProfessionalReportPdfAccessDeniedException denied) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(PRIVATE_NO_STORE).build();
        }
        catch (IllegalArgumentException malformed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(PRIVATE_NO_STORE).build();
        }
        catch (RuntimeException failure) {
            metrics.pdfGenerationFailed("professional");
            LOGGER.error("Purchased report PDF could not be supplied ({})", FailureDescription.of(failure));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(PRIVATE_NO_STORE)
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body("Your report is available online, but the PDF could not be generated right now.");
        }
    }

    private static ResponseEntity<byte[]> pdf(String filename, long size, byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .cacheControl(PRIVATE_NO_STORE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentLength(size)
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
