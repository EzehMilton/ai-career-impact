package com.chikere.futureai.evidence.professional.nomis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile("live-evidence & !local-fixture")
public class NomisProfessionalEvidenceConfiguration {
    @Bean
    NomisApiClient nomisApiClient(RestClient.Builder builder) {
        return new RestClientNomisApiClient(builder.baseUrl("https://www.nomisweb.co.uk").build());
    }

    @Bean
    NomisProfessionalEvidenceProvider nomisProfessionalEvidenceProvider(NomisApiClient client) {
        return new NomisProfessionalEvidenceProvider(client, Clock.systemUTC());
    }
}
