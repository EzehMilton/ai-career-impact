package com.chikere.futureai.narrative.university.fixture;

import com.chikere.futureai.assessment.domain.AssessmentConfidence;
import com.chikere.futureai.assessment.domain.AssessmentStatus;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.university.application.NarrativeSource;
import com.chikere.futureai.narrative.university.application.UniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeContext;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeContextFactory;
import com.chikere.futureai.narrative.university.application.UniversityNarrativeValidator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LocalFixtureUniversityNarrativeGeneratorTest {

    private final LocalFixtureUniversityNarrativeGenerator generator =
            new LocalFixtureUniversityNarrativeGenerator();

    @Test
    void producesDeterministicValidatedTextFromSavedBandsForEveryFixture() {
        for (String fixtureId : LocalFixtureUniversityJudgmentProvider.supportedFixtureIds()) {
            UniversityNarrativeContext context = context(fixtureId);

            UniversityNarrative first = generator.generate(context);
            UniversityNarrative second = generator.generate(context);

            assertThat(second).as("%s determinism", fixtureId).isEqualTo(first);
            assertThat(new UniversityNarrativeValidator().validate(first)).isSameAs(first);
            assertThat(first.practicalActions()).hasSizeBetween(3, 5);
            assertThat(generator.source()).isEqualTo(NarrativeSource.LOCAL_FIXTURE);
            assertThat(generator.providerIdentifier()).isEqualTo("local-fixture");
        }
    }

    @Test
    void reflectsSavedBandsAndCareerAreasWithoutInventingFacts() {
        UniversityNarrative narrative = generator.generate(context("computer_science"));

        assertThat(narrative.summary()).contains("Future Readiness is strong");
        assertThat(narrative.careerAdaptabilityExplanation()).contains("Software engineering");
        assertThat(narrative.cautionOrLimitations()).contains("development fixture")
                .contains("not a guarantee about employment");
        assertThat(narrative.headline()).contains("Computer Science");
    }

    @Test
    void insufficientInformationProducesNoInventedMeasureLanguage() {
        UniversityNarrativeContext insufficient = new UniversityNarrativeContext("Computer Science",
                "Too short.", List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(),
                AssessmentStatus.MORE_INFORMATION_REQUIRED, AssessmentConfidence.LOW,
                List.of(), List.of(), List.of(), AssessmentSource.LOCAL_FIXTURE,
                "university-v1", "university-decision-v1");

        UniversityNarrative narrative = generator.generate(insufficient);

        assertThat(narrative.futureReadinessExplanation()).contains("cannot yet be explained");
        assertThat(new UniversityNarrativeValidator().validate(narrative)).isSameAs(narrative);
    }

    private UniversityNarrativeContext context(String fixtureId) {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of("Software engineering"),
                Optional.empty(), Optional.of("United Kingdom"));
        var judgments = new LocalFixtureUniversityJudgmentProvider(fixtureId).judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        var assessment = new StoredUniversityAssessment(UUID.randomUUID(),
                Instant.parse("2026-10-01T12:00:00Z"), state, judgments,
                new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        return new UniversityNarrativeContextFactory().create(assessment);
    }
}
