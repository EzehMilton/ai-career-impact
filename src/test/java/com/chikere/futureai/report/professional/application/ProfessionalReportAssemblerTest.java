package com.chikere.futureai.report.professional.application;

import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.*;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1;
import com.chikere.futureai.evidence.professional.application.*;
import com.chikere.futureai.judgment.domain.AssessmentJudgmentSet;
import com.chikere.futureai.judgment.domain.DimensionJudgment;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.*;
import com.chikere.futureai.occupation.professional.application.*;
import com.chikere.futureai.report.professional.web.ProfessionalReportViewMapper;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

public class ProfessionalReportAssemblerTest {
    private static final UUID ID = UUID.fromString("b0285ff5-7dc4-4808-aef4-1b975de59151");
    private final ProfessionalReportAssembler assembler = new ProfessionalReportAssembler();

    @Test
    void sameSavedPackageProducesDeterministicTieredReportsWithIdenticalScores() {
        var source = source(true, true, true);
        var free = assembler.assemble(source, ProfessionalReportTier.FREE);
        var paid = assembler.assemble(source, ProfessionalReportTier.PAID);

        assertThat(assembler.assemble(source, ProfessionalReportTier.FREE)).isEqualTo(free);
        assertThat(assembler.assemble(source, ProfessionalReportTier.PAID)).isEqualTo(paid);
        assertThat(free.measures()).extracting(ProfessionalReport.MeasureSummary::indicator)
                .containsExactlyElementsOf(paid.measures().stream().map(ProfessionalReport.MeasureSummary::indicator).toList());
        assertThat(free.measures()).hasSize(4);
        assertThat(free.exposureDrivers()).isNotEmpty();
        assertThat(free.protectiveFactors()).isNotEmpty();
        assertThat(free.assessmentSummary().interpretation()).isNotBlank();
        assertThat(free.narrative().summary()).isPresent();
        assertThat(free.narrative().exposureExplanation()).isEmpty();
        assertThat(free.practicalActions()).isEmpty();
        assertThat(free.skillPriorities()).isEmpty();
        assertThat(free.workFocus().areas()).isEmpty();
        assertThat(free.evidence().items()).isEmpty();
        assertThat(free.evidence().message()).contains("where available");
        assertThat(free.occupationClassification()).isEmpty();

        assertThat(paid.narrative().exposureExplanation()).contains("Detailed exposure");
        assertThat(paid.practicalActions()).containsExactly("Action one", "Action two", "Action three");
        assertThat(paid.skillPriorities()).isNotEmpty();
        assertThat(paid.workFocus().areas()).anyMatch(value ->
                value.category() == WorkFocusCategory.USER_REVIEW_REQUIRED
                        && value.relatedResponsibilities().contains("Design systems"));
        assertThat(paid.evidence().items()).singleElement().satisfies(item ->
                assertThat(item.sourceName()).isEqualTo("Local fixture evidence"));
        assertThat(new ProfessionalReportViewMapper().toView(paid).evidence().items())
                .singleElement().satisfies(item ->
                        assertThat(item.quality()).isEqualTo("Development fixture"));
        assertThat(paid.occupationClassification()).get().extracting(
                ProfessionalReport.OccupationClassification::classificationCode).isEqualTo("2133");
        assertThat(paid.methodology().reportVersion()).isEqualTo("professional-report-v1");
    }

    @Test
    void optionalFailuresNeverPreventEitherReportTier() {
        var source = source(false, false, false);
        var free = assembler.assemble(source, ProfessionalReportTier.FREE);
        var paid = assembler.assemble(source, ProfessionalReportTier.PAID);

        assertThat(free.measures()).hasSize(4);
        assertThat(paid.measures()).hasSize(4);
        assertThat(free.narrative().available()).isFalse();
        assertThat(paid.narrative().available()).isFalse();
        assertThat(paid.evidence().status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(paid.evidence().message()).isEqualTo("External career context is currently unavailable.");
        assertThat(paid.occupationClassification()).isEmpty();
    }

    @Test
    void workFocusDoesNotPretendResponsibilitiesWereIndividuallyScored() {
        var paid = assembler.assemble(source(true, true, true), ProfessionalReportTier.PAID);
        assertThat(paid.workFocus().limitation()).contains("does not assign task-level scores");
        assertThat(paid.workFocus().areas()).filteredOn(value -> !value.relatedResponsibilities().isEmpty())
                .allMatch(value -> value.category() == WorkFocusCategory.USER_REVIEW_REQUIRED);
    }

    @Test
    void fixedMeasureDescriptionsUsePlainNeutralWording() {
        var free = assembler.assemble(source(true, true, true), ProfessionalReportTier.FREE);

        assertThat(free.measures()).extracting(ProfessionalReport.MeasureSummary::conciseExplanation).containsExactly(
                "How much the work overlaps with characteristics AI can perform through digital systems.",
                "How strongly the work relies on human trust, context, physical presence, judgement and accountability.",
                "How much scope there is for AI assistance while meaningful human contribution remains.",
                "How much the structure of the role can accommodate technological change while retaining "
                        + "meaningful human contribution.");
    }

    @Test
    void routineAndEstablishedApproachFactorsUseNeutralWordingWithoutChangingSelection() {
        // Everything neutral except high task repeatability and low original judgement, so exactly those two
        // dimensions drive exposure.
        Map<String, Double> scores = new HashMap<>();
        for (String dimension : List.of("digital_executability", "human_trust_dependency",
                "physical_world_dependency", "contextual_complexity", "accountability_responsibility",
                "ai_complementarity")) {
            scores.put(dimension, 2.0);
        }
        scores.put("task_repeatability", 4.0);
        scores.put("original_judgment", 0.0);
        var source = source(true, true, true, scores);

        var free = assembler.assemble(source, ProfessionalReportTier.FREE);

        assertThat(source.assessment().result().decision().topExposureDrivers())
                .extracting(factor -> factor.dimensionId()).containsExactly("task_repeatability", "original_judgment");
        assertThat(free.exposureDrivers()).containsExactly(
                "Some of the work follows set routines or established processes.",
                "Some of the work follows established approaches rather than requiring completely new approaches.");
    }

    @Test
    void confidenceAndCautionRemainInTheReportEvenThoughPagesNoLongerShowThem() {
        var paid = assembler.assemble(source(true, true, true), ProfessionalReportTier.PAID);

        assertThat(paid.assessmentSummary().confidence()).isNotNull();
        assertThat(paid.measures()).allSatisfy(measure -> assertThat(measure.confidence()).isCloseTo(0.85, org.assertj.core.api.Assertions.within(1e-9)));
        assertThat(paid.narrative().cautionOrLimitations()).contains("Caution");
    }

    public static ProfessionalReportSource source(boolean narrativeAvailable, boolean evidenceAvailable,
            boolean classificationAvailable) {
        return source(narrativeAvailable, evidenceAvailable, classificationAvailable, Map.of());
    }

    /** The architect fixture with some judgment values replaced, keeping each judgment's confidence. */
    public static ProfessionalReportSource source(boolean narrativeAvailable, boolean evidenceAvailable,
            boolean classificationAvailable, Map<String, Double> values) {
        Instant time = Instant.parse("2026-09-30T12:00:00Z");
        var state = new ProfessionalAssessmentState("Software Architect", "Designs complex systems.",
                List.of("Design systems", "Resolve trade-offs"), List.of("Architecture"), OptionalInt.of(14));
        var fixture = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var judgments = new AssessmentJudgmentSet(fixture.journey(), fixture.schemaVersion(),
                fixture.judgments().stream().map(judgment -> values.containsKey(judgment.dimensionId())
                        ? new DimensionJudgment(judgment.dimensionId(), judgment.name(),
                                OptionalDouble.of(values.get(judgment.dimensionId())), judgment.categoricalValue(),
                                judgment.confidence(), judgment.confidenceValue(), judgment.probabilityDistribution())
                        : judgment).toList());
        var decision = new ProfessionalDecisionPolicyV1().decide(judgments);
        var assessment = new StoredProfessionalAssessment(ID, time, state, judgments,
                new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        Optional<StoredProfessionalNarrative> narrative = narrativeAvailable
                ? Optional.of(new StoredProfessionalNarrative(ID, NarrativeStatus.AVAILABLE,
                    Optional.of(new ProfessionalNarrative("Headline", "Summary", "What this means",
                            "Detailed exposure", "Detailed human advantage", "Detailed complementarity",
                            "Detailed adaptability", List.of("Action one", "Action two", "Action three"), "Caution")),
                    NarrativeSource.LOCAL_FIXTURE, "fixture", Optional.empty(), "professional-narrative-v1",
                    time, Optional.of(time))) : Optional.of(new StoredProfessionalNarrative(ID,
                    NarrativeStatus.FAILED, Optional.empty(), NarrativeSource.LOCAL_FIXTURE, "fixture",
                    Optional.empty(), "professional-narrative-v1", time, Optional.empty()));
        Optional<ProfessionalEvidenceBundle> evidence = evidenceAvailable ? Optional.of(evidence(time))
                : Optional.of(new ProfessionalEvidenceBundle(ID, "professional-evidence-v1",
                    EvidenceSource.LOCAL_FIXTURE, "fixture", EvidenceStatus.FAILED, time, List.of()));
        Optional<StoredProfessionalOccupationClassification> classification = classificationAvailable
                ? Optional.of(new StoredProfessionalOccupationClassification(ID,
                    new ProfessionalOccupationClassification("IT business analysts, architects and systems designers",
                            OccupationClassificationSystem.SOC_2020, "2133",
                            OccupationClassificationSource.LOCAL_FIXTURE,
                            OccupationClassificationQuality.DEVELOPMENT_FIXTURE, "fixture",
                            ProfessionalOccupationClassifierVersions.V1), time)) : Optional.empty();
        return new ProfessionalReportSource(assessment, narrative, evidence, classification);
    }

    private static ProfessionalEvidenceBundle evidence(Instant time) {
        var item = new ProfessionalEvidenceItem(ProfessionalEvidenceCategory.OCCUPATION_OUTLOOK,
                "Illustrative context", "Saved factual summary", "Local fixture evidence", Optional.empty(),
                Optional.empty(), Optional.of("Fixture publisher"), Optional.of("United Kingdom"), Optional.empty(),
                time, Optional.empty(), Optional.empty(), Optional.empty(),
                EvidenceSourceQuality.DEVELOPMENT_FIXTURE, "Related context only");
        return new ProfessionalEvidenceBundle(ID, "professional-evidence-v1", EvidenceSource.LOCAL_FIXTURE,
                "fixture", EvidenceStatus.AVAILABLE, time, List.of(item));
    }
}
