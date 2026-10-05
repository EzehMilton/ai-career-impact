package com.chikere.futureai.cv.professional.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ProfessionalCvConfiguration {
    @Bean
    ProfessionalCvUploadService professionalCvUploadService(ProfessionalCvParser parser,
            ObjectProvider<ProfessionalProfileExtractor> extractor,
            ProfessionalProfileValidator validator) {
        return new ProfessionalCvUploadService(parser, extractor.stream().findFirst(), validator, Clock.systemUTC());
    }
}
