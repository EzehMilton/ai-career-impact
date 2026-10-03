package com.chikere.futureai.evidence.professional.nomis;

import tools.jackson.databind.JsonNode;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.regex.Pattern;

final class RestClientNomisApiClient implements NomisApiClient {
    private static final String DATASET = "NM_218_1";
    private static final String UK_GEOGRAPHY = "United Kingdom";
    private final RestClient client;

    RestClientNomisApiClient(RestClient client) {
        this.client = client;
    }

    @Override
    public NomisObservation latestUnitedKingdomEmployment(String soc2020Code) {
        if (soc2020Code == null || !soc2020Code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("A four-digit SOC 2020 code is required");
        }
        int nomisOccupation = resolveExactOccupation(soc2020Code);
        JsonNode response = client.get().uri(uri -> uri
                        .path("/api/v01/dataset/NM_218_1.data.json")
                        .queryParam("geography", "2092957697")
                        .queryParam("time", "latest")
                        .queryParam("soc2020_full", nomisOccupation)
                        .queryParam("measures", "20100")
                        .queryParam("setallwherenotspecified", "false")
                        .build())
                .retrieve().body(JsonNode.class);
        if (response == null || response.has("error") || !response.path("obs").isArray()
                || response.path("obs").size() != 1) {
            throw new IllegalStateException("Nomis returned no unique matching observation");
        }
        JsonNode observation = response.path("obs").path(0);
        if (!DATASET.equals(text(observation, "dataset", "value"))
                || !UK_GEOGRAPHY.equals(text(observation, "geography", "description"))
                || !"K02000001".equals(text(observation, "geography", "geogcode"))
                || !String.valueOf(nomisOccupation).equals(text(observation, "soc2020_full", "value"))
                || !text(observation, "soc2020_full", "description").startsWith(soc2020Code + " : ")
                || !"Count".equals(text(observation, "measure", "description"))
                || !"20100".equals(text(observation, "measures", "value"))) {
            throw new IllegalStateException("Nomis returned an unexpected occupation or measure");
        }
        String period = text(observation, "time", "description");
        String occupation = text(observation, "soc2020_full", "description")
                .substring((soc2020Code + " : ").length());
        if (period.isBlank() || occupation.isBlank() || !observation.path("obs_value").path("value").isNumber()) {
            throw new IllegalStateException("Nomis returned an incomplete observation");
        }
        BigDecimal value = observation.path("obs_value").path("value").decimalValue();
        if (value.signum() < 0) {
            throw new IllegalStateException("Nomis returned an invalid employment count");
        }
        return new NomisObservation(period, occupation, UK_GEOGRAPHY, value, "people");
    }

    private int resolveExactOccupation(String soc2020Code) {
        JsonNode response = client.get().uri(
                        "/api/v01/dataset/NM_218_1/SOC2020_FULL.def.sdmx.json")
                .retrieve().body(JsonNode.class);
        JsonNode codes = response == null ? null : response.path("structure").path("codelists")
                .path("codelist").path(0).path("code");
        if (codes == null || !codes.isArray()) {
            throw new IllegalStateException("Nomis returned no SOC 2020 codelist");
        }
        int matched = -1;
        Pattern label = Pattern.compile("^" + soc2020Code + "\\s+:\\s+.+$");
        for (JsonNode code : codes) {
            if (label.matcher(text(code, "description", "value")).matches()) {
                if (matched != -1 || !code.path("value").canConvertToInt()) {
                    throw new IllegalStateException("Nomis returned an ambiguous SOC 2020 code");
                }
                matched = code.path("value").intValue();
            }
        }
        if (matched == -1) {
            throw new IllegalArgumentException("SOC 2020 code is unavailable in APS218");
        }
        return matched;
    }

    private static String text(JsonNode node, String object, String field) {
        return node.path(object).path(field).asText("");
    }
}
