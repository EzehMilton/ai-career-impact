package com.chikere.futureai.occupation.professional.configured;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("live-evidence & !local-fixture")
public class ConfiguredProfessionalOccupationConfiguration {
    @Bean
    ConfiguredProfessionalOccupationClassifier configuredProfessionalOccupationClassifier(
            @Value("${app.professional.evidence.classified-profession:}") String profession,
            @Value("${app.professional.evidence.canonical-occupation:}") String canonicalTitle,
            @Value("${app.professional.evidence.soc2020-code:}") String soc2020Code) {
        return new ConfiguredProfessionalOccupationClassifier(profession, canonicalTitle, soc2020Code);
    }
}
