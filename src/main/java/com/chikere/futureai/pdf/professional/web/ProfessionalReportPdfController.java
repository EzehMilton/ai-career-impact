package com.chikere.futureai.pdf.professional.web;

import com.chikere.futureai.pdf.professional.application.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Controller
public final class ProfessionalReportPdfController {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProfessionalReportPdfController.class);
    private static final CacheControl PRIVATE_NO_STORE = CacheControl.noStore().cachePrivate();
    private final ProfessionalReportPdfService pdfService;

    public ProfessionalReportPdfController(ProfessionalReportPdfService pdfService) {
        this.pdfService = pdfService;
    }

    @GetMapping("/professional-assessment/{assessmentId}/report.pdf")
    public ResponseEntity<?> download(@PathVariable String assessmentId) {
        try {
            PurchasedProfessionalReportPdf artifact = pdfService.getOrCreate(UUID.fromString(assessmentId));
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
            LOGGER.error("Purchased report PDF could not be supplied ({})", failure.getClass().getSimpleName());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(PRIVATE_NO_STORE)
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body("Your report is available online, but the PDF could not be generated right now.");
        }
    }
}
