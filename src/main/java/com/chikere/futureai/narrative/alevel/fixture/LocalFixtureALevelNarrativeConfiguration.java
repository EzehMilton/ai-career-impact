package com.chikere.futureai.narrative.alevel.fixture;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("local-fixture")
public class LocalFixtureALevelNarrativeConfiguration {

    @Bean
    LocalFixtureALevelNarrativeGenerator localFixtureALevelNarrativeGenerator() {
        return new LocalFixtureALevelNarrativeGenerator();
    }
}
