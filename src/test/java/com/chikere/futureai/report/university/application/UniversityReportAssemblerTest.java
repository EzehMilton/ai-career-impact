package com.chikere.futureai.report.university.application;

import com.chikere.futureai.assessment.domain.UniversityAssessmentState;
import com.chikere.futureai.assessment.university.application.AssessmentSource;
import com.chikere.futureai.assessment.university.application.StoredUniversityAssessment;
import com.chikere.futureai.assessment.university.application.UniversityAssessmentResult;
import com.chikere.futureai.decision.university.UniversityDecisionPolicyV1;
import com.chikere.futureai.decision.university.UniversityMeasure;
import com.chikere.futureai.evidence.university.application.EvidenceSource;
import com.chikere.futureai.evidence.university.application.EvidenceSourceQuality;
import com.chikere.futureai.evidence.university.application.EvidenceStatus;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceBundle;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceCategory;
import com.chikere.futureai.evidence.university.application.UniversityEvidenceItem;
import com.chikere.futureai.judgment.fixture.university.LocalFixtureUniversityJudgmentProvider;
import com.chikere.futureai.narrative.university.application.NarrativeSource;
import com.chikere.futureai.narrative.university.application.NarrativeStatus;
import com.chikere.futureai.narrative.university.application.StoredUniversityNarrative;
import com.chikere.futureai.narrative.university.application.UniversityNarrative;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UniversityReportAssemblerTest {

    private static final UUID ID = UUID.fromString("9a8b7c6d-5e4f-4a3b-8c2d-1e0f9a8b7c6d");
    private final UniversityReportAssembler assembler = new UniversityReportAssembler();

    @Test
    void freeAndPaidPreserveExactlyTheSameAuthoritativeAssessment() {
        var source = source(true, true);

        UniversityReport free = assembler.assemble(source, UniversityReportTier.FREE);
        UniversityReport paid = assembler.assemble(source, UniversityReportTier.PAID);

        assertThat(free.measures()).hasSize(5);
        assertThat(free.measures()).extracting(UniversityReport.MeasureSummary::measure,
                        UniversityReport.MeasureSummary::indicator, UniversityReport.MeasureSummary::band)
                .containsExactlyElementsOf(paid.measures().stream()
                        .map(value -> org.assertj.core.groups.Tuple.tuple(value.measure(), value.indicator(),
                                value.band())).toList());
        assertThat(free.assessmentSummary().status()).isEqualTo(paid.assessmentSummary().status());
        assertThat(free.assessmentSummary().confidence()).isEqualTo(paid.assessmentSummary().confidence());
        assertThat(free.keyStrengths()).isEqualTo(paid.keyStrengths());
        assertThat(free.developmentAreas()).isEqualTo(paid.developmentAreas());
        assertThat(free.reportVersion()).isEqualTo("university-report-v1");
    }

    @Test
    void freeIsUsefulButExcludesEveryPremiumOnlySection() {
        UniversityReport free = assembler.assemble(source(true, true), UniversityReportTier.FREE);

        assertThat(free.measures()).isNotEmpty();
        assertThat(free.assessmentSummary().interpretation()).isNotBlank();
        assertThat(free.assessmentSummary().employmentDisclaimer())
                .contains("not a guarantee of employment");
        assertThat(free.narrative().available()).isTrue();
        assertThat(free.narrative().summary()).isPresent();
        assertThat(free.narrative().whatThisMeans()).isPresent();

        assertThat(free.narrative().futureReadinessExplanation()).isEmpty();
        assertThat(free.narrative().strengthsExplanation()).isEmpty();
        assertThat(free.narrative().developmentOpportunitiesExplanation()).isEmpty();
        assertThat(free.narrative().complementarityExplanation()).isEmpty();
        assertThat(free.narrative().transferableSkillsExplanation()).isEmpty();
        assertThat(free.narrative().careerAdaptabilityExplanation()).isEmpty();
        assertThat(free.narrative().cautionOrLimitations()).isEmpty();
        assertThat(free.practicalActions()).isEmpty();
        assertThat(free.skillPriorities()).isEmpty();
        assertThat(free.careerAreas().areasToExplore()).isEmpty();
        assertThat(free.evidence().status()).isEqualTo(EvidenceStatus.NOT_REQUESTED);
        assertThat(free.evidence().items()).isEmpty();
        assertThat(free.evidence().message()).contains("where available");
        assertThat(free.measures()).allSatisfy(measure ->
                assertThat(measure.detailedExplanation()).isEmpty());
    }

    @Test
    void paidAddsNarrativeActionsSkillsCareerContextAndEvidence() {
        UniversityReport paid = assembler.assemble(source(true, true), UniversityReportTier.PAID);

        assertThat(paid.narrative().futureReadinessExplanation()).contains("Premium readiness detail");
        assertThat(paid.practicalActions()).containsExactly("Premium action one", "Premium action two",
                "Premium action three");
        assertThat(paid.skillPriorities()).isNotEmpty();
        assertThat(paid.careerAreas().areasToExplore()).containsExactly("Software engineering");
        assertThat(paid.careerAreas().guidance()).contains("relevant areas to explore",
                "skills that may help", "additional preparation is worth considering");
        assertThat(paid.careerAreas().limitation()).contains("does not recommend");
        assertThat(paid.evidence().status()).isEqualTo(EvidenceStatus.AVAILABLE);
        assertThat(paid.evidence().items()).hasSize(1);
        assertThat(paid.measures()).allSatisfy(measure ->
                assertThat(measure.detailedExplanation()).isPresent());
    }

    @Test
    void compositionIsDeterministicAndNeverInventsAJobRecommendation() {
        var source = source(true, true);

        assertThat(assembler.assemble(source, UniversityReportTier.PAID))
                .isEqualTo(assembler.assemble(source, UniversityReportTier.PAID));

        UniversityReport paid = assembler.assemble(source, UniversityReportTier.PAID);
        String prose = (paid.headline() + " " + paid.assessmentSummary().interpretation() + " "
                + paid.assessmentSummary().employmentDisclaimer() + " "
                + paid.assessmentSummary().methodologyCaveat() + " "
                + String.join(" ", paid.keyStrengths()) + " "
                + String.join(" ", paid.developmentAreas()) + " "
                + paid.measures().stream().map(UniversityReport.MeasureSummary::conciseExplanation)
                        .reduce("", (left, right) -> left + " " + right) + " "
                + paid.careerAreas().guidance() + " " + paid.careerAreas().limitation() + " "
                + paid.skillPriorities().stream().map(UniversityReport.SkillDevelopmentPriority::rationale)
                        .reduce("", (left, right) -> left + " " + right))
                .toLowerCase(java.util.Locale.ROOT);

        assertThat(prose).doesNotContain("you should become", "ai exposure", "job risk",
                "automation risk", "future-proof", "bad degree", "guaranteed", "will get a job");
        // AI Exposure is named in exactly one place: the explicit cross-journey separation caveat.
        assertThat(paid.methodology().limitations().stream()
                .filter(value -> value.contains("AI Exposure")).toList()).hasSize(1);
    }

    @Test
    void methodologyStatesTheCrossJourneySeparationAndProvisionalPolicy() {
        UniversityReport paid = assembler.assemble(source(true, true), UniversityReportTier.PAID);

        assertThat(paid.methodology().judgmentSchemaVersion()).isEqualTo("university-v1");
        assertThat(paid.methodology().decisionPolicyVersion()).isEqualTo("university-decision-v1");
        assertThat(paid.methodology().reportVersion()).isEqualTo("university-report-v1");
        assertThat(paid.methodology().narrativeVersion()).contains("university-narrative-v1");
        assertThat(paid.methodology().evidenceVersion()).contains("university-evidence-v1");
        assertThat(paid.methodology().limitations()).anySatisfy(limitation ->
                assertThat(limitation).contains("different constructs on different scales"));
        assertThat(paid.methodology().limitations()).anySatisfy(limitation ->
                assertThat(limitation).contains("provisional product policy"));
    }

    @Test
    void missingNarrativeAndEvidenceStillProduceACompleteDeterministicReport() {
        UniversityReport paid = assembler.assemble(source(false, false), UniversityReportTier.PAID);

        assertThat(paid.measures()).hasSize(5);
        assertThat(paid.narrative().available()).isFalse();
        assertThat(paid.practicalActions()).isEmpty();
        assertThat(paid.assessmentSummary().interpretation())
                .contains("This assessment suggests", "future readiness");
        assertThat(paid.evidence().status()).isEqualTo(EvidenceStatus.FAILED);
        assertThat(paid.evidence().message()).isEqualTo("External course context is currently unavailable.");
        assertThat(paid.skillPriorities()).isNotEmpty();
    }

    @Test
    void developmentFixtureProvenanceIsCarriedThrough() {
        assertThat(assembler.assemble(source(true, true), UniversityReportTier.FREE).developmentFixture())
                .isTrue();
    }

    private UniversityReportSource source(boolean narrative, boolean evidence) {
        return new UniversityReportSource(snapshot(),
                narrative ? Optional.of(storedNarrative()) : Optional.empty(),
                evidence ? Optional.of(evidenceBundle()) : Optional.empty());
    }

    private StoredUniversityAssessment snapshot() {
        var state = new UniversityAssessmentState("Computer Science",
                "Programming, algorithms, systems and a final-year project.",
                List.of("Algorithms"), List.of("Programming"), List.of("Software engineering"),
                Optional.of("A UK university"), Optional.of("United Kingdom"));
        var judgments = new LocalFixtureUniversityJudgmentProvider("computer_science").judge(state);
        var decision = new UniversityDecisionPolicyV1().decide(judgments);
        assertThat(decision.measure(UniversityMeasure.FUTURE_READINESS)).isPresent();
        return new StoredUniversityAssessment(ID, Instant.parse("2026-10-01T12:00:00Z"), state, judgments,
                new UniversityAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
    }

    private StoredUniversityNarrative storedNarrative() {
        var narrative = new UniversityNarrative("Headline", "A useful saved summary", "Saved meaning",
                "Premium readiness detail", "Premium strengths detail", "Premium development detail",
                "Premium complementarity detail", "Premium transferability detail",
                "Premium adaptability detail",
                List.of("Premium action one", "Premium action two", "Premium action three"),
                "Premium caution");
        Instant time = Instant.parse("2026-10-01T12:01:00Z");
        return new StoredUniversityNarrative(ID, NarrativeStatus.AVAILABLE, Optional.of(narrative),
                NarrativeSource.LOCAL_FIXTURE, "secret-provider", Optional.empty(),
                "university-narrative-v1", time, Optional.of(time));
    }

    private UniversityEvidenceBundle evidenceBundle() {
        Instant time = Instant.parse("2026-10-01T12:02:00Z");
        var item = new UniversityEvidenceItem(UniversityEvidenceCategory.GRADUATE_OUTCOMES,
                "Illustrative evidence title", "Saved factual summary", "Local fixture evidence",
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of("United Kingdom"),
                Optional.empty(), time, Optional.empty(), Optional.empty(), Optional.empty(),
                EvidenceSourceQuality.DEVELOPMENT_FIXTURE, "Context only");
        return new UniversityEvidenceBundle(ID, "university-evidence-v1", EvidenceSource.LOCAL_FIXTURE,
                "secret-provider", EvidenceStatus.AVAILABLE, time, List.of(item));
    }
}
