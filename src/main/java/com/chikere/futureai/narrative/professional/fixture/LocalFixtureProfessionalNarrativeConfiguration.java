package com.chikere.futureai.narrative.professional.fixture;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local-fixture")
public class LocalFixtureProfessionalNarrativeConfiguration {

    @Bean
    LocalFixtureProfessionalNarrativeGenerator localFixtureProfessionalNarrativeGenerator() {
        return new LocalFixtureProfessionalNarrativeGenerator();
    }
}
