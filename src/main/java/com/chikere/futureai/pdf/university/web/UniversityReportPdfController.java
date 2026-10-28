package com.chikere.futureai.pdf.university.web;

import com.chikere.futureai.pdf.university.application.PurchasedUniversityReportPdf;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfAccessDeniedException;
import com.chikere.futureai.pdf.university.application.UniversityReportPdfUseCase;
import com.chikere.futureai.configuration.FailureDescription;
import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.payment.university.application.UniversityReportPurchaseUseCase;
import com.chikere.futureai.report.university.application.UniversityReportVersions;
import com.chikere.futureai.report.university.detailed.UniversityDetailedReportPdfService;
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
    private final ObjectProvider<UniversityReportPurchaseUseCase> purchases;
    private final ObjectProvider<UniversityDetailedReportPdfService> detailedPdfs;

    /**
     * Metrics, purchases and the v2 PDF resolve optionally so a sliced web test without them still loads this
     * controller. A university-report-v2 purchase is rendered from its stored detailed report; every other purchase
     * keeps the historical v1 PDF path.
     */
    public UniversityReportPdfController(UniversityReportPdfUseCase pdfService,
            ObjectProvider<JourneyMetrics> metrics, ObjectProvider<UniversityReportPurchaseUseCase> purchases,
            ObjectProvider<UniversityDetailedReportPdfService> detailedPdfs) {
        this.pdfService = pdfService;
        this.metrics = metrics.getIfAvailable(JourneyMetrics::disabled);
        this.purchases = purchases;
        this.detailedPdfs = detailedPdfs;
    }

    @GetMapping("/university-assessment/{assessmentId}/report.pdf")
    public ResponseEntity<?> download(@PathVariable String assessmentId) {
        try {
            UUID id = UUID.fromString(assessmentId);
            UniversityDetailedReportPdfService detailed = detailedPdfs.getIfAvailable();
            UniversityReportPurchaseUseCase purchase = purchases.getIfAvailable();
            if (detailed != null && purchase != null
                    && purchase.activePurchaseVersion(id).filter(UniversityReportVersions.V2::equals).isPresent()) {
                var pdf = detailed.render(id);
                return pdf(pdf.filename(), pdf.bytes());
            }
            PurchasedUniversityReportPdf artifact = pdfService.getOrCreate(id);
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

    private static ResponseEntity<byte[]> pdf(String filename, byte[] bytes) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .cacheControl(PRIVATE_NO_STORE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename, StandardCharsets.UTF_8).build().toString())
                .contentLength(bytes.length)
                .header("X-Content-Type-Options", "nosniff")
                .body(bytes);
    }
}
