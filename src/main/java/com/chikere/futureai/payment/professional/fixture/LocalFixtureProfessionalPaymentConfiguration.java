package com.chikere.futureai.payment.professional.fixture;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile("dev")
public class LocalFixtureProfessionalPaymentConfiguration {
    @Bean
    LocalFixtureProfessionalPaymentGateway localFixtureProfessionalPaymentGateway(ObjectMapper objectMapper) {
        return new LocalFixtureProfessionalPaymentGateway(objectMapper, Clock.systemUTC());
    }
}
