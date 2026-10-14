package com.chikere.futureai.evidence.university.fixture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"dev", "dev-no-jev"})
public class LocalFixtureUniversityEvidenceConfiguration {

    @Bean
    LocalFixtureUniversityEvidenceProvider localFixtureUniversityEvidenceProvider(
            @Value("${app.university.fixture-id:}") String fixtureId) {
        return new LocalFixtureUniversityEvidenceProvider(fixtureId);
    }
}
