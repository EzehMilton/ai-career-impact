package com.chikere.futureai.evidence.professional.nomis;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class NomisProfessionalEvidenceConfiguration {
    @Bean
    NomisApiClient nomisApiClient(RestClient.Builder builder) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5_000);
        requestFactory.setReadTimeout(10_000);
        return new RestClientNomisApiClient(builder.baseUrl("https://www.nomisweb.co.uk")
                .requestFactory(requestFactory).build());
    }

    @Bean
    NomisProfessionalEvidenceProvider nomisProfessionalEvidenceProvider(NomisApiClient client) {
        return new NomisProfessionalEvidenceProvider(client, Clock.systemUTC());
    }
}
