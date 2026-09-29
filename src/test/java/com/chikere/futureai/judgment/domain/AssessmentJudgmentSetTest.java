package com.chikere.futureai.judgment.domain;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.JourneyType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssessmentJudgmentSetTest {

    @Test
    void preservesSchemaVersionAndAtomicJudgments() {
        DimensionJudgment judgment = new DimensionJudgment(
                "task-routine-intensity",
                "Routine task intensity",
                OptionalDouble.of(0.65),
                Optional.of("MODERATE"),
                Optional.of(AssessmentConfidence.HIGH),
                Map.of("LOW", 0.1, "MODERATE", 0.7, "HIGH", 0.2));

        AssessmentJudgmentSet set = new AssessmentJudgmentSet(
                JourneyType.PROFESSIONAL, "professional-v1", List.of(judgment));

        assertThat(set.schemaVersion()).isEqualTo("professional-v1");
        assertThat(set.judgments()).containsExactly(judgment);
        assertThat(set.judgments().getFirst().dimensionId()).isEqualTo("task-routine-intensity");
    }

    @Test
    void judgmentCollectionsAndProbabilityDistributionsAreImmutableCopies() {
        LinkedHashMap<String, Double> probabilities = new LinkedHashMap<>();
        probabilities.put("NARROW", 0.25);
        probabilities.put("BROAD", 0.75);
        DimensionJudgment judgment = new DimensionJudgment(
                "optionality",
                "Future optionality",
                OptionalDouble.empty(),
                Optional.of("BROAD"),
                Optional.of(AssessmentConfidence.MODERATE),
                probabilities);
        ArrayList<DimensionJudgment> judgments = new ArrayList<>(List.of(judgment));
        AssessmentJudgmentSet set = new AssessmentJudgmentSet(
                JourneyType.A_LEVEL, "a-level-v1", judgments);

        probabilities.put("OTHER", 0.0);
        judgments.clear();

        assertThat(judgment.probabilityDistribution()).containsOnlyKeys("NARROW", "BROAD");
        assertThat(set.judgments()).containsExactly(judgment);
        assertThatThrownBy(() -> set.judgments().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> judgment.probabilityDistribution().put("OTHER", 0.0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsAProbabilityDistributionThatIsNotAProbabilityDistribution() {
        assertThatThrownBy(() -> new DimensionJudgment(
                "readiness",
                "Future readiness",
                OptionalDouble.empty(),
                Optional.empty(),
                Optional.empty(),
                Map.of("LOW", 0.2, "HIGH", 0.2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sum to 1");
    }
}
