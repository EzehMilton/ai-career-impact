package com.chikere.futureai.evidence.professional.nomis;

import com.chikere.futureai.evidence.professional.application.*;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Live, opt-in adapter for the ONS Annual Population Survey exposed by Nomis. */
public final class NomisProfessionalEvidenceProvider implements ProfessionalEvidenceProvider {
    private static final URI DATASET_URL = URI.create("https://www.nomisweb.co.uk/datasets/aps218");
    private final NomisApiClient client;
    private final Clock clock;

    NomisProfessionalEvidenceProvider(NomisApiClient client, Clock clock) {
        this.client = client;
        this.clock = clock;
    }

    @Override
    public ProfessionalEvidenceBundle gather(ProfessionalEvidenceRequest request) {
        String classification = request.occupationClassification().orElseThrow(() ->
                new IllegalArgumentException("An explicitly configured SOC 2020 occupation code is required"));
        NomisObservation observation = client.latestUnitedKingdomEmployment(classification);
        Instant retrievedAt = Instant.now(clock);
        var item = new ProfessionalEvidenceItem(ProfessionalEvidenceCategory.OCCUPATION_OUTLOOK,
                "Employment in " + observation.occupationName(),
                "The latest available Annual Population Survey estimate for this SOC 2020 occupation is shown below.",
                "Nomis APS218", Optional.of("APS218 / SOC 2020 " + classification),
                Optional.of(DATASET_URL), Optional.of("Office for National Statistics"),
                Optional.of(request.geography()), Optional.empty(), retrievedAt,
                Optional.of("Estimated employment (" + observation.period() + ")"),
                Optional.of(observation.value()), Optional.of(observation.unit()),
                EvidenceSourceQuality.OFFICIAL,
                "This is occupational labour-market context and does not determine the assessment scores.");
        return new ProfessionalEvidenceBundle(request.assessmentId(),
                ProfessionalEvidenceVersions.EVIDENCE_SCHEMA, source(), providerIdentifier(),
                EvidenceStatus.AVAILABLE, retrievedAt, List.of(item));
    }

    @Override public EvidenceSource source() { return EvidenceSource.LIVE; }
    @Override public String providerIdentifier() { return "nomis-aps218"; }
}
