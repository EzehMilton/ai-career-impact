package com.chikere.futureai.judgment.fixture.alevel;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJudgmentSchemaV2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Explicit, illustrative {@code a-level-v2} judgments for local UI development only.
 *
 * <p>Development data, not empirical claims about any subject or combination. The fixture is keyed by the configured
 * {@code app.a-level.fixture-id}, never by the subjects a user typed, so it cannot become a hidden subject-ranking
 * table. Where the same subjects exist, the values are the approved Stage A1/A2 offline calibration judgments; the
 * ids match the historical v1 fixtures so existing local configuration keeps working.</p>
 */
public final class LocalFixtureALevelV2JudgmentProvider implements ALevelJudgmentProvider {

    private static final double FIXTURE_CONFIDENCE = 0.85;

    private static final Map<String, Fixture> FIXTURES;

    static {
        LinkedHashMap<String, Fixture> fixtures = new LinkedHashMap<>();
        //                                         info  quant  anly  comm  creat  ctx   tech  div  family  ai
        fixtures.put("maths_economics_geography", new Fixture(
                0.880, 3.400, 3.400, 3.000, 2.200, 3.600, 1.600, 3.400, 3.400, 3.000));
        fixtures.put("mathematics_physics_computer_science", new Fixture(
                0.900, 4.000, 3.800, 1.600, 2.400, 1.200, 4.000, 1.800, 2.600, 3.800));
        fixtures.put("biology_chemistry_mathematics", new Fixture(
                0.880, 3.400, 3.600, 1.800, 1.800, 2.000, 1.600, 2.000, 2.200, 2.800));
        fixtures.put("english_history_politics", new Fixture(
                0.860, 0.800, 3.200, 4.000, 3.400, 4.000, 0.600, 2.200, 2.800, 2.400));
        fixtures.put("art_design_media", new Fixture(
                0.720, 0.600, 1.800, 2.800, 4.000, 2.600, 2.200, 2.400, 1.600, 2.600));
        fixtures.put("mixed_balanced", new Fixture(
                0.840, 2.800, 3.000, 3.200, 2.800, 3.000, 2.400, 4.000, 3.600, 3.000));
        FIXTURES = Map.copyOf(fixtures);
    }

    private final Fixture fixture;
    private final String fixtureId;

    public LocalFixtureALevelV2JudgmentProvider(String fixtureId) {
        String selectedId = Objects.requireNonNull(fixtureId, "fixtureId must not be null").strip();
        this.fixture = FIXTURES.get(selectedId);
        this.fixtureId = selectedId;
        if (fixture == null) {
            throw new IllegalArgumentException("Unsupported app.a-level.fixture-id '" + selectedId
                    + "'. Supported fixture IDs: " + supportedFixtureIds());
        }
    }

    @Override
    public AssessmentJudgmentSet judge(ALevelJudgmentInput input) {
        Objects.requireNonNull(input, "input must not be null");
        return new AssessmentJudgmentSet(JourneyType.A_LEVEL, ALevelJudgmentSchemaV2.VERSION, List.of(
                new DimensionJudgment(ALevelJudgmentSchemaV2.INFORMATION_SUFFICIENT,
                        ALevelJudgmentSchemaV2.nameOf(ALevelJudgmentSchemaV2.INFORMATION_SUFFICIENT),
                        OptionalDouble.of(fixture.informationSufficiency()), Optional.empty(), Optional.empty(),
                        OptionalDouble.empty(), Map.of()),
                score(ALevelJudgmentSchemaV2.QUANTITATIVE_REASONING, fixture.quantitativeReasoning()),
                score(ALevelJudgmentSchemaV2.ANALYTICAL_REASONING, fixture.analyticalReasoning()),
                score(ALevelJudgmentSchemaV2.WRITTEN_VERBAL_COMMUNICATION, fixture.writtenVerbalCommunication()),
                score(ALevelJudgmentSchemaV2.ORIGINAL_JUDGMENT_CREATIVITY, fixture.originalJudgmentCreativity()),
                score(ALevelJudgmentSchemaV2.CONTEXTUAL_SOCIAL_UNDERSTANDING, fixture.contextualSocialUnderstanding()),
                score(ALevelJudgmentSchemaV2.TECHNICAL_DIGITAL_FOUNDATION, fixture.technicalDigitalFoundation()),
                score(ALevelJudgmentSchemaV2.SUBJECT_SKILL_DIVERSITY, fixture.subjectSkillDiversity()),
                score(ALevelJudgmentSchemaV2.DISCIPLINE_FAMILY_COVERAGE, fixture.disciplineFamilyCoverage()),
                score(ALevelJudgmentSchemaV2.AI_LEARNING_SUPPORT, fixture.aiLearningSupport())));
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

    private static DimensionJudgment score(String id, double value) {
        return new DimensionJudgment(id, ALevelJudgmentSchemaV2.nameOf(id), OptionalDouble.of(value),
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
            double disciplineFamilyCoverage,
            double aiLearningSupport) {
    }
}
