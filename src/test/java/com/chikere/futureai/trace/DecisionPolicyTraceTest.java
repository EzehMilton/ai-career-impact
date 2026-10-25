package com.chikere.futureai.trace;

import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionFactor;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.decision.alevel.ALevelDecisionResult;
import com.chikere.futureai.decision.professional.ProfessionalDecisionFactor;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.decision.professional.ProfessionalDecisionResult;
import com.chikere.futureai.decision.university.UniversityDecisionFactor;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityDecisionResult;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.stream.DoubleStream;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The score diagnostics come from the policy's own calculation: tracing changes no result, and every
 * logged contribution reproduces the stored score from the decision model's actual judgments.
 */
class DecisionPolicyTraceTest {

    private static final ProfessionalAssessmentState PROFESSIONAL = new ProfessionalAssessmentState(
            "Software Architect", "Designs systems", List.of("Design"), List.of("Architecture"), OptionalInt.of(10));
    private static final UniversityAssessmentState UNIVERSITY = new UniversityAssessmentState("Law",
            "Contract, tort and public law", List.of("Contract"), List.of("Argument"), List.of(),
            Optional.empty(), Optional.empty());
    private static final ALevelAssessmentState A_LEVEL = new ALevelAssessmentState(
            List.of("Mathematics", "Economics", "Geography"), Map.of(), List.of(), List.of(), List.of(),
            Optional.empty(), Optional.empty());

    private final TraceLogCapture log = TraceLogCapture.start();

    @AfterEach
    void detach() {
        log.close();
    }

    @Test
    void professionalTracingChangesNoResultAndEveryCalculationReproducesTheScore() {
        for (String fixture : LocalFixtureProfessionalJudgmentProvider.supportedFixtureIds()) {
            AssessmentJudgmentSet judgments = new LocalFixtureProfessionalJudgmentProvider(fixture).judge(PROFESSIONAL);
            log.clear();
            ProfessionalDecisionResult traced = new ProfessionalDecisionPolicyV1(AssessmentTrace.enabled()).decide(judgments);

            assertThat(traced).as(fixture).isEqualTo(new ProfessionalDecisionPolicyV1().decide(judgments));
            Map<String, Double> scores = new HashMap<>();
            Map<String, String> bands = new HashMap<>();
            traced.measures().forEach((measure, result) -> {
                scores.put(measure.name(), result.indicator());
                bands.put(measure.name(), result.band().name());
            });
            assertCalculationsReproduce(fixture, judgments, scores, bands);
            JsonNode selection = log.only(TraceStage.FACTOR_SELECTION).get("payload");
            assertThat(texts(selection.get("whatIncreasesAiExposure"))).as(fixture).isEqualTo(
                    traced.topExposureDrivers().stream().map(ProfessionalDecisionFactor::dimensionId).toList());
            assertThat(texts(selection.get("whereHumanSkillsRemainImportant"))).as(fixture).isEqualTo(
                    traced.topProtectiveFactors().stream().map(ProfessionalDecisionFactor::dimensionId).toList());
            assertThat(log.only(TraceStage.INFORMATION_SUFFICIENCY).at("/payload/scoresProduced").asBoolean()).isTrue();
        }
    }

    @Test
    void universityTracingChangesNoResultAndEveryCalculationReproducesTheScore() {
        for (String fixture : LocalFixtureUniversityJudgmentProvider.supportedFixtureIds()) {
            AssessmentJudgmentSet judgments = new LocalFixtureUniversityJudgmentProvider(fixture).judge(UNIVERSITY);
            log.clear();
            UniversityDecisionResult traced = new UniversityDecisionPolicyV1(AssessmentTrace.enabled()).decide(judgments);

            assertThat(traced).as(fixture).isEqualTo(new UniversityDecisionPolicyV1().decide(judgments));
            Map<String, Double> scores = new HashMap<>();
            Map<String, String> bands = new HashMap<>();
            traced.measures().forEach((measure, result) -> {
                scores.put(measure.name(), result.indicator());
                bands.put(measure.name(), result.band().name());
            });
            assertCalculationsReproduce(fixture, judgments, scores, bands);
            JsonNode selection = log.only(TraceStage.FACTOR_SELECTION).get("payload");
            assertThat(texts(selection.get("strengths"))).as(fixture).isEqualTo(
                    traced.topStrengths().stream().map(UniversityDecisionFactor::dimensionId).toList());
            assertThat(texts(selection.get("developmentOpportunities"))).as(fixture).isEqualTo(
                    traced.topDevelopmentOpportunities().stream().map(UniversityDecisionFactor::dimensionId).toList());
        }
    }

    @Test
    void aLevelTracingChangesNoResultAndEveryCalculationReproducesTheScore() {
        for (String fixture : LocalFixtureALevelJudgmentProvider.supportedFixtureIds()) {
            AssessmentJudgmentSet judgments = new LocalFixtureALevelJudgmentProvider(fixture).judge(A_LEVEL);
            log.clear();
            ALevelDecisionResult traced = new ALevelDecisionPolicyV1(AssessmentTrace.enabled()).decide(judgments);

            assertThat(traced).as(fixture).isEqualTo(new ALevelDecisionPolicyV1().decide(judgments));
            Map<String, Double> scores = new HashMap<>();
            Map<String, String> bands = new HashMap<>();
            traced.measures().forEach((measure, result) -> {
                scores.put(measure.name(), result.indicator());
                bands.put(measure.name(), result.band().name());
            });
            assertCalculationsReproduce(fixture, judgments, scores, bands);
            JsonNode selection = log.only(TraceStage.FACTOR_SELECTION).get("payload");
            assertThat(texts(selection.get("strengths"))).as(fixture).isEqualTo(
                    traced.topStrengths().stream().map(ALevelDecisionFactor::dimensionId).toList());
            assertThat(texts(selection.get("developmentOpportunities"))).as(fixture).isEqualTo(
                    traced.topDevelopmentOpportunities().stream().map(ALevelDecisionFactor::dimensionId).toList());
        }
    }

    @Test
    void insufficientInformationIsTracedAsSuppressingEveryScore() {
        AssessmentJudgmentSet fixture = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect")
                .judge(PROFESSIONAL);
        List<DimensionJudgment> judgments = new ArrayList<>(fixture.judgments());
        DimensionJudgment information = judgments.getFirst();
        judgments.set(0, new DimensionJudgment(information.dimensionId(), information.name(), OptionalDouble.of(0.4),
                Optional.empty(), Optional.empty(), Map.of()));

        new ProfessionalDecisionPolicyV1(AssessmentTrace.enabled())
                .decide(new AssessmentJudgmentSet(fixture.journey(), fixture.schemaVersion(), judgments));

        JsonNode gate = log.only(TraceStage.INFORMATION_SUFFICIENCY).get("payload");
        assertThat(gate.get("informationSufficiency").asDouble()).isEqualTo(0.4);
        assertThat(gate.get("moreInformationRequiredBelow").asDouble()).isEqualTo(0.6);
        assertThat(gate.get("status").asText()).isEqualTo("MORE_INFORMATION_REQUIRED");
        assertThat(gate.get("scoresProduced").asBoolean()).isFalse();
        assertThat(log.events(TraceStage.SCORE_CALCULATION)).isEmpty();
    }

    private void assertCalculationsReproduce(String fixture, AssessmentJudgmentSet judgments,
            Map<String, Double> scores, Map<String, String> bands) {
        Map<String, Double> raw = new HashMap<>();
        Map<String, Double> confidence = new HashMap<>();
        judgments.judgments().forEach(judgment -> {
            raw.put(judgment.dimensionId(), judgment.numericValue().orElseThrow());
            judgment.confidenceValue().ifPresent(value -> confidence.put(judgment.dimensionId(), value));
        });
        List<JsonNode> calculations = log.events(TraceStage.SCORE_CALCULATION);
        assertThat(calculations).as(fixture).hasSize(scores.size());
        for (JsonNode event : calculations) {
            JsonNode payload = event.get("payload");
            String measure = payload.get("measure").asText();
            List<Double> contributions = new ArrayList<>();
            for (JsonNode input : payload.get("inputs")) {
                String dimension = input.get("dimensionId").asText();
                double rawScore = input.get("rawScore").asDouble();
                double weight = input.get("weight").asDouble();
                double transformed = input.get("transformedValue").asDouble();
                assertThat(rawScore).as("%s %s %s raw", fixture, measure, dimension).isEqualTo(raw.get(dimension));
                assertThat(input.get("judgmentConfidence").asDouble()).isEqualTo(confidence.get(dimension));
                double expected = input.get("transformation").asText().startsWith("inverse")
                        ? 1.0 - rawScore / 4.0 : rawScore / 4.0;
                assertThat(transformed).as("%s %s %s transformed", fixture, measure, dimension).isEqualTo(expected);
                assertThat(input.get("contribution").asDouble()).isEqualTo(weight * transformed);
                contributions.add(input.get("contribution").asDouble());
            }
            double total = payload.get("weightedTotal").asDouble();
            assertThat(total).as("%s %s total", fixture, measure)
                    .isEqualTo(DoubleStream.of(contributions.stream().mapToDouble(Double::doubleValue).toArray()).sum());
            assertThat(payload.get("unroundedScore").asDouble()).as("%s %s score", fixture, measure)
                    .isEqualTo(scores.get(measure))
                    .isEqualTo(Math.max(0.0, Math.min(10.0, total * 10.0)));
            assertThat(payload.get("band").asText()).as("%s %s band", fixture, measure).isEqualTo(bands.get(measure));
        }
    }

    private static List<String> texts(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false).map(JsonNode::asText).toList();
    }
}
