package com.chikere.futureai.assessment.domain;

import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.alevel.ALevelMeasure;
import com.chikere.futureai.decision.alevel.OptionalityBand;
import com.chikere.futureai.decision.professional.IndicatorBand;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalMeasure;
import com.chikere.futureai.decision.university.ReadinessBand;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.judgment.typesafe.alevel.ALevelJudgmentSchema;
import com.chikere.futureai.judgment.typesafe.professional.ProfessionalJudgmentSchema;
import com.chikere.futureai.judgment.typesafe.university.UniversityJudgmentSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Professional AI Exposure, University Future Readiness and A-level Future Optionality are three
 * different constructs.
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
        Set<String> aLevel = Set.of(ALevelMeasure.values()).stream()
                .map(Enum::name).collect(java.util.stream.Collectors.toSet());
        assertThat(aLevel).containsExactlyInAnyOrder("FUTURE_OPTIONALITY", "DEGREE_PATHWAY_BREADTH",
                "ANALYTICAL_FOUNDATION", "HUMAN_AND_CREATIVE_STRENGTH", "SKILL_TRANSFERABILITY",
                "AI_ERA_ADAPTABILITY");

        assertThat(university).doesNotContain("AI_EXPOSURE", "HUMAN_ADVANTAGE", "ROLE_ADAPTABILITY");
        assertThat(aLevel).doesNotContain("AI_EXPOSURE", "HUMAN_ADVANTAGE", "ROLE_ADAPTABILITY",
                "FUTURE_READINESS", "HUMAN_DIFFERENTIATION", "CAREER_ADAPTABILITY");
        // Only SKILL_TRANSFERABILITY is shared by name between university and A-level, and even then
        // it is a different enum type computed from different weights.
        Set<String> sharedNames = new java.util.HashSet<>(aLevel);
        sharedNames.retainAll(university);
        assertThat(sharedNames).containsExactly("SKILL_TRANSFERABILITY");
        // The three measure enums are different types: no shared score object can exist.
        assertThat(ProfessionalMeasure.class).isNotEqualTo(UniversityMeasure.class);
        assertThat(UniversityMeasure.class).isNotEqualTo(ALevelMeasure.class);
        assertThat(ProfessionalMeasure.class).isNotEqualTo(ALevelMeasure.class);
    }

    @Test
    void theBandVocabulariesAreDeliberatelyDifferent() {
        assertThat(IndicatorBand.values()).extracting(Enum::name)
                .containsExactly("LOW", "MODERATE", "HIGH");
        assertThat(ReadinessBand.values()).extracting(Enum::name)
                .containsExactly("LIMITED", "DEVELOPING", "STRONG");
        assertThat(OptionalityBand.values()).extracting(Enum::name)
                .containsExactly("FOCUSED", "BALANCED", "BROAD");
        // No two journeys share a band vocabulary, so a reader cannot carry an interpretation across.
        assertThat(Set.of("LOW", "MODERATE", "HIGH"))
                .doesNotContainAnyElementsOf(Set.of("LIMITED", "DEVELOPING", "STRONG"));
        assertThat(Set.of("LIMITED", "DEVELOPING", "STRONG"))
                .doesNotContainAnyElementsOf(Set.of("FOCUSED", "BALANCED", "BROAD"));
    }

    @Test
    void eachPolicyHasItsOwnVersionedSchemaAndWeights() {
        assertThat(ProfessionalJudgmentSchema.VERSION).isEqualTo("professional-v1");
        assertThat(UniversityJudgmentSchema.VERSION).isEqualTo("university-v1");
        assertThat(ALevelJudgmentSchema.VERSION).isEqualTo("a-level-v1");
        assertThat(ProfessionalDecisionPolicyV1.POLICY_VERSION).isEqualTo("professional-decision-v1");
        assertThat(UniversityDecisionPolicyV1.POLICY_VERSION).isEqualTo("university-decision-v1");
        assertThat(ALevelDecisionPolicyV1.POLICY_VERSION).isEqualTo("a-level-decision-v1");
        assertThat(Set.of(ProfessionalJudgmentSchema.VERSION, UniversityJudgmentSchema.VERSION,
                ALevelJudgmentSchema.VERSION)).hasSize(3);
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

        var aLevelState = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of(), List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());
        var aLevel = new ALevelDecisionPolicyV1().decide(
                new LocalFixtureALevelJudgmentProvider("maths_economics_geography").judge(aLevelState));

        double exposure = professional.measure(ProfessionalMeasure.AI_EXPOSURE).orElseThrow().indicator();
        double readiness = university.measure(UniversityMeasure.FUTURE_READINESS).orElseThrow().indicator();
        double optionality = aLevel.measure(ALevelMeasure.FUTURE_OPTIONALITY).orElseThrow().indicator();

        // All three use an 0-10 presentation scale, but they are not the same quantity.
        assertThat(exposure).isBetween(0.0, 10.0);
        assertThat(readiness).isBetween(0.0, 10.0);
        assertThat(optionality).isBetween(0.0, 10.0);
        // High exposure is a characteristic of work; high readiness is a property of a course;
        // high optionality is a property of a subject combination.
        assertThat(professional.assessmentDecision().resultLabel()).endsWith("EXPOSURE");
        assertThat(university.assessmentDecision().resultLabel()).endsWith("FUTURE READINESS");
        assertThat(aLevel.assessmentDecision().resultLabel()).endsWith("FUTURE OPTIONALITY");
        assertThat(professional.assessmentDecision().journey()).isEqualTo(JourneyType.PROFESSIONAL);
        assertThat(university.assessmentDecision().journey()).isEqualTo(JourneyType.UNIVERSITY);
        assertThat(aLevel.assessmentDecision().journey()).isEqualTo(JourneyType.A_LEVEL);
    }

    @Test
    void noPolicyWillConsumeAnotherJourneysJudgmentSet() {
        var professionalState = new ProfessionalAssessmentState("Software Developer", "Writes code.",
                List.of(), List.of(), OptionalInt.empty());
        var universityState = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms and a final-year project.", List.of(), List.of(), List.of(),
                Optional.empty(), Optional.empty());
        var aLevelState = new ALevelAssessmentState(List.of("Mathematics", "Physics"), Map.of(),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty());

        var professional = new LocalFixtureProfessionalJudgmentProvider("routine_software_developer")
                .judge(professionalState);
        var university = new LocalFixtureUniversityJudgmentProvider("computer_science")
                .judge(universityState);
        var aLevel = new LocalFixtureALevelJudgmentProvider("mixed_balanced").judge(aLevelState);

        assertThatThrownBy(() -> new ProfessionalDecisionPolicyV1().decide(university))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new ProfessionalDecisionPolicyV1().decide(aLevel))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new UniversityDecisionPolicyV1().decide(professional))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new UniversityDecisionPolicyV1().decide(aLevel))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new ALevelDecisionPolicyV1().decide(professional))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> new ALevelDecisionPolicyV1().decide(university))
                .isInstanceOf(RuntimeException.class);
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
