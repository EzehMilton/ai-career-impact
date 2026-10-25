package com.chikere.futureai.judgment.fixture.university;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.typesafe.university.UniversityDimensionNames;
import com.chikere.futureai.judgment.typesafe.university.UniversityJudgmentSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Explicit, illustrative university judgments for local UI development only.
 *
 * <p>These numbers are development data chosen to exercise contrasting presentation paths. They are
 * not empirical findings about any course, institution or subject, and they are never selected
 * automatically: the exact fixture ID must be configured.</p>
 */
public final class LocalFixtureUniversityJudgmentProvider implements UniversityJudgmentProvider {

    private static final double FIXTURE_CONFIDENCE = 0.85;

    private static final Map<String, Fixture> FIXTURES;

    static {
        LinkedHashMap<String, Fixture> fixtures = new LinkedHashMap<>();
        //                                     info  tech  anly  orig  human ctxt  prac  aiC   xfer  brdth
        fixtures.put("computer_science", new Fixture(
                0.880, 4.000, 3.800, 3.000, 1.400, 2.600, 3.200, 3.800, 3.400, 3.500));
        fixtures.put("nursing", new Fixture(
                0.900, 1.600, 2.600, 2.200, 4.000, 3.600, 4.000, 2.600, 2.400, 2.000));
        fixtures.put("economics", new Fixture(
                0.860, 2.600, 3.800, 2.800, 2.000, 3.200, 2.000, 3.400, 3.600, 3.600));
        fixtures.put("graphic_design", new Fixture(
                0.720, 2.800, 1.600, 4.000, 2.600, 2.400, 3.600, 3.000, 2.600, 2.600));
        fixtures.put("mechanical_engineering", new Fixture(
                0.880, 3.600, 3.600, 2.800, 2.000, 2.800, 3.800, 3.200, 3.000, 3.000));
        fixtures.put("history", new Fixture(
                0.820, 1.000, 3.000, 3.400, 2.400, 3.400, 1.200, 2.400, 3.000, 2.800));
        FIXTURES = Map.copyOf(fixtures);
    }

    private final Fixture fixture;
    private final String fixtureId;

    public LocalFixtureUniversityJudgmentProvider(String fixtureId) {
        String selectedId = Objects.requireNonNull(fixtureId, "fixtureId must not be null").strip();
        this.fixture = FIXTURES.get(selectedId);
        this.fixtureId = selectedId;
        if (fixture == null) {
            throw new IllegalArgumentException("Unsupported app.university.fixture-id '" + selectedId
                    + "'. Supported fixture IDs: " + supportedFixtureIds());
        }
    }

    @Override
    public AssessmentJudgmentSet judge(UniversityAssessmentState state) {
        Objects.requireNonNull(state, "state must not be null");
        return new AssessmentJudgmentSet(JourneyType.UNIVERSITY, UniversityJudgmentSchema.VERSION, List.of(
                informationJudgment(fixture.informationSufficiency()),
                score(UniversityJudgmentSchema.TECHNICAL_DIGITAL_DEPTH, fixture.technicalDigitalDepth()),
                score(UniversityJudgmentSchema.ANALYTICAL_REASONING, fixture.analyticalReasoning()),
                score(UniversityJudgmentSchema.ORIGINAL_JUDGMENT_CREATIVITY, fixture.originalJudgmentCreativity()),
                score(UniversityJudgmentSchema.HUMAN_RELATIONSHIP_DEPENDENCY, fixture.humanRelationshipDependency()),
                score(UniversityJudgmentSchema.CONTEXTUAL_COMPLEXITY, fixture.contextualComplexity()),
                score(UniversityJudgmentSchema.PRACTICAL_REAL_WORLD_APPLICATION,
                        fixture.practicalRealWorldApplication()),
                score(UniversityJudgmentSchema.AI_COMPLEMENTARITY, fixture.aiComplementarity()),
                score(UniversityJudgmentSchema.SKILL_TRANSFERABILITY, fixture.skillTransferability()),
                score(UniversityJudgmentSchema.CAREER_BREADTH, fixture.careerBreadth())));
    }

    @Override
    public AssessmentSource source() {
        return AssessmentSource.LOCAL_FIXTURE;
    }

    @Override
    public Optional<String> fixtureId() {
        return Optional.of(fixtureId);
    }

    public static List<String> supportedFixtureIds() {
        return FIXTURES.keySet().stream().sorted().toList();
    }

    private static DimensionJudgment informationJudgment(double value) {
        return new DimensionJudgment(UniversityJudgmentSchema.INFORMATION_SUFFICIENT,
                UniversityDimensionNames.of(UniversityJudgmentSchema.INFORMATION_SUFFICIENT),
                OptionalDouble.of(value), Optional.empty(), Optional.empty(), OptionalDouble.empty(), Map.of());
    }

    private static DimensionJudgment score(String id, double value) {
        return new DimensionJudgment(id, UniversityDimensionNames.of(id), OptionalDouble.of(value),
                Optional.empty(), Optional.empty(), OptionalDouble.of(FIXTURE_CONFIDENCE), Map.of());
    }

    private record Fixture(
            double informationSufficiency,
            double technicalDigitalDepth,
            double analyticalReasoning,
            double originalJudgmentCreativity,
            double humanRelationshipDependency,
            double contextualComplexity,
            double practicalRealWorldApplication,
            double aiComplementarity,
            double skillTransferability,
            double careerBreadth) {
    }
}
