package com.chikere.futureai.evidence.alevel.fixture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local-fixture")
public class LocalFixtureALevelEvidenceConfiguration {

    @Bean
    LocalFixtureALevelEvidenceProvider localFixtureALevelEvidenceProvider(
            @Value("${app.a-level.fixture-id:}") String fixtureId) {
        return new LocalFixtureALevelEvidenceProvider(fixtureId);
    }
}
