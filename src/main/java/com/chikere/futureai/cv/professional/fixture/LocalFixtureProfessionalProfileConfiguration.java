package com.chikere.futureai.cv.professional.fixture;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class LocalFixtureProfessionalProfileConfiguration {
    @Bean
    LocalFixtureProfessionalProfileExtractor localFixtureProfessionalProfileExtractor(
            @Value("${app.professional.fixture-id}") String fixtureId, ObjectProvider<Clock> clock) {
        return new LocalFixtureProfessionalProfileExtractor(fixtureId, clock.getIfAvailable(Clock::systemUTC));
    }
}
