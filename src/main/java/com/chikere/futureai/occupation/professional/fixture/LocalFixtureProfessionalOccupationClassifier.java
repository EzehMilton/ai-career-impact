package com.chikere.futureai.occupation.professional.fixture;

import com.chikere.futureai.occupation.professional.application.*;

import java.util.Map;
import java.util.Optional;

public final class LocalFixtureProfessionalOccupationClassifier implements ProfessionalOccupationClassifier {
    private static final Map<String, Mapping> MAPPINGS = Map.of(
            "routine_software_developer", new Mapping("Programmers and software development professionals", "2134"),
            "senior_software_architect", new Mapping("IT business analysts, architects and systems designers", "2133"),
            "electrician", new Mapping("Electricians and electrical fitters", "5241"),
            "registered_nurse", new Mapping("Nurses", "2231"));
    private final String fixtureId;

    public LocalFixtureProfessionalOccupationClassifier(String fixtureId) {
        if (fixtureId == null || !MAPPINGS.containsKey(fixtureId.strip())) {
            throw new IllegalArgumentException("Unsupported professional occupation fixture id: " + fixtureId);
        }
        this.fixtureId = fixtureId.strip();
    }

    @Override
    public Optional<ProfessionalOccupationClassification> classify(
            ProfessionalOccupationClassificationRequest request) {
        Mapping mapping = MAPPINGS.get(fixtureId);
        return Optional.of(new ProfessionalOccupationClassification(mapping.title(),
                OccupationClassificationSystem.SOC_2020, mapping.code(),
                OccupationClassificationSource.LOCAL_FIXTURE,
                OccupationClassificationQuality.DEVELOPMENT_FIXTURE,
                "local-fixture:" + fixtureId, ProfessionalOccupationClassifierVersions.V1));
    }

    private record Mapping(String title, String code) { }
}
