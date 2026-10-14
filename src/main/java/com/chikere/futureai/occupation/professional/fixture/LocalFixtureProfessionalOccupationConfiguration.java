package com.chikere.futureai.occupation.professional.fixture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile({"dev", "dev-no-jev"})
public class LocalFixtureProfessionalOccupationConfiguration {
    @Bean
    LocalFixtureProfessionalOccupationClassifier localFixtureProfessionalOccupationClassifier(
            @Value("${app.professional.fixture-id:}") String fixtureId) {
        return new LocalFixtureProfessionalOccupationClassifier(fixtureId);
    }
}
