package com.chikere.futureai.assessment.domain;

import com.chikere.futureai.decision.professional.IndicatorBand;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.decision.university.ReadinessBand;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import com.chikere.futureai.judgment.typesafe.university.UniversityJudgmentSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Professional AI Exposure and University Future Readiness are different constructs.
 *
 * <p>These journeys share delivery and operational infrastructure, not score semantics. This test
 * pins the separation so a future refactor cannot quietly merge them into one universal risk score.
 */
class CrossJourneySeparationTest {

    @Test
    void theTwoJourneysHaveDistinctMeasureSetsWithNoSharedName() {
        Set<String> professional = Set.of(ProfessionalMeasure.values()).stream()
                .map(Enum::name).collect(java.util.stream.Collectors.toSet());
        Set<String> university = Set.of(UniversityMeasure.values()).stream()
                .map(Enum::name).collect(java.util.stream.Collectors.toSet());

        assertThat(professional).containsExactlyInAnyOrder("AI_EXPOSURE", "HUMAN_ADVANTAGE",
                "AI_COMPLEMENTARITY", "ROLE_ADAPTABILITY");
        assertThat(university).containsExactlyInAnyOrder("FUTURE_READINESS", "AI_COMPLEMENTARITY",
                "HUMAN_DIFFERENTIATION", "SKILL_TRANSFERABILITY", "CAREER_ADAPTABILITY");
        assertThat(university).doesNotContain("AI_EXPOSURE", "HUMAN_ADVANTAGE", "ROLE_ADAPTABILITY");
        // The two measure enums are different types: no shared score object can exist.
        assertThat(ProfessionalMeasure.class).isNotEqualTo(UniversityMeasure.class);
    }

    @Test
    void theBandVocabulariesAreDeliberatelyDifferent() {
        assertThat(IndicatorBand.values()).extracting(Enum::name)
                .containsExactly("LOW", "MODERATE", "HIGH");
        assertThat(ReadinessBand.values()).extracting(Enum::name)
                .containsExactly("LIMITED", "DEVELOPING", "STRONG");
    }

    @Test
    void eachPolicyHasItsOwnVersionedSchemaAndWeights() {
        assertThat(ProfessionalJudgmentSchema.VERSION).isEqualTo("professional-v1");
        assertThat(UniversityJudgmentSchema.VERSION).isEqualTo("university-v1");
        assertThat(ProfessionalDecisionPolicyV1.POLICY_VERSION).isEqualTo("professional-decision-v1");
        assertThat(UniversityDecisionPolicyV1.POLICY_VERSION).isEqualTo("university-decision-v1");
        assertThat(ProfessionalJudgmentSchema.VERSION).isNotEqualTo(UniversityJudgmentSchema.VERSION);
    }

    @Test
    void anIdenticalNumberMeansDifferentThingsInEachJourney() {
        var professionalState = new ProfessionalAssessmentState("Software Developer",
                "Writes and maintains application code to a defined specification.",
                List.of("Write code"), List.of("Programming"), OptionalInt.of(3));
        var universityState = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of(),
                Optional.empty(), Optional.empty());

        var professional = new ProfessionalDecisionPolicyV1().decide(
                new LocalFixtureProfessionalJudgmentProvider("routine_software_developer")
                        .judge(professionalState));
        var university = new UniversityDecisionPolicyV1().decide(
                new LocalFixtureUniversityJudgmentProvider("computer_science").judge(universityState));

        double exposure = professional.measure(ProfessionalMeasure.AI_EXPOSURE).orElseThrow().indicator();
        double readiness = university.measure(UniversityMeasure.FUTURE_READINESS).orElseThrow().indicator();

        // Both are on an 0-10 presentation scale, but they are not the same quantity.
        assertThat(exposure).isBetween(0.0, 10.0);
        assertThat(readiness).isBetween(0.0, 10.0);
        // High exposure is a characteristic of work; high readiness is a property of a course.
        assertThat(professional.assessmentDecision().resultLabel()).endsWith("EXPOSURE");
        assertThat(university.assessmentDecision().resultLabel()).endsWith("FUTURE READINESS");
        assertThat(professional.assessmentDecision().journey()).isEqualTo(JourneyType.PROFESSIONAL);
        assertThat(university.assessmentDecision().journey()).isEqualTo(JourneyType.UNIVERSITY);
    }

    @Test
    void theJourneysShareOnlyGenuinelyNeutralDomainTypes() {
        assertThat(JourneyType.values())
                .containsExactly(JourneyType.PROFESSIONAL, JourneyType.UNIVERSITY, JourneyType.A_LEVEL);
        assertThat(AssessmentStatus.values()).containsExactly(AssessmentStatus.READY,
                AssessmentStatus.READY_WITH_CAUTION, AssessmentStatus.MORE_INFORMATION_REQUIRED);
        assertThat(AssessmentConfidence.values()).containsExactly(AssessmentConfidence.HIGH,
                AssessmentConfidence.MODERATE, AssessmentConfidence.LOW);
    }
}
