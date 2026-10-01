package com.chikere.futureai.narrative.alevel.fixture;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrative;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeContext;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeContextFactory;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeValidator;
import com.chikere.futureai.narrative.alevel.application.NarrativeSource;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFixtureALevelNarrativeGeneratorTest {

    private final LocalFixtureALevelNarrativeGenerator generator =
            new LocalFixtureALevelNarrativeGenerator();

    @Test
    void producesDeterministicValidatedTextFromSavedBandsForEveryFixture() {
        for (String fixtureId : LocalFixtureALevelJudgmentProvider.supportedFixtureIds()) {
            ALevelNarrativeContext context = context(fixtureId);

            ALevelNarrative first = generator.generate(context);
            ALevelNarrative second = generator.generate(context);

            assertThat(second).as("%s determinism", fixtureId).isEqualTo(first);
            assertThat(new ALevelNarrativeValidator().validate(first)).isSameAs(first);
            assertThat(first.practicalActions()).hasSizeBetween(3, 5);
            assertThat(generator.source()).isEqualTo(NarrativeSource.LOCAL_FIXTURE);
            assertThat(generator.providerIdentifier()).isEqualTo("local-fixture");
        }
    }

    @Test
    void reflectsSavedBandsAndDegreeAreasWithoutInventingPrerequisites() {
        ALevelNarrative narrative = generator.generate(context("maths_economics_geography"));

        assertThat(narrative.summary()).contains("future optionality is broad");
        assertThat(narrative.developmentOpportunitiesExplanation()).contains("Economics");
        assertThat(narrative.degreePathwayExplanation()).contains("check its own published requirements");
        assertThat(narrative.cautionOrLimitations()).contains("development fixture")
                .contains("does not reflect any university's admissions policy");
        assertThat(narrative.headline()).contains("Mathematics, Economics, Geography");
    }

    @Test
    void neverTellsTheStudentToChangeOrDropASubject() {
        for (String fixtureId : LocalFixtureALevelJudgmentProvider.supportedFixtureIds()) {
            ALevelNarrative narrative = generator.generate(context(fixtureId));
            String everything = (narrative.toString() + String.join(" ", narrative.practicalActions()))
                    .toLowerCase(java.util.Locale.ROOT);

            assertThat(everything).as("%s wording", fixtureId)
                    .doesNotContain("drop ", "swap ", "change your subject", "wrong subject",
                            "bad subject", "you should take", "instead of");
        }
    }

    @Test
    void insufficientInformationProducesNoInventedMeasureLanguage() {
        ALevelNarrativeContext insufficient = new ALevelNarrativeContext(
                List.of("Mathematics", "Physics"), Map.of(), List.of(), List.of(), List.of(),
                Optional.empty(), Optional.empty(), AssessmentStatus.MORE_INFORMATION_REQUIRED,
                AssessmentConfidence.LOW, List.of(), List.of(), List.of(),
                AssessmentSource.LOCAL_FIXTURE, "a-level-v1", "a-level-decision-v1");

        ALevelNarrative narrative = generator.generate(insufficient);

        assertThat(narrative.futureOptionalityExplanation()).contains("cannot yet be explained");
        assertThat(new ALevelNarrativeValidator().validate(narrative)).isSameAs(narrative);
    }

    private ALevelNarrativeContext context(String fixtureId) {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of("Mathematics", "A"), List.of("Problem solving"), List.of("Economics"),
                List.of(), Optional.of("United Kingdom"), Optional.empty());
        var judgments = new LocalFixtureALevelJudgmentProvider(fixtureId).judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        var assessment = new StoredALevelAssessment(UUID.randomUUID(),
                Instant.parse("2026-10-01T12:00:00Z"), state, judgments,
                new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        return new ALevelNarrativeContextFactory().create(assessment);
    }
}
