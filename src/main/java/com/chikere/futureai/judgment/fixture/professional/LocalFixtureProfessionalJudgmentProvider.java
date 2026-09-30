package com.chikere.futureai.judgment.fixture.professional;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Explicit, illustrative judgments for local UI development only. */
public final class LocalFixtureProfessionalJudgmentProvider implements ProfessionalJudgmentProvider {

    private static final double FIXTURE_CONFIDENCE = 0.85;

    private static final Map<String, Fixture> FIXTURES = Map.of(
            "routine_software_developer", new Fixture(0.840, 3.580, 4.000, 0.500, 0.000,
                    0.090, 0.110, 0.500, 3.020),
            "senior_software_architect", new Fixture(0.800, 0.570, 3.710, 2.850, 0.020,
                    3.630, 3.550, 3.460, 3.210),
            "electrician", new Fixture(0.900, 1.500, 0.500, 2.500, 3.900,
                    3.200, 2.800, 3.800, 2.400),
            "registered_nurse", new Fixture(0.900, 1.700, 0.800, 3.800, 3.500,
                    3.700, 3.000, 3.900, 2.600));

    private final Fixture fixture;

    public LocalFixtureProfessionalJudgmentProvider(String fixtureId) {
        String selectedId = Objects.requireNonNull(fixtureId, "fixtureId must not be null").strip();
        this.fixture = FIXTURES.get(selectedId);
        if (fixture == null) {
            throw new IllegalArgumentException("Unsupported app.professional.fixture-id '" + selectedId
                    + "'. Supported fixture IDs: " + supportedFixtureIds());
        }
    }

    @Override
    public AssessmentJudgmentSet judge(ProfessionalAssessmentState state) {
        Objects.requireNonNull(state, "state must not be null");
        return new AssessmentJudgmentSet(JourneyType.PROFESSIONAL, ProfessionalJudgmentSchema.VERSION, List.of(
                informationJudgment(fixture.informationSufficiency()),
                scoreJudgment(ProfessionalJudgmentSchema.TASK_REPEATABILITY, "Task repeatability",
                        fixture.taskRepeatability()),
                scoreJudgment(ProfessionalJudgmentSchema.DIGITAL_EXECUTABILITY, "Digital executability",
                        fixture.digitalExecutability()),
                scoreJudgment(ProfessionalJudgmentSchema.HUMAN_TRUST_DEPENDENCY, "Human trust dependency",
                        fixture.humanTrustDependency()),
                scoreJudgment(ProfessionalJudgmentSchema.PHYSICAL_WORLD_DEPENDENCY, "Physical-world dependency",
                        fixture.physicalWorldDependency()),
                scoreJudgment(ProfessionalJudgmentSchema.CONTEXTUAL_COMPLEXITY, "Contextual complexity",
                        fixture.contextualComplexity()),
                scoreJudgment(ProfessionalJudgmentSchema.ORIGINAL_JUDGMENT, "Original judgment",
                        fixture.originalJudgment()),
                scoreJudgment(ProfessionalJudgmentSchema.ACCOUNTABILITY_RESPONSIBILITY,
                        "Accountability and responsibility", fixture.accountabilityResponsibility()),
                scoreJudgment(ProfessionalJudgmentSchema.AI_COMPLEMENTARITY, "AI complementarity",
                        fixture.aiComplementarity())));
    }

    @Override
    public AssessmentSource source() {
        return AssessmentSource.LOCAL_FIXTURE;
    }

    public static List<String> supportedFixtureIds() {
        return FIXTURES.keySet().stream().sorted().toList();
    }

    private static DimensionJudgment informationJudgment(double value) {
        return new DimensionJudgment(ProfessionalJudgmentSchema.INFORMATION_SUFFICIENT,
                "Information sufficiency", OptionalDouble.of(value), Optional.empty(),
                Optional.empty(), OptionalDouble.empty(), Map.of());
    }

    private static DimensionJudgment scoreJudgment(String id, String name, double value) {
        return new DimensionJudgment(id, name, OptionalDouble.of(value), Optional.empty(),
                Optional.empty(), OptionalDouble.of(FIXTURE_CONFIDENCE), Map.of());
    }

    private record Fixture(
            double informationSufficiency,
            double taskRepeatability,
            double digitalExecutability,
            double humanTrustDependency,
            double physicalWorldDependency,
            double contextualComplexity,
            double originalJudgment,
            double accountabilityResponsibility,
            double aiComplementarity) {
    }
}
