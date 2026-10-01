package com.chikere.futureai.judgment.fixture.university;

import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LocalFixtureUniversityJudgmentProviderTest {

    @Test
    void supportsOnlyTheExactConfiguredFixtureIds() {
        assertThat(LocalFixtureUniversityJudgmentProvider.supportedFixtureIds()).containsExactly(
                "computer_science", "economics", "graphic_design", "history",
                "mechanical_engineering", "nursing");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LocalFixtureUniversityJudgmentProvider("computer science"))
                .withMessageContaining("app.university.fixture-id");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LocalFixtureUniversityJudgmentProvider("comp"))
                .withMessageContaining("Supported fixture IDs");
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalFixtureUniversityJudgmentProvider(""));
    }

    @Test
    void producesDeterministicUniversitySchemaJudgmentsWithFixtureProvenance() {
        var provider = new LocalFixtureUniversityJudgmentProvider("history");

        AssessmentJudgmentSet first = provider.judge(state("History"));
        AssessmentJudgmentSet second = provider.judge(state("A completely different description"));

        assertThat(provider.source()).isEqualTo(AssessmentSource.LOCAL_FIXTURE);
        assertThat(first.journey()).isEqualTo(JourneyType.UNIVERSITY);
        assertThat(first.schemaVersion()).isEqualTo("university-v1");
        assertThat(first.judgments()).hasSize(10);
        // The fixture is selected by configuration, not by the submitted text.
        assertThat(second).isEqualTo(first);
        assertThat(first.judgments().getFirst().dimensionId()).isEqualTo("information_sufficient");
        assertThat(first.judgments().getFirst().confidenceValue()).isEmpty();
        assertThat(first.judgments().stream().skip(1))
                .allSatisfy(judgment -> assertThat(judgment.confidenceValue()).hasValue(0.85));
    }

    @Test
    void contrastingFixturesDifferOnTheirDefiningDimensions() {
        var computing = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state("CS"));
        var nursing = new LocalFixtureUniversityJudgmentProvider("nursing").judge(state("Nursing"));

        assertThat(score(computing, "technical_digital_depth")).isEqualTo(4.0);
        assertThat(score(nursing, "technical_digital_depth")).isEqualTo(1.6);
        assertThat(score(computing, "human_relationship_dependency")).isEqualTo(1.4);
        assertThat(score(nursing, "human_relationship_dependency")).isEqualTo(4.0);
        assertThat(score(nursing, "practical_real_world_application")).isEqualTo(4.0);
    }

    private double score(AssessmentJudgmentSet judgments, String dimensionId) {
        return judgments.judgments().stream()
                .filter(value -> value.dimensionId().equals(dimensionId))
                .map(DimensionJudgment::numericValue)
                .findFirst().orElseThrow().orElseThrow();
    }

    private UniversityAssessmentState state(String description) {
        return new UniversityAssessmentState("A course", description + " with enough detail to assess.",
                List.of("A module"), List.of("A skill"), List.of(), Optional.empty(), Optional.empty());
    }
}
