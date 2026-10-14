package com.chikere.futureai.judgment.fixture.university;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"dev", "dev-no-jev"})
public class LocalFixtureUniversityConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFixtureUniversityConfiguration.class);

    @Bean
    LocalFixtureUniversityJudgmentProvider localFixtureUniversityJudgmentProvider(
            @Value("${app.university.fixture-id:}") String fixtureId) {
        LocalFixtureUniversityJudgmentProvider provider = new LocalFixtureUniversityJudgmentProvider(fixtureId);
        LOGGER.warn("LOCAL FIXTURE MODE ACTIVE - UNIVERSITY RESULTS ARE NOT LIVE JEV RESULTS");
        return provider;
    }
}
