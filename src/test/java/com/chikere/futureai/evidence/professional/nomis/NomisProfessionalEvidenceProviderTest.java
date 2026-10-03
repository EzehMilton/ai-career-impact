package com.chikere.futureai.evidence.professional.nomis;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.evidence.professional.application.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NomisProfessionalEvidenceProviderTest {
    @Test
    void mapsOfflineClientObservationToSharedEvidenceContract() {
        Instant now = Instant.parse("2026-09-30T12:00:00Z");
        NomisApiClient client = code -> new NomisObservation("Apr 2025-Mar 2026",
                "IT business analysts, architects and systems designers", "United Kingdom",
                new BigDecimal("123400"), "people");
        var provider = new NomisProfessionalEvidenceProvider(client, Clock.fixed(now, ZoneOffset.UTC));
        var request = request(Optional.of("2133"));

        var bundle = provider.gather(request);

        assertThat(bundle.status()).isEqualTo(EvidenceStatus.AVAILABLE);
        assertThat(bundle.source()).isEqualTo(EvidenceSource.LIVE);
        assertThat(bundle.providerIdentifier()).isEqualTo("nomis-aps218");
        assertThat(bundle.items()).singleElement().satisfies(item -> {
            assertThat(item.category()).isEqualTo(ProfessionalEvidenceCategory.OCCUPATION_OUTLOOK);
            assertThat(item.sourceQuality()).isEqualTo(EvidenceSourceQuality.OFFICIAL);
            assertThat(item.publisher()).contains("Office for National Statistics");
            assertThat(item.metricValue()).contains(new BigDecimal("123400"));
            assertThat(item.sourceUrl()).isPresent();
            assertThat(item.retrievedAt()).isEqualTo(now);
        });
    }

    @Test
    void refusesToGuessAnOccupationClassification() {
        NomisApiClient unused = code -> { throw new AssertionError("must not call client"); };
        var provider = new NomisProfessionalEvidenceProvider(unused, Clock.systemUTC());
        assertThatThrownBy(() -> provider.gather(request(Optional.empty())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("explicitly configured");
    }

    @Test
    void rejectsUnsupportedGeographyBeforeMakingAnApiRequest() {
        NomisApiClient unused = code -> { throw new AssertionError("must not call client"); };
        var provider = new NomisProfessionalEvidenceProvider(unused, Clock.systemUTC());
        var regional = new ProfessionalEvidenceRequest(UUID.randomUUID(), "Software Architect",
                List.of("Design"), List.of("Architecture"), "England", JourneyType.PROFESSIONAL,
                Optional.of("2133"));
        assertThatThrownBy(() -> provider.gather(regional))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("United Kingdom only");
    }

    private ProfessionalEvidenceRequest request(Optional<String> code) {
        return new ProfessionalEvidenceRequest(UUID.randomUUID(), "Software Architect", List.of("Design"),
                List.of("Architecture"), "United Kingdom", JourneyType.PROFESSIONAL, code);
    }
}
