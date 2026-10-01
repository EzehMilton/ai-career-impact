package com.chikere.futureai.pdf.university.application;

import com.chikere.futureai.payment.university.application.UniversityPurchaseStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class UniversityReportPdfConfiguration {

    @Bean
    UniversityReportPdfModelMapper universityReportPdfModelMapper() {
        return new UniversityReportPdfModelMapper();
    }

    @Bean
    UniversityReportPdfService universityReportPdfService(UniversityPurchaseStore purchaseStore,
            UniversityReportPdfStore pdfStore, UniversityReportPdfRenderer renderer,
            UniversityReportPdfModelMapper mapper,
            @Value("${app.university.report.pdf.maximum-bytes:5242880}") long maximumBytes) {
        return new UniversityReportPdfService(purchaseStore, pdfStore, renderer, mapper,
                maximumBytes, Clock.systemUTC());
    }
}
