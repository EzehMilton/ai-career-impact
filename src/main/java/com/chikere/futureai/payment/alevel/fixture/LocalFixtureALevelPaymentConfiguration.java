package com.chikere.futureai.payment.alevel.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class LocalFixtureALevelPaymentConfiguration {

    @Bean
    LocalFixtureALevelPaymentGateway localFixtureALevelPaymentGateway(ObjectMapper objectMapper) {
        return new LocalFixtureALevelPaymentGateway(objectMapper, Clock.systemUTC());
    }
}
