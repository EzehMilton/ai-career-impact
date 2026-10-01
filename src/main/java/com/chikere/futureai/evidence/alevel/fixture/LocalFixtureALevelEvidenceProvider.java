package com.chikere.futureai.evidence.alevel.fixture;

import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceBundle;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceCategory;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceItem;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceProvider;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceRequest;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceVersions;
import com.chikere.futureai.evidence.alevel.application.EvidenceSource;
import com.chikere.futureai.evidence.alevel.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.alevel.application.EvidenceStatus;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Deterministic, explicitly selected UI-development data. It is not external evidence.
 *
 * <p>Items are phrased as neutral factual context and are labelled as a development fixture, so a
 * prerequisite note can never be mistaken for a real admissions statement or read as a verdict on
 * the student's choices. No fixture item carries a government or university URL.</p>
 */
public final class LocalFixtureALevelEvidenceProvider implements ALevelEvidenceProvider {

    private static final Instant FIXTURE_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private static final Map<String, Fixture> FIXTURES;

    static {
        LinkedHashMap<String, Fixture> fixtures = new LinkedHashMap<>();
        fixtures.put("maths_economics_geography", new Fixture(
                "Illustrative subject requirement context",
                "This local example shows how a sourced note could read: some Mathematics-heavy "
                        + "Economics courses ask for A-level Mathematics. Always check each course's own "
                        + "published requirements.",
                ALevelEvidenceCategory.DEGREE_PREREQUISITES));
        fixtures.put("mathematics_physics_computer_science", new Fixture(
                "Illustrative engineering entry context",
                "This local example shows how a sourced note could read: many Engineering courses ask "
                        + "for A-level Mathematics and often Physics. Always check each course's own "
                        + "published requirements.",
                ALevelEvidenceCategory.DEGREE_PREREQUISITES));
        fixtures.put("biology_chemistry_mathematics", new Fixture(
                "Illustrative health-course entry context",
                "This local example shows how a sourced note could read: Medicine courses commonly ask "
                        + "for A-level Chemistry and often Biology. Always check each course's own "
                        + "published requirements.",
                ALevelEvidenceCategory.COURSE_ENTRY_CONTEXT));
        fixtures.put("english_history_politics", new Fixture(
                "Illustrative humanities pathway context",
                "This local example shows how a sourced note could describe the range of degree "
                        + "subjects that commonly accept essay-based A-levels without naming a specific "
                        + "required subject.",
                ALevelEvidenceCategory.SUBJECT_PATHWAY_CONTEXT));
        fixtures.put("art_design_media", new Fixture(
                "Illustrative creative pathway context",
                "This local example shows how a sourced note could describe portfolio expectations "
                        + "that some creative courses publish alongside subject requirements.",
                ALevelEvidenceCategory.COURSE_ENTRY_CONTEXT));
        fixtures.put("mixed_balanced", new Fixture(
                "Illustrative skill demand context",
                "This local example shows how a sourced labour-market observation about demand for "
                        + "combined analytical and communication skills could be presented.",
                ALevelEvidenceCategory.SKILL_DEMAND_CONTEXT));
        FIXTURES = Map.copyOf(fixtures);
    }

    private final String fixtureId;

    public LocalFixtureALevelEvidenceProvider(String fixtureId) {
        if (fixtureId == null || !FIXTURES.containsKey(fixtureId.strip())) {
            throw new IllegalArgumentException("Unsupported A-level evidence fixture id: " + fixtureId);
        }
        this.fixtureId = fixtureId.strip();
    }

    @Override
    public ALevelEvidenceBundle gather(ALevelEvidenceRequest request) {
        Fixture fixture = FIXTURES.get(fixtureId);
        var item = new ALevelEvidenceItem(fixture.category(), fixture.title(), fixture.summary(),
                "Local fixture evidence", Optional.of(fixtureId), Optional.empty(),
                Optional.of("AI Career Impact development fixture"), Optional.of(request.geography()),
                Optional.empty(), FIXTURE_TIME, Optional.empty(), Optional.empty(), Optional.empty(),
                EvidenceSourceQuality.DEVELOPMENT_FIXTURE,
                "Factual context only for the explicitly selected " + fixtureId + " fixture. "
                        + "This does not change your assessment and is not a judgement on your subjects.");
        return new ALevelEvidenceBundle(request.assessmentId(), ALevelEvidenceVersions.EVIDENCE_SCHEMA,
                source(), providerIdentifier(), EvidenceStatus.AVAILABLE, FIXTURE_TIME, List.of(item));
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

    private record Fixture(String title, String summary, ALevelEvidenceCategory category) { }
}
