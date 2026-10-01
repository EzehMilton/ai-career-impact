package com.chikere.futureai.cv.professional.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ProfessionalCvConfiguration {
    @Bean
    ProfessionalCvUploadService professionalCvUploadService(ProfessionalCvParser parser) {
        return new ProfessionalCvUploadService(parser, Clock.systemUTC());
    }
}
