package com.chikere.futureai.judgment.fixture.alevel;

import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.JourneyType;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class LocalFixtureALevelJudgmentProviderTest {

    @Test
    void supportsOnlyTheExactConfiguredFixtureIds() {
        assertThat(LocalFixtureALevelJudgmentProvider.supportedFixtureIds()).containsExactly(
                "art_design_media", "biology_chemistry_mathematics", "english_history_politics",
                "mathematics_physics_computer_science", "maths_economics_geography", "mixed_balanced");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LocalFixtureALevelJudgmentProvider("maths economics geography"))
                .withMessageContaining("app.a-level.fixture-id");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new LocalFixtureALevelJudgmentProvider("maths"))
                .withMessageContaining("Supported fixture IDs");
        assertThatIllegalArgumentException().isThrownBy(() -> new LocalFixtureALevelJudgmentProvider(""));
    }

    @Test
    void producesDeterministicALevelSchemaJudgmentsWithFixtureProvenance() {
        var provider = new LocalFixtureALevelJudgmentProvider("english_history_politics");

        AssessmentJudgmentSet first = provider.judge(state(List.of("English Literature", "History")));
        AssessmentJudgmentSet second = provider.judge(state(List.of("Mathematics", "Physics")));

        assertThat(provider.source()).isEqualTo(AssessmentSource.LOCAL_FIXTURE);
        assertThat(first.journey()).isEqualTo(JourneyType.A_LEVEL);
        assertThat(first.schemaVersion()).isEqualTo("a-level-v1");
        assertThat(first.judgments()).hasSize(11);
        // The fixture is selected by configuration, not by the subjects submitted. This is what
        // keeps the fixture from becoming a hidden subject-ranking table.
        assertThat(second).isEqualTo(first);
        assertThat(first.judgments().getFirst().dimensionId()).isEqualTo("information_sufficient");
        assertThat(first.judgments().getFirst().confidenceValue()).isEmpty();
        assertThat(first.judgments().stream().skip(1))
                .allSatisfy(judgment -> assertThat(judgment.confidenceValue()).hasValue(0.85));
    }

    @Test
    void contrastingFixturesDifferOnTheirDefiningDimensions() {
        var stem = new LocalFixtureALevelJudgmentProvider("mathematics_physics_computer_science")
                .judge(state(List.of("Mathematics", "Physics")));
        var humanities = new LocalFixtureALevelJudgmentProvider("english_history_politics")
                .judge(state(List.of("English Literature", "History")));
        var creative = new LocalFixtureALevelJudgmentProvider("art_design_media")
                .judge(state(List.of("Art and Design", "Music")));

        assertThat(score(stem, "quantitative_reasoning")).isEqualTo(4.0);
        assertThat(score(humanities, "quantitative_reasoning")).isEqualTo(0.8);
        assertThat(score(stem, "written_verbal_communication")).isEqualTo(1.6);
        assertThat(score(humanities, "written_verbal_communication")).isEqualTo(4.0);
        assertThat(score(creative, "original_judgment_creativity")).isEqualTo(4.0);
        assertThat(score(stem, "technical_digital_foundation")).isEqualTo(4.0);
        assertThat(score(humanities, "technical_digital_foundation")).isEqualTo(0.6);
    }

    private double score(AssessmentJudgmentSet judgments, String dimensionId) {
        return judgments.judgments().stream()
                .filter(value -> value.dimensionId().equals(dimensionId))
                .map(DimensionJudgment::numericValue)
                .findFirst().orElseThrow().orElseThrow();
    }

    private ALevelAssessmentState state(List<String> subjects) {
        return new ALevelAssessmentState(subjects, Map.of(), List.of(), List.of(), List.of(),
                Optional.empty(), Optional.empty());
    }
}
