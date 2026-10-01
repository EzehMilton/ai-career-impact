package com.chikere.futureai.pdf.alevel.application;

import com.chikere.futureai.payment.alevel.application.ALevelPurchaseStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ALevelReportPdfConfiguration {

    @Bean
    ALevelReportPdfModelMapper aLevelReportPdfModelMapper() {
        return new ALevelReportPdfModelMapper();
    }

    @Bean
    ALevelReportPdfService aLevelReportPdfService(ALevelPurchaseStore purchaseStore,
            ALevelReportPdfStore pdfStore, ALevelReportPdfRenderer renderer,
            ALevelReportPdfModelMapper mapper,
            @Value("${app.a-level.report.pdf.maximum-bytes:5242880}") long maximumBytes) {
        return new ALevelReportPdfService(purchaseStore, pdfStore, renderer, mapper, maximumBytes,
                Clock.systemUTC());
    }
}
