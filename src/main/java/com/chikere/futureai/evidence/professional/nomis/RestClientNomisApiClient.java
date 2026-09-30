package com.chikere.futureai.evidence.professional.nomis;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

final class RestClientNomisApiClient implements NomisApiClient {
    private final RestClient client;

    RestClientNomisApiClient(RestClient client) {
        this.client = client;
    }

    @Override
    public NomisObservation latestUnitedKingdomEmployment(String soc2020Code) {
        JsonNode response = client.get().uri(uri -> uri
                        .path("/api/v01/dataset/APS218.data.json")
                        .queryParam("geography", "2092957697")
                        .queryParam("date", "latest")
                        .queryParam("occupation", soc2020Code)
                        .queryParam("measures", "20100")
                        .queryParam("setallwherenotspecified", "false")
                        .build())
                .retrieve().body(JsonNode.class);
        JsonNode observation = response == null ? null : response.path("obs").path(0);
        if (observation == null || observation.isMissingNode() || observation.path("obs_value").isMissingNode()) {
            throw new IllegalStateException("Nomis returned no matching observation");
        }
        String period = firstText(observation, "date_name", "time_name");
        String occupation = firstText(observation, "occupation_name", "c_occupation_name");
        return new NomisObservation(period, occupation,
                new BigDecimal(observation.path("obs_value").asText()), "people");
    }

    private String firstText(JsonNode node, String first, String second) {
        String value = node.path(first).asText("");
        if (value.isBlank()) value = node.path(second).asText("");
        if (value.isBlank()) throw new IllegalStateException("Nomis response omitted required labels");
        return value;
    }
}
