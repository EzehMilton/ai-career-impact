package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.decision.alevel.ALevelProfileBand;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasure;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelV2JudgmentProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ALevelSubjectProfileComposerV3Test {

    private final ALevelSubjectProfileComposerV3 composer = new ALevelSubjectProfileComposerV3();

    private ALevelSubjectProfile profile(String fixture) {
        var judgments = new LocalFixtureALevelV2JudgmentProvider(fixture)
                .judge(new ALevelJudgmentInput(List.of("Subject A", "Subject B")));
        return composer.compose(new ALevelDecisionPolicyV2().decide(judgments));
    }

    @Test
    void mapsInternalIdsToPlainCustomerLabelsWithoutRenamingIds() {
        var profile = profile("maths_economics_geography");

        assertThat(profile.optionsKeptOpen()).extracting(ALevelSubjectProfile.Measure::id,
                        ALevelSubjectProfile.Measure::name)
                .containsExactly("OPTIONS_KEPT_OPEN", "Future study options");
        assertThat(profile.capabilityProfile().sides()).extracting(ALevelSubjectProfile.Measure::id,
                        ALevelSubjectProfile.Measure::name)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("ANALYTICAL_AND_QUANTITATIVE", "Maths and problem-solving"),
                        org.assertj.core.groups.Tuple.tuple("COMMUNICATION_INTERPRETATION_AND_CONTEXT",
                                "Writing, ideas and the wider world"));
        assertThat(profile.learningAlongsideAi()).extracting(ALevelSubjectProfile.Measure::id,
                        ALevelSubjectProfile.Measure::name)
                .containsExactly("LEARNING_ALONGSIDE_AI", "How AI can help you learn");
    }

    @Test
    void usesPlainDescriptionsAndKeepsTheRequiredCaveats() {
        var profile = profile("english_history_politics");

        assertThat(profile.optionsKeptOpen().explanation())
                .contains("different study directions", "different skills", "does not check any university's entry requirements")
                .doesNotContain("distinct academic families", "capabilities they build");
        assertThat(profile.capabilityProfile().analyticalAndQuantitative().explanation())
                .contains("mathematical reasoning", "problem-solving", "statistics", "structured thinking");
        assertThat(profile.capabilityProfile().communicationInterpretationAndContext().explanation())
                .contains("writing", "argument", "interpretation", "people, society", "wider context");
        assertThat(profile.learningAlongsideAi().explanation())
                .contains("explanation", "practice", "feedback", "checking", "revision")
                .contains("does not replace your own understanding", "may not be allowed in assessed work");
    }

    @Test
    void usesSentenceCaseBandLabelsAndNaturalSummarySentences() {
        var profile = profile("maths_economics_geography");

        assertThat(ALevelSubjectProfileComposerV3.bandLabel(ALevelProfileMeasure.OPTIONS_KEPT_OPEN,
                ALevelProfileBand.HIGH)).isEqualTo("broad");
        assertThat(ALevelSubjectProfileComposerV3.bandLabel(ALevelProfileMeasure.ANALYTICAL_AND_QUANTITATIVE,
                ALevelProfileBand.HIGH)).isEqualTo("strong emphasis");
        assertThat(ALevelSubjectProfileComposerV3.bandLabel(ALevelProfileMeasure.LEARNING_ALONGSIDE_AI,
                ALevelProfileBand.MODERATE)).isEqualTo("moderate");
        assertThat(profile.summary()).contains("keep a broad range of future study options open",
                "maths, analysis and problem-solving", "writing, interpretation and understanding the wider world",
                "scope to use AI to support your learning")
                .doesNotContain("Analytical & Quantitative", "Communication, Interpretation & Context",
                        "Future study options measure", "How AI can help you learn", " band", "measure");
    }

    @Test
    void everyV3BandCombinationUsesNaturalProseRatherThanInterpolatedHeadings() {
        for (String fixture : LocalFixtureALevelV2JudgmentProvider.supportedFixtureIds()) {
            assertThat(profile(fixture).summary()).as(fixture)
                    .doesNotContain("Future study options measure", "How AI can help you learn", "profile combines",
                            " band", " measure");
        }
    }
}
