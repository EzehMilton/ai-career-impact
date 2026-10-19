package com.chikere.futureai.cv.professional.application;

import com.chikere.futureai.configuration.JourneyMetrics;
import com.chikere.futureai.cost.ProviderCostTracker;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ProfessionalCvConfiguration {
    @Bean
    ProfessionalCvUploadService professionalCvUploadService(ProfessionalCvParser parser,
            ObjectProvider<ProfessionalProfileExtractor> extractor,
            ProfessionalProfileValidator validator, ObjectProvider<JourneyMetrics> metrics,
            ObjectProvider<ProviderCostTracker> costs) {
        return new ProfessionalCvUploadService(parser, extractor.stream().findFirst(), validator, Clock.systemUTC(),
                metrics.getIfAvailable(JourneyMetrics::disabled), costs.getIfAvailable(ProviderCostTracker::disabled));
    }
}
