package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV2;
import com.chikere.futureai.decision.alevel.ALevelProfileBand;
import com.chikere.futureai.decision.alevel.ALevelProfileMeasure;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelV2JudgmentProvider;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** The deterministic v2 free result: neutral wording, no headline, no winner, no percentages. */
class ALevelSubjectProfileComposerTest {

    private final ALevelSubjectProfileComposer composer = new ALevelSubjectProfileComposer();

    private ALevelSubjectProfile profile(String fixture) {
        var judgments = new LocalFixtureALevelV2JudgmentProvider(fixture)
                .judge(new ALevelJudgmentInput(List.of("Subject A", "Subject B")));
        return composer.compose(new ALevelDecisionPolicyV2().decide(judgments));
    }

    @Test
    void showsThreeCardsWithTheCapabilitySidesTogether() {
        var profile = profile("english_history_politics");

        assertThat(profile.heading()).isEqualTo("Your A-level subject profile");
        assertThat(profile.optionsKeptOpen().name()).isEqualTo("Options Kept Open");
        assertThat(profile.optionsKeptOpen().bandLabel()).isIn("Focused", "Balanced", "Broad");
        assertThat(profile.capabilityProfile().sides()).extracting(ALevelSubjectProfile.Measure::name)
                .containsExactly("Analytical & Quantitative", "Communication, Interpretation & Context");
        assertThat(profile.capabilityProfile().note()).contains("neither side is better", "not a weakness");
        assertThat(profile.learningAlongsideAi().explanation())
                .contains("does not replace developing your own understanding", "not mean AI is allowed");
        // A lighter analytical side for this combination is described, never limited.
        var analytical = profile.capabilityProfile().analyticalAndQuantitative();
        assertThat(analytical.limits()).isEmpty();
        assertThat(profile.capabilityProfile().communicationInterpretationAndContext().limits()).isEmpty();
    }

    @Test
    void neverUsesHeadlineRankingOrDeficitLanguage() {
        for (String fixture : LocalFixtureALevelV2JudgmentProvider.supportedFixtureIds()) {
            var profile = profile(fixture);
            String text = String.join(" ", Stream.concat(Stream.of(profile.summary(), profile.heading(),
                    profile.capabilityProfile().note()), Stream.of(profile.optionsKeptOpen(),
                    profile.capabilityProfile().analyticalAndQuantitative(),
                    profile.capabilityProfile().communicationInterpretationAndContext(), profile.learningAlongsideAi())
                    .flatMap(measure -> Stream.concat(Stream.of(measure.explanation(), measure.bandLabel(),
                            measure.confidenceNote()), Stream.concat(measure.drivers().stream(),
                            measure.limits().stream()))))
                    .toList()).toLowerCase()
                    // The capability note's explicit reassurance is the one permitted use of the word.
                    .replace("not a weakness", "");
            assertThat(text).as(fixture).doesNotContain("weak", "poor", "bad ", "lacking", "worse than",
                    "other students", "alternatives", "future readiness", "employability", "ai readiness",
                    "best subjects", "strongest combination", "overall score", "career impact", "%");
        }
    }

    @Test
    void bandWordsAreTheApprovedOnesForEachMeasure() {
        assertThat(ALevelSubjectProfileComposer.bandLabel(ALevelProfileMeasure.OPTIONS_KEPT_OPEN,
                ALevelProfileBand.LOW)).isEqualTo("Focused");
        assertThat(ALevelSubjectProfileComposer.bandLabel(ALevelProfileMeasure.OPTIONS_KEPT_OPEN,
                ALevelProfileBand.HIGH)).isEqualTo("Broad");
        assertThat(ALevelSubjectProfileComposer.bandLabel(ALevelProfileMeasure.ANALYTICAL_AND_QUANTITATIVE,
                ALevelProfileBand.LOW)).isEqualTo("Lighter emphasis");
        assertThat(ALevelSubjectProfileComposer.bandLabel(ALevelProfileMeasure.COMMUNICATION_INTERPRETATION_AND_CONTEXT,
                ALevelProfileBand.HIGH)).isEqualTo("Strong emphasis");
        assertThat(ALevelSubjectProfileComposer.bandLabel(ALevelProfileMeasure.LEARNING_ALONGSIDE_AI,
                ALevelProfileBand.MODERATE)).isEqualTo("Moderate");
        assertThat(ALevelSubjectProfileComposer.score(6.996, ALevelProfileBand.MODERATE)).isEqualTo("6.99");
    }

    @Test
    void lowerConfidenceIsANoteNeverAPercentage() {
        var profile = profile("maths_economics_geography");

        assertThat(profile.optionsKeptOpen().confidenceNote()).isEmpty();
        assertThat(ALevelSubjectProfileComposer.LOWER_CONFIDENCE_NOTE).doesNotContainPattern("\\d");
    }
}
