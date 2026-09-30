package com.chikere.futureai.evidence.professional.fixture;

import com.chikere.futureai.evidence.professional.application.EvidenceSource;
import com.chikere.futureai.evidence.professional.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.professional.application.EvidenceStatus;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceBundle;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceCategory;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceItem;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceProvider;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceRequest;
import com.chikere.futureai.evidence.professional.application.ProfessionalEvidenceVersions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Deterministic, explicitly selected UI-development data. It is not external evidence. */
public final class LocalFixtureProfessionalEvidenceProvider implements ProfessionalEvidenceProvider {
    private static final Instant FIXTURE_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final Map<String, Fixture> FIXTURES = Map.of(
            "routine_software_developer", new Fixture("Illustrative developer employment context",
                    "This local example represents an occupational employment count that a live provider could supply.",
                    ProfessionalEvidenceCategory.OCCUPATION_OUTLOOK, "Illustrative employment count", "120000", "people"),
            "senior_software_architect", new Fixture("Illustrative architecture skill context",
                    "This local example shows how a sourced skill-demand observation could be presented.",
                    ProfessionalEvidenceCategory.SKILL_DEMAND, "Illustrative postings mentioning architecture", "4200", "postings"),
            "electrician", new Fixture("Illustrative electrician employment context",
                    "This local example represents a published occupational employment measure.",
                    ProfessionalEvidenceCategory.EMPLOYMENT_TREND, "Illustrative employment count", "235000", "people"),
            "registered_nurse", new Fixture("Illustrative nursing workforce context",
                    "This local example represents an occupational workforce measure from a source snapshot.",
                    ProfessionalEvidenceCategory.OCCUPATION_OUTLOOK, "Illustrative workforce count", "720000", "people"));

    private final String fixtureId;

    public LocalFixtureProfessionalEvidenceProvider(String fixtureId) {
        if (fixtureId == null || !FIXTURES.containsKey(fixtureId.strip())) {
            throw new IllegalArgumentException("Unsupported professional evidence fixture id: " + fixtureId);
        }
        this.fixtureId = fixtureId.strip();
    }

    @Override
    public ProfessionalEvidenceBundle gather(ProfessionalEvidenceRequest request) {
        Fixture fixture = FIXTURES.get(fixtureId);
        var item = new ProfessionalEvidenceItem(fixture.category(), fixture.title(), fixture.summary(),
                "Local fixture evidence", Optional.of(fixtureId), Optional.empty(),
                Optional.of("AI Career Impact development fixture"), Optional.of(request.geography()),
                Optional.empty(), FIXTURE_TIME, Optional.of(fixture.metricName()),
                Optional.of(new BigDecimal(fixture.metricValue())), Optional.of(fixture.metricUnit()),
                EvidenceSourceQuality.DEVELOPMENT_FIXTURE,
                "Representative UI data for the explicitly selected " + fixtureId + " fixture.");
        return new ProfessionalEvidenceBundle(request.assessmentId(),
                ProfessionalEvidenceVersions.EVIDENCE_SCHEMA, source(), providerIdentifier(),
                EvidenceStatus.AVAILABLE, FIXTURE_TIME, List.of(item));
    }

    @Override public EvidenceSource source() { return EvidenceSource.LOCAL_FIXTURE; }
    @Override public String providerIdentifier() { return "local-fixture:" + fixtureId; }

    private record Fixture(String title, String summary, ProfessionalEvidenceCategory category,
                           String metricName, String metricValue, String metricUnit) { }
}
