package com.chikere.futureai.evidence.professional.nomis;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class RestClientNomisApiClientTest {
    private static final String CODELIST_URL =
            "https://www.nomisweb.co.uk/api/v01/dataset/NM_218_1/SOC2020_FULL.def.sdmx.json";
    private static final String DATA_URL = "https://www.nomisweb.co.uk/api/v01/dataset/NM_218_1.data.json"
            + "?geography=2092957697&time=latest&soc2020_full=59&measures=20100"
            + "&setallwherenotspecified=false";
    private static final String CODELIST = """
            {"structure":{"codelists":{"codelist":[{"code":[
              {"value":59,"description":{"value":"2133 : IT business analysts, architects and systems designers"}},
              {"value":60,"description":{"value":"2134 : Programmers and software development professionals"}}
            ]}]}}}
            """;
    private static final String OBSERVATION = """
            {"obs":[{"dataset":{"value":"NM_218_1"},
              "geography":{"description":"United Kingdom","geogcode":"K02000001"},
              "soc2020_full":{"value":59,"description":"2133 : IT business analysts, architects and systems designers"},
              "measure":{"description":"Count"},"measures":{"value":20100},
              "time":{"description":"Apr 2025-Mar 2026"},"obs_value":{"value":212200}}]}
            """;

    @Test
    void resolvesExactSocCodeAndParsesNestedOfficialObservation() {
        var builder = RestClient.builder().baseUrl("https://www.nomisweb.co.uk");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(CODELIST_URL)).andRespond(withSuccess(CODELIST, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo(DATA_URL)).andRespond(withSuccess(OBSERVATION, MediaType.APPLICATION_JSON));

        var observation = new RestClientNomisApiClient(builder.build()).latestUnitedKingdomEmployment("2133");

        assertThat(observation.occupationName()).isEqualTo("IT business analysts, architects and systems designers");
        assertThat(observation.geography()).isEqualTo("United Kingdom");
        assertThat(observation.period()).isEqualTo("Apr 2025-Mar 2026");
        assertThat(observation.value()).isEqualByComparingTo(new BigDecimal("212200"));
        assertThat(observation.unit()).isEqualTo("people");
        server.verify();
    }

    @Test
    void refusesUnknownSocCodeWithoutDataRequest() {
        var builder = RestClient.builder().baseUrl("https://www.nomisweb.co.uk");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(CODELIST_URL)).andRespond(withSuccess(CODELIST, MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> new RestClientNomisApiClient(builder.build())
                .latestUnitedKingdomEmployment("9999"))
                .hasMessageContaining("unavailable in APS218");
        server.verify();
    }

    @Test
    void refusesMismatchedGeographyAndMalformedValue() {
        for (String payload : new String[] {
                OBSERVATION.replace("United Kingdom", "England"),
                OBSERVATION.replace("\"value\":212200", "\"value\":\"suppressed\"")}) {
            var builder = RestClient.builder().baseUrl("https://www.nomisweb.co.uk");
            var server = MockRestServiceServer.bindTo(builder).build();
            server.expect(once(), requestTo(CODELIST_URL))
                    .andRespond(withSuccess(CODELIST, MediaType.APPLICATION_JSON));
            server.expect(once(), requestTo(DATA_URL))
                    .andRespond(withSuccess(payload, MediaType.APPLICATION_JSON));
            assertThatThrownBy(() -> new RestClientNomisApiClient(builder.build())
                    .latestUnitedKingdomEmployment("2133"))
                    .isInstanceOf(IllegalStateException.class);
            server.verify();
        }
    }

    @Test
    void propagatesNonSuccessResponseToEvidenceServiceFailureHandling() {
        var builder = RestClient.builder().baseUrl("https://www.nomisweb.co.uk");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(once(), requestTo(CODELIST_URL)).andRespond(withServerError());
        assertThatThrownBy(() -> new RestClientNomisApiClient(builder.build())
                .latestUnitedKingdomEmployment("2133"))
                .isInstanceOf(RuntimeException.class);
        server.verify();
    }
}
