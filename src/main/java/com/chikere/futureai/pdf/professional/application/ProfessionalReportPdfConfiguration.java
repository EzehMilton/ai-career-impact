package com.chikere.futureai.pdf.professional.application;

import com.chikere.futureai.payment.professional.application.ProfessionalPurchaseStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ProfessionalReportPdfConfiguration {
    @Bean
    ProfessionalReportPdfModelMapper professionalReportPdfModelMapper() {
        return new ProfessionalReportPdfModelMapper();
    }

    @Bean
    ProfessionalReportPdfService professionalReportPdfService(ProfessionalPurchaseStore purchaseStore,
            ProfessionalReportPdfStore pdfStore, ProfessionalReportPdfRenderer renderer,
            ProfessionalReportPdfModelMapper mapper,
            @Value("${app.professional.report.pdf.maximum-bytes:5242880}") long maximumBytes) {
        return new ProfessionalReportPdfService(purchaseStore, pdfStore, renderer, mapper,
                maximumBytes, Clock.systemUTC());
    }
}
