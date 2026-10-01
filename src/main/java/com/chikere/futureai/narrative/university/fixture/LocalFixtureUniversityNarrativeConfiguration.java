package com.chikere.futureai.narrative.university.fixture;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local-fixture")
public class LocalFixtureUniversityNarrativeConfiguration {

    @Bean
    LocalFixtureUniversityNarrativeGenerator localFixtureUniversityNarrativeGenerator() {
        return new LocalFixtureUniversityNarrativeGenerator();
    }
}
