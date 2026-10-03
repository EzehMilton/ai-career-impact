package com.chikere.futureai.evidence.professional.nomis;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.evidence.professional.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.professional.application.EvidenceStatus;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Explicit opt-in live adapter check; never selected by normal verify. */
@EnabledIfSystemProperty(named = "evidence.live.evaluation.enabled", matches = "true")
class NomisLiveEvidenceIT {
    @Test
    void officialSoc2133UnitedKingdomEmployment() throws Exception {
        var configuration = new NomisProfessionalEvidenceConfiguration();
        var provider = new NomisProfessionalEvidenceProvider(
                configuration.nomisApiClient(RestClient.builder()), java.time.Clock.systemUTC());
        var request = new ProfessionalEvidenceRequest(UUID.randomUUID(), "Senior Software Architect",
                List.of("Design software architecture"), List.of("System design"),
                "United Kingdom", JourneyType.PROFESSIONAL, Optional.of("2133"));
        long start = System.nanoTime();
        var bundle = provider.gather(request);
        long elapsed = (System.nanoTime() - start) / 1_000_000;
        assertThat(bundle.status()).isEqualTo(EvidenceStatus.AVAILABLE);
        assertThat(bundle.items()).singleElement().satisfies(item -> {
            assertThat(item.sourceQuality()).isEqualTo(EvidenceSourceQuality.OFFICIAL);
            assertThat(item.geography()).contains("United Kingdom");
            assertThat(item.sourceReference()).contains("APS218 / SOC 2020 2133");
            assertThat(item.title()).contains("IT business analysts, architects and systems designers");
            assertThat(item.metricValue()).isPresent();
        });
        var item = bundle.items().getFirst();
        Path report = Path.of("target", "live-evidence", "nomis-2133-report.md");
        Files.createDirectories(report.getParent());
        Files.writeString(report, "# Live Nomis APS218 / SOC 2020 2133\n\n"
                + "- Elapsed: " + elapsed + " ms\n"
                + "- Requests: 2 (SOC codelist, one observation)\n"
                + "- Status: " + bundle.status() + "\n"
                + "- Source: " + bundle.source() + " / " + bundle.providerIdentifier() + "\n"
                + "- Occupation: " + item.title() + "\n"
                + "- Geography: " + item.geography().orElseThrow() + "\n"
                + "- Dataset/reference: " + item.sourceReference().orElseThrow() + "\n"
                + "- Reference period and metric: " + item.metricName().orElseThrow() + ": "
                + item.metricValue().orElseThrow() + " " + item.metricUnit().orElseThrow() + "\n"
                + "- Publication date: " + item.publicationDate().map(Object::toString)
                        .orElse("not supplied in observation") + "\n"
                + "- Retrieved at: " + item.retrievedAt() + "\n"
                + "- Quality: " + item.sourceQuality() + "\n"
                + "- URL: " + item.sourceUrl().orElseThrow() + "\n");
        System.out.println("Live Nomis report written to " + report.toAbsolutePath());
    }
}
