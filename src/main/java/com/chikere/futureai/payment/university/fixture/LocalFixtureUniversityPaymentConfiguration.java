package com.chikere.futureai.payment.university.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile("local-fixture")
public class LocalFixtureUniversityPaymentConfiguration {

    @Bean
    LocalFixtureUniversityPaymentGateway localFixtureUniversityPaymentGateway(ObjectMapper objectMapper) {
        return new LocalFixtureUniversityPaymentGateway(objectMapper, Clock.systemUTC());
    }
}
