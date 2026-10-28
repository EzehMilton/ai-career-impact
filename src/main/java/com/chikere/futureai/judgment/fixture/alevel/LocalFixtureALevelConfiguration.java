package com.chikere.futureai.judgment.fixture.alevel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"dev", "dev-no-jev"})
public class LocalFixtureALevelConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFixtureALevelConfiguration.class);

    @Bean
    LocalFixtureALevelV2JudgmentProvider localFixtureALevelV2JudgmentProvider(
            @Value("${app.a-level.fixture-id:}") String fixtureId) {
        LocalFixtureALevelV2JudgmentProvider provider = new LocalFixtureALevelV2JudgmentProvider(fixtureId);
        LOGGER.warn("LOCAL FIXTURE MODE ACTIVE - A-LEVEL RESULTS ARE NOT LIVE JEV RESULTS");
        return provider;
    }
}
