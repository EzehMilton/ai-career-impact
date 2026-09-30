package com.chikere.futureai.judgment.fixture.professional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;

@Configuration(proxyBeanMethods = false)
@Profile("local-fixture")
public class LocalFixtureProfessionalConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalFixtureProfessionalConfiguration.class);

    @Bean
    LocalFixtureProfessionalJudgmentProvider localFixtureProfessionalJudgmentProvider(
            @Value("${app.professional.fixture-id:}") String fixtureId) {
        LocalFixtureProfessionalJudgmentProvider provider =
                new LocalFixtureProfessionalJudgmentProvider(fixtureId);
        LOGGER.warn("LOCAL FIXTURE MODE ACTIVE - PROFESSIONAL RESULTS ARE NOT LIVE JEV RESULTS");
        return provider;
    }
}
