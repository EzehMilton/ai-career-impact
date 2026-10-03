package com.chikere.futureai.report.alevel.application;

import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentResult;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.assessment.alevel.application.StoredALevelAssessment;
import com.chikere.futureai.assessment.domain.ALevelAssessmentState;
import com.chikere.futureai.decision.alevel.ALevelDecisionPolicyV1;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceBundle;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceCategory;
import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceItem;
import com.chikere.futureai.evidence.alevel.application.EvidenceSource;
import com.chikere.futureai.evidence.alevel.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.alevel.application.EvidenceStatus;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelJudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrative;
import com.chikere.futureai.narrative.alevel.application.NarrativeSource;
import com.chikere.futureai.narrative.alevel.application.NarrativeStatus;
import com.chikere.futureai.narrative.alevel.application.StoredALevelNarrative;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ALevelReportAssemblerTest {

    private static final UUID ID = UUID.fromString("7a8b9c0d-1e2f-4a3b-8c4d-5e6f7a8b9c0d");
    private final ALevelReportAssembler assembler = new ALevelReportAssembler();

    @Test
    void freeAndPaidPreserveExactlyTheSameAuthoritativeAssessment() {
        var source = source(true, true);

        ALevelReport free = assembler.assemble(source, ALevelReportTier.FREE);
        ALevelReport paid = assembler.assemble(source, ALevelReportTier.PAID);

        assertThat(free.measures()).hasSize(6);
        assertThat(free.measures()).extracting(ALevelReport.MeasureSummary::measure,
                        ALevelReport.MeasureSummary::indicator, ALevelReport.MeasureSummary::band)
                .containsExactlyElementsOf(paid.measures().stream()
                        .map(value -> org.assertj.core.groups.Tuple.tuple(value.measure(),
                                value.indicator(), value.band())).toList());
        assertThat(free.assessmentSummary().status()).isEqualTo(paid.assessmentSummary().status());
        assertThat(free.assessmentSummary().confidence()).isEqualTo(paid.assessmentSummary().confidence());
        assertThat(free.mainStrengths()).isEqualTo(paid.mainStrengths());
        assertThat(free.developmentOpportunities()).isEqualTo(paid.developmentOpportunities());
        assertThat(free.selectedSubjects()).isEqualTo(paid.selectedSubjects());
        assertThat(free.reportVersion()).isEqualTo("a-level-report-v1");
    }

    @Test
    void freeIsUsefulButExcludesEveryPremiumOnlySection() {
        ALevelReport free = assembler.assemble(source(true, true), ALevelReportTier.FREE);

        assertThat(free.measures()).isNotEmpty();
        assertThat(free.selectedSubjects()).containsExactly("Mathematics", "Economics", "Geography");
        assertThat(free.assessmentSummary().interpretation()).isNotBlank();
        assertThat(free.assessmentSummary().admissionsDisclaimer())
                .contains("not a prediction of university admission");
        assertThat(free.narrative().available()).isTrue();
        assertThat(free.narrative().summary()).isPresent();
        assertThat(free.narrative().whatThisMeans()).isPresent();

        assertThat(free.narrative().futureOptionalityExplanation()).isEmpty();
        assertThat(free.narrative().degreePathwayExplanation()).isEmpty();
        assertThat(free.narrative().analyticalFoundationExplanation()).isEmpty();
        assertThat(free.narrative().cautionOrLimitations()).isEmpty();
        assertThat(free.practicalActions()).isEmpty();
        assertThat(free.guidance()).isEmpty();
        assertThat(free.preferredAreas().degreeAreas()).isEmpty();
        assertThat(free.preferredAreas().careerAreas()).isEmpty();
        assertThat(free.evidence().status()).isEqualTo(EvidenceStatus.NOT_REQUESTED);
        assertThat(free.evidence().items()).isEmpty();
        assertThat(free.evidence().message()).contains("where available");
        assertThat(free.measures()).allSatisfy(measure ->
                assertThat(measure.detailedExplanation()).isEmpty());
    }

    @Test
    void paidAddsNarrativeActionsGuidanceAreaContextAndEvidence() {
        ALevelReport paid = assembler.assemble(source(true, true), ALevelReportTier.PAID);

        assertThat(paid.narrative().futureOptionalityExplanation()).contains("Premium optionality detail");
        assertThat(paid.practicalActions()).containsExactly("Premium action one", "Premium action two",
                "Premium action three");
        assertThat(paid.guidance()).isNotEmpty();
        assertThat(paid.preferredAreas().degreeAreas()).containsExactly("Economics");
        assertThat(paid.preferredAreas().careerAreas()).containsExactly("Data analysis");
        assertThat(paid.preferredAreas().guidance()).contains("relevant areas to research further",
                "checking the published subject requirements");
        assertThat(paid.preferredAreas().limitation()).contains("does not confirm that you meet any requirement");
        assertThat(paid.evidence().status()).isEqualTo(EvidenceStatus.AVAILABLE);
        assertThat(paid.evidence().items()).hasSize(1);
        assertThat(paid.evidence().separationNote())
                .contains("subject requirements are facts to check, not part of your assessment");
        assertThat(paid.measures()).allSatisfy(measure ->
                assertThat(measure.detailedExplanation()).isPresent());
    }

    @Test
    void paidGuidanceNeverSuggestsChangingOrDroppingASubject() {
        for (String fixtureId : LocalFixtureALevelJudgmentProvider.supportedFixtureIds()) {
            ALevelReport paid = assembler.assemble(source(fixtureId, false, false), ALevelReportTier.PAID);
            String guidance = paid.guidance().stream()
                    .map(item -> item.title() + " " + item.suggestion())
                    .reduce("", (left, right) -> left + " " + right)
                    .toLowerCase(Locale.ROOT);

            assertThat(paid.guidance()).as("%s guidance", fixtureId).isNotEmpty();
            assertThat(guidance).as("%s wording", fixtureId)
                    .doesNotContain("drop", "swap", "change your subject", "change subject",
                            "instead of", "wrong subject", "retake");
            // Every combination is told to check real course requirements rather than trust a guess.
            assertThat(paid.guidance()).anySatisfy(item ->
                    assertThat(item.title()).isEqualTo("Check specific course requirements early"));
        }
    }

    @Test
    void compositionIsDeterministicAndUsesMeasuredStudentFacingLanguage() {
        var source = source(true, true);

        assertThat(assembler.assemble(source, ALevelReportTier.PAID))
                .isEqualTo(assembler.assemble(source, ALevelReportTier.PAID));

        ALevelReport paid = assembler.assemble(source, ALevelReportTier.PAID);
        String prose = (paid.headline() + " " + paid.assessmentSummary().interpretation() + " "
                + paid.assessmentSummary().admissionsDisclaimer() + " "
                + paid.assessmentSummary().methodologyCaveat() + " "
                + String.join(" ", paid.mainStrengths()) + " "
                + String.join(" ", paid.developmentOpportunities()) + " "
                + paid.measures().stream().map(ALevelReport.MeasureSummary::conciseExplanation)
                        .reduce("", (left, right) -> left + " " + right) + " "
                + paid.preferredAreas().guidance() + " " + paid.preferredAreas().limitation())
                .toLowerCase(Locale.ROOT);

        assertThat(prose).doesNotContain("ai exposure", "future readiness", "job risk",
                "automation risk", "employability score", "future-proof", "bad degree",
                "wrong subject", "bad subject", "guaranteed", "you should become",
                "will limit your future", "you won't get");
    }

    @Test
    void methodologyStatesTheCrossJourneySeparationAndThePrerequisiteSeparation() {
        ALevelReport paid = assembler.assemble(source(true, true), ALevelReportTier.PAID);

        assertThat(paid.methodology().judgmentSchemaVersion()).isEqualTo("a-level-v1");
        assertThat(paid.methodology().decisionPolicyVersion()).isEqualTo("a-level-decision-v1");
        assertThat(paid.methodology().reportVersion()).isEqualTo("a-level-report-v1");
        assertThat(paid.methodology().narrativeVersion()).contains("a-level-narrative-v1");
        assertThat(paid.methodology().evidenceVersion()).contains("a-level-evidence-v1");
        assertThat(paid.methodology().limitations()).anySatisfy(limitation ->
                assertThat(limitation).contains("different constructs on different scales"));
        assertThat(paid.methodology().limitations()).anySatisfy(limitation ->
                assertThat(limitation).contains("provisional product policy")
                        .contains("no subject is awarded points of its own"));
        assertThat(paid.methodology().limitations()).anySatisfy(limitation ->
                assertThat(limitation).contains("shown only in the evidence section with their own source"));
    }

    @Test
    void missingNarrativeAndEvidenceStillProduceACompleteDeterministicReport() {
        ALevelReport paid = assembler.assemble(source(false, false), ALevelReportTier.PAID);

        assertThat(paid.measures()).hasSize(6);
        assertThat(paid.narrative().available()).isFalse();
        assertThat(paid.practicalActions()).isEmpty();
        assertThat(paid.assessmentSummary().interpretation())
                .contains("This assessment suggests", "future optionality");
        assertThat(paid.evidence().status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(paid.evidence().message()).isEqualTo("Factual course context is currently unavailable.");
        assertThat(paid.guidance()).isNotEmpty();
    }

    @Test
    void developmentFixtureProvenanceIsCarriedThrough() {
        assertThat(assembler.assemble(source(true, true), ALevelReportTier.FREE).developmentFixture())
                .isTrue();
    }

    private ALevelReportSource source(boolean narrative, boolean evidence) {
        return source("maths_economics_geography", narrative, evidence);
    }

    private ALevelReportSource source(String fixtureId, boolean narrative, boolean evidence) {
        return new ALevelReportSource(snapshot(fixtureId),
                narrative ? Optional.of(storedNarrative()) : Optional.empty(),
                evidence ? Optional.of(evidenceBundle()) : Optional.empty());
    }

    private StoredALevelAssessment snapshot(String fixtureId) {
        var state = new ALevelAssessmentState(List.of("Mathematics", "Economics", "Geography"),
                Map.of("Mathematics", "A"), List.of("Problem solving"), List.of("Economics"),
                List.of("Data analysis"), Optional.of("United Kingdom"), Optional.empty());
        var judgments = new LocalFixtureALevelJudgmentProvider(fixtureId).judge(state);
        var decision = new ALevelDecisionPolicyV1().decide(judgments);
        return new StoredALevelAssessment(ID, Instant.parse("2026-10-01T12:00:00Z"), state, judgments,
                new ALevelAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }

    private StoredALevelNarrative storedNarrative() {
        var narrative = new ALevelNarrative("Headline", "A useful saved summary", "Saved meaning",
                "Premium optionality detail", "Premium strengths detail", "Premium pathway detail",
                "Premium analytical detail", "Premium human detail", "Premium transferability detail",
                "Premium adaptability detail", "Premium development detail",
                List.of("Premium action one", "Premium action two", "Premium action three"),
                "Premium caution");
        Instant time = Instant.parse("2026-10-01T12:01:00Z");
        return new StoredALevelNarrative(ID, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                NarrativeSource.LOCAL_FIXTURE, "secret-provider", Optional.empty(),
                "a-level-narrative-v1", time, Optional.of(time));
    }

    private ALevelEvidenceBundle evidenceBundle() {
        Instant time = Instant.parse("2026-10-01T12:02:00Z");
        var item = new ALevelEvidenceItem(ALevelEvidenceCategory.DEGREE_PREREQUISITES,
                "Illustrative requirement note", "Saved factual summary", "Local fixture evidence",
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("United Kingdom"),
                Optional.empty(), time, Optional.empty(), Optional.empty(), Optional.empty(),
                EvidenceSourceQuality.DEVELOPMENT_FIXTURE, "Context only");
        return new ALevelEvidenceBundle(ID, "a-level-evidence-v1", EvidenceSource.LOCAL_FIXTURE,
                "secret-provider", EvidenceStatus.AVAILABLE, time, List.of(item));
    }
}
