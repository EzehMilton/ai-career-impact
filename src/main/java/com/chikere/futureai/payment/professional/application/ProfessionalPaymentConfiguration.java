package com.chikere.futureai.payment.professional.application;

import com.chikere.futureai.payment.complimentary.ComplimentaryReportAccess;
import com.chikere.futureai.report.professional.application.ProfessionalReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import com.chikere.futureai.configuration.JourneyMetrics;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.time.Clock;
import java.util.Optional;

@Configuration(proxyBeanMethods = false)
public class ProfessionalPaymentConfiguration {
    @Bean
    @ConditionalOnMissingBean(ObjectMapper.class)
    ObjectMapper professionalReportSnapshotObjectMapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Bean
    ProfessionalReportPurchaseService professionalReportPurchaseService(
            ProfessionalReportService reportService,
            ProfessionalPurchaseStore purchaseStore,
            Optional<ProfessionalPaymentGateway> paymentGateway,
            @Value("${app.professional.report.base-url}") URI applicationBaseUrl,
            @Value("${app.professional.report.display-price:}") String displayPrice,
            ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<ComplimentaryReportAccess> complimentaryAccess) {
        return new ProfessionalReportPurchaseService(reportService, purchaseStore, paymentGateway,
                applicationBaseUrl, Optional.ofNullable(displayPrice), Clock.systemUTC(),
                metrics.getIfAvailable(JourneyMetrics::disabled),
                complimentaryAccess.getIfAvailable(ComplimentaryReportAccess::disabled));
    }
}
