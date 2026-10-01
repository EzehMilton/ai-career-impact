package com.chikere.futureai.evidence.university.fixture;

import com.chikere.futureai.evidence.university.application.EvidenceSource;
import com.chikere.futureai.evidence.university.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.university.application.EvidenceStatus;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceBundle;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceCategory;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceItem;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceProvider;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceRequest;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceVersions;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic, explicitly selected UI-development data. It is not external evidence.
 *
 * <p>Items carry no government URL and are labelled as a development fixture so they can never be
 * mistaken for official statistics about any real course or institution.</p>
 */
public final class LocalFixtureUniversityEvidenceProvider implements UniversityEvidenceProvider {

    private static final Instant FIXTURE_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final Map<String, Fixture> FIXTURES;

    static {
        LinkedHashMap<String, Fixture> fixtures = new LinkedHashMap<>();
        fixtures.put("computer_science", new Fixture(
                "Illustrative computing graduate destinations",
                "This local example shows how a sourced graduate-destinations observation could be presented.",
                UniversityEvidenceCategory.OCCUPATIONAL_DESTINATIONS,
                "Illustrative graduates entering technical roles", "7400", "graduates"));
        fixtures.put("nursing", new Fixture(
                "Illustrative nursing workforce context",
                "This local example represents an occupational workforce measure from a source snapshot.",
                UniversityEvidenceCategory.LABOUR_MARKET_CONTEXT,
                "Illustrative workforce count", "720000", "people"));
        fixtures.put("economics", new Fixture(
                "Illustrative analytical skill demand",
                "This local example shows how a sourced skill-demand observation could be presented.",
                UniversityEvidenceCategory.SKILL_DEMAND,
                "Illustrative postings mentioning quantitative analysis", "5100", "postings"));
        fixtures.put("graphic_design", new Fixture(
                "Illustrative design graduate outcomes",
                "This local example represents a published graduate-outcomes measure.",
                UniversityEvidenceCategory.GRADUATE_OUTCOMES,
                "Illustrative graduates in creative roles", "2600", "graduates"));
        fixtures.put("mechanical_engineering", new Fixture(
                "Illustrative engineering course content context",
                "This local example represents how accredited course content could be described.",
                UniversityEvidenceCategory.COURSE_CONTENT,
                "Illustrative accredited programmes", "180", "programmes"));
        fixtures.put("history", new Fixture(
                "Illustrative humanities graduate destinations",
                "This local example shows the breadth of destinations a sourced dataset could report.",
                UniversityEvidenceCategory.OCCUPATIONAL_DESTINATIONS,
                "Illustrative distinct destination categories", "26", "categories"));
        FIXTURES = Map.copyOf(fixtures);
    }

    private final String fixtureId;

    public LocalFixtureUniversityEvidenceProvider(String fixtureId) {
        if (fixtureId == null || !FIXTURES.containsKey(fixtureId.strip())) {
            throw new IllegalArgumentException("Unsupported university evidence fixture id: " + fixtureId);
        }
        this.fixtureId = fixtureId.strip();
    }

    @Override
    public UniversityEvidenceBundle gather(UniversityEvidenceRequest request) {
        Fixture fixture = FIXTURES.get(fixtureId);
        var item = new UniversityEvidenceItem(fixture.category(), fixture.title(), fixture.summary(),
                "Local fixture evidence", Optional.of(fixtureId), Optional.empty(),
                Optional.of("AI Career Impact development fixture"), Optional.of(request.geography()),
                Optional.empty(), FIXTURE_TIME, Optional.of(fixture.metricName()),
                Optional.of(new BigDecimal(fixture.metricValue())), Optional.of(fixture.metricUnit()),
                EvidenceSourceQuality.DEVELOPMENT_FIXTURE,
                "Representative UI data for the explicitly selected " + fixtureId + " fixture.");
        return new UniversityEvidenceBundle(request.assessmentId(),
                UniversityEvidenceVersions.EVIDENCE_SCHEMA, source(), providerIdentifier(),
                EvidenceStatus.AVAILABLE, FIXTURE_TIME, List.of(item));
    }

    @Override
    public EvidenceSource source() {
        return EvidenceSource.LOCAL_FIXTURE;
    }

    @Override
    public String providerIdentifier() {
        return "local-fixture:" + fixtureId;
    }

    public static List<String> supportedFixtureIds() {
        return FIXTURES.keySet().stream().sorted().toList();
    }

    private record Fixture(String title, String summary, UniversityEvidenceCategory category,
                           String metricName, String metricValue, String metricUnit) { }
}
