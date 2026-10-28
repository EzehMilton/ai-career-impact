package com.chikere.futureai.pdf.alevel.web;

import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseUseCase;
import com.chikere.futureai.report.alevel.application.ALevelReportVersions;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportPdfService;
import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfAccessDeniedException;
import com.chikere.futureai.pdf.alevel.application.ALevelReportPdfUseCase;
import com.chikere.futureai.pdf.alevel.application.PurchasedALevelReportPdf;
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
public final class ALevelReportPdfController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ALevelReportPdfController.class);
    private static final CacheControl PRIVATE_NO_STORE = CacheControl.noStore().cachePrivate();
    private final ALevelReportPdfUseCase pdfService;
    private final JourneyMetrics metrics;
    private final ObjectProvider<ALevelReportPurchaseUseCase> purchases;
    private final ObjectProvider<ALevelDetailedReportPdfService> detailedPdfs;

    /**
     * Metrics, purchases and the v2 PDF resolve optionally so a sliced web test without them still loads this
     * controller. An a-level-report-v2 purchase is rendered on demand from its stored detailed report; every other
     * purchase keeps the historical v1 path and its stored PDF bytes.
     */
    public ALevelReportPdfController(ALevelReportPdfUseCase pdfService, ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<ALevelReportPurchaseUseCase> purchases,
            ObjectProvider<ALevelDetailedReportPdfService> detailedPdfs) {
        this.pdfService = pdfService;
        this.metrics = metrics.getIfAvailable(JourneyMetrics::disabled);
        this.purchases = purchases;
        this.detailedPdfs = detailedPdfs;
    }

    @GetMapping("/a-level-assessment/{assessmentId}/report.pdf")
    public ResponseEntity<?> download(@PathVariable String assessmentId) {
        try {
            UUID id = UUID.fromString(assessmentId);
            ALevelDetailedReportPdfService detailed = detailedPdfs.getIfAvailable();
            ALevelReportPurchaseUseCase purchase = purchases.getIfAvailable();
            if (detailed != null && purchase != null
                    && purchase.activePurchaseVersion(id).filter(ALevelReportVersions.V2::equals).isPresent()) {
                // A provisional report has no stored snapshot, so this is refused until a final report exists.
                var pdf = detailed.render(id);
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).cacheControl(PRIVATE_NO_STORE)
                        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                                .filename(pdf.filename(), StandardCharsets.UTF_8).build().toString())
                        .contentLength(pdf.bytes().length)
                        .header("X-Content-Type-Options", "nosniff")
                        .body(pdf.bytes());
            }
            PurchasedALevelReportPdf artifact = pdfService.getOrCreate(id);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                    .cacheControl(PRIVATE_NO_STORE)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(artifact.filename(), StandardCharsets.UTF_8)
                                    .build().toString())
                    .contentLength(artifact.fileSize())
                    .header("X-Content-Type-Options", "nosniff")
                    .body(artifact.bytes());
        }
        catch (ALevelReportPdfAccessDeniedException denied) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).cacheControl(PRIVATE_NO_STORE).build();
        }
        catch (IllegalArgumentException malformed) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(PRIVATE_NO_STORE).build();
        }
        catch (RuntimeException failure) {
            metrics.pdfGenerationFailed("a-level");
            LOGGER.error("Purchased A-level report PDF could not be supplied ({})",
                    FailureDescription.of(failure));
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).cacheControl(PRIVATE_NO_STORE)
                    .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                    .body("Your report is available online, but the PDF could not be generated right now.");
        }
    }
}
