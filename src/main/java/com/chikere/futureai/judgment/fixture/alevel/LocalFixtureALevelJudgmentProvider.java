package com.chikere.futureai.judgment.fixture.alevel;

import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelDimensionNames;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJudgmentSchema;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Explicit, illustrative historical {@code a-level-v1} judgments, kept unchanged so v1 behaviour and its calibration
 * baseline stay reproducible. It is no longer the runtime provider: new local submissions use
 * {@link LocalFixtureALevelV2JudgmentProvider}.
 *
 * <p>These numbers are development data chosen to exercise contrasting presentation paths. They
 * are <strong>not</strong> empirical claims about any subject or combination, and they are never
 * selected automatically: the exact fixture ID must be configured. The fixture is keyed by a
 * configured identifier, not by the subjects a user typed, precisely so this cannot become a
 * hidden subject-ranking table.</p>
 */
public final class LocalFixtureALevelJudgmentProvider {

    private static final double FIXTURE_CONFIDENCE = 0.85;

    private static final Map<String, Fixture> FIXTURES;

    static {
        LinkedHashMap<String, Fixture> fixtures = new LinkedHashMap<>();
        //                                        info  quant  anly  comm  creat  ctx   tech  div  breadth xfer  ai
        fixtures.put("maths_economics_geography", new Fixture(
                0.880, 3.400, 3.400, 3.000, 2.200, 3.600, 1.600, 3.400, 3.600, 3.400, 3.000));
        fixtures.put("mathematics_physics_computer_science", new Fixture(
                0.900, 4.000, 3.800, 1.600, 2.400, 1.200, 4.000, 1.800, 2.800, 3.000, 3.800));
        fixtures.put("biology_chemistry_mathematics", new Fixture(
                0.880, 3.400, 3.600, 1.800, 1.800, 2.000, 1.600, 2.000, 3.000, 2.800, 2.800));
        fixtures.put("english_history_politics", new Fixture(
                0.860, 0.800, 3.200, 4.000, 3.400, 4.000, 0.600, 2.200, 2.600, 3.000, 2.400));
        fixtures.put("art_design_media", new Fixture(
                0.720, 0.600, 1.800, 2.800, 4.000, 2.600, 2.200, 2.400, 1.600, 2.200, 2.600));
        fixtures.put("mixed_balanced", new Fixture(
                0.840, 2.800, 3.000, 3.200, 2.800, 3.000, 2.400, 4.000, 3.800, 3.600, 3.000));
        FIXTURES = Map.copyOf(fixtures);
    }

    private final Fixture fixture;
    private final String fixtureId;

    public LocalFixtureALevelJudgmentProvider(String fixtureId) {
        String selectedId = Objects.requireNonNull(fixtureId, "fixtureId must not be null").strip();
        this.fixture = FIXTURES.get(selectedId);
        this.fixtureId = selectedId;
        if (fixture == null) {
            throw new IllegalArgumentException("Unsupported app.a-level.fixture-id '" + selectedId
                    + "'. Supported fixture IDs: " + supportedFixtureIds());
        }
    }

    public AssessmentJudgmentSet judge(ALevelAssessmentState state) {
        Objects.requireNonNull(state, "state must not be null");
        return new AssessmentJudgmentSet(JourneyType.A_LEVEL, ALevelJudgmentSchema.VERSION, List.of(
                informationJudgment(fixture.informationSufficiency()),
                score(ALevelJudgmentSchema.QUANTITATIVE_REASONING, fixture.quantitativeReasoning()),
                score(ALevelJudgmentSchema.ANALYTICAL_REASONING, fixture.analyticalReasoning()),
                score(ALevelJudgmentSchema.WRITTEN_VERBAL_COMMUNICATION, fixture.writtenVerbalCommunication()),
                score(ALevelJudgmentSchema.ORIGINAL_JUDGMENT_CREATIVITY, fixture.originalJudgmentCreativity()),
                score(ALevelJudgmentSchema.CONTEXTUAL_SOCIAL_UNDERSTANDING,
                        fixture.contextualSocialUnderstanding()),
                score(ALevelJudgmentSchema.TECHNICAL_DIGITAL_FOUNDATION, fixture.technicalDigitalFoundation()),
                score(ALevelJudgmentSchema.SUBJECT_SKILL_DIVERSITY, fixture.subjectSkillDiversity()),
                score(ALevelJudgmentSchema.DEGREE_PATHWAY_BREADTH, fixture.degreePathwayBreadth()),
                score(ALevelJudgmentSchema.SKILL_TRANSFERABILITY, fixture.skillTransferability()),
                score(ALevelJudgmentSchema.AI_ERA_COMPLEMENTARITY, fixture.aiEraComplementarity())));
    }

    public AssessmentSource source() {
        return AssessmentSource.LOCAL_FIXTURE;
    }

    public Optional<String> fixtureId() {
        return Optional.of(fixtureId);
    }

    public static List<String> supportedFixtureIds() {
        return FIXTURES.keySet().stream().sorted().toList();
    }

    private static DimensionJudgment informationJudgment(double value) {
        return new DimensionJudgment(ALevelJudgmentSchema.INFORMATION_SUFFICIENT,
                ALevelDimensionNames.of(ALevelJudgmentSchema.INFORMATION_SUFFICIENT),
                OptionalDouble.of(value), Optional.empty(), Optional.empty(), OptionalDouble.empty(), Map.of());
    }

    private static DimensionJudgment score(String id, double value) {
        return new DimensionJudgment(id, ALevelDimensionNames.of(id), OptionalDouble.of(value),
                Optional.empty(), Optional.empty(), OptionalDouble.of(FIXTURE_CONFIDENCE), Map.of());
    }

    private record Fixture(
            double informationSufficiency,
            double quantitativeReasoning,
            double analyticalReasoning,
            double writtenVerbalCommunication,
            double originalJudgmentCreativity,
            double contextualSocialUnderstanding,
            double technicalDigitalFoundation,
            double subjectSkillDiversity,
            double degreePathwayBreadth,
            double skillTransferability,
            double aiEraComplementarity) {
    }
}
