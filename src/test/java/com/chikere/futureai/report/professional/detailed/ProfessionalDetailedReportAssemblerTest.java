package com.chikere.futureai.report.professional.detailed;

import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeContext;
import com.chikere.futureai.responsibility.professional.analysis.ResponsibilityAnalysisItem;
import com.chikere.futureai.responsibility.professional.guidance.GuidanceVocabulary.SafeUseConstraint;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.AiAssistanceOpportunity;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.HumanValueArea;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.PlanAction;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidance.SkillPriority;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chikere.futureai.responsibility.professional.guidance.AnalysisSnapshots;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalGuidancePolicyV1;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** professional-report-v3 keeps every frozen artefact exactly, with or without the paid narrative. */
class ProfessionalDetailedReportAssemblerTest {

    private final ProfessionalDetailedReportAssembler assembler = new ProfessionalDetailedReportAssembler();

    @Test
    void everyRoleLevelAndResponsibilityLevelFindingIsCopiedExactlyAndInOrder() {
        for (String run : DetailedReportFixtures.ROLES.keySet()) {
            var chain = DetailedReportFixtures.chain(run);
            var role = chain.role().report();
            var guidance = chain.guidance().guidance();
            var report = assembler.assemble(chain.role(), chain.analysis(), chain.guidance(),
                    DetailedReportFixtures.fallback(chain));

            assertThat(report.reportVersion()).isEqualTo("professional-report-v3");
            // Scores and bands exactly as purchased.
            assertThat(report.measures()).extracting(ProfessionalDetailedReport.Measure::measure)
                    .containsExactlyElementsOf(role.measures().stream().map(m -> m.measure()).toList());
            assertThat(report.measures()).extracting(ProfessionalDetailedReport.Measure::indicator)
                    .containsExactlyElementsOf(role.measures().stream().map(m -> m.indicator()).toList());
            assertThat(report.measures()).extracting(ProfessionalDetailedReport.Measure::band)
                    .containsExactlyElementsOf(role.measures().stream().map(m -> m.band()).toList());
            // Factors exactly as selected by Stage 2 materiality.
            assertThat(report.factors().raising()).isEqualTo(role.exposureDrivers());
            assertThat(report.factors().lowering()).isEqualTo(role.protectiveFactors());
            // Responsibilities in order, with their stored categories.
            assertThat(report.responsibilities().cards()).extracting(ProfessionalDetailedReport.ResponsibilityCard::key)
                    .containsExactlyElementsOf(chain.analysis().items().stream().map(ResponsibilityAnalysisItem::responsibilityKey).toList());
            assertThat(report.responsibilities().cards()).extracting(ProfessionalDetailedReport.ResponsibilityCard::category)
                    .containsExactlyElementsOf(chain.analysis().items().stream().map(ResponsibilityAnalysisItem::category).toList());
            // Guidance in its deterministic order.
            assertThat(report.opportunities().items()).extracting(ProfessionalDetailedReport.Opportunity::key)
                    .containsExactlyElementsOf(guidance.opportunities().stream().map(AiAssistanceOpportunity::responsibilityKey).toList());
            assertThat(report.opportunities().items()).extracting(ProfessionalDetailedReport.Opportunity::howAiMayAssist)
                    .containsExactlyElementsOf(guidance.opportunities().stream().map(AiAssistanceOpportunity::guidance).toList());
            for (int i = 0; i < guidance.opportunities().size(); i++) {
                assertThat(report.opportunities().items().get(i).conditions()).containsAll(guidance.opportunities().get(i)
                        .constraints().stream().map(SafeUseConstraint::guidance).toList());
            }
            assertThat(report.humanValue().items()).extracting(ProfessionalDetailedReport.HumanValue::key)
                    .containsExactlyElementsOf(guidance.humanValueAreas().stream().map(HumanValueArea::responsibilityKey).toList());
            assertThat(report.skills().items()).extracting(ProfessionalDetailedReport.Skill::label)
                    .containsExactlyElementsOf(guidance.skillPriorities().stream().map(SkillPriority::label).toList());
            assertThat(report.safeguards().items()).extracting(ProfessionalDetailedReport.Safeguard::guidance)
                    .containsExactlyElementsOf(guidance.safeguards().stream().map(s -> s.guidance()).toList());
            var plan = guidance.plan();
            List<List<PlanAction>> phases = List.of(plan.days1To30(), plan.days31To60(), plan.days61To90());
            for (int i = 0; i < 3; i++) {
                assertThat(report.plan().phases().get(i).actions()).extracting(ProfessionalDetailedReport.Action::title)
                        .as(run).containsExactlyElementsOf(phases.get(i).stream().map(PlanAction::title).toList());
            }
        }
    }

    @Test
    void theFallbackReportIsCompleteAndCarriesNoNarrativeText() {
        var chain = DetailedReportFixtures.chain("electrician-8");
        var report = assembler.assemble(chain.role(), chain.analysis(), chain.guidance(),
                DetailedReportFixtures.fallback(chain));
        assertThat(report.narrativeStatus()).isEqualTo(PaidNarrativeStatus.FALLBACK);
        assertThat(report.executiveSummary()).hasSizeGreaterThanOrEqualTo(3);
        assertThat(String.join(" ", report.executiveSummary())).contains("eight responsibilities",
                "Complete safety documentation", "Install electrical wiring and equipment");
        assertThat(report.opportunities().items()).isNotEmpty().allSatisfy(item -> assertThat(item.explanation()).isNull());
        assertThat(report.humanValue().items()).isNotEmpty().allSatisfy(item -> assertThat(item.explanation()).isNull());
        assertThat(report.skills().narrative()).isNull();
        assertThat(report.plan().narrative()).isNull();
        assertThat(report.closingPerspective()).isNull();
        assertThat(report.safeguards().items()).isNotEmpty();
        assertThat(report.plan().phases()).hasSize(3).allSatisfy(phase -> assertThat(phase.actions()).hasSizeBetween(2, 4));
        assertThat(report.limitations()).anySatisfy(line -> assertThat(line).contains("not a prediction of job loss"));
        assertThat(report.nextSteps().getFirst()).contains("Complete safety documentation");
    }

    @Test
    void anAvailableNarrativeIsAttachedByIdBesideTheDeterministicCopyNeverInsteadOfIt() {
        var chain = DetailedReportFixtures.chain("secondary-school-teacher-8");
        ProfessionalPaidNarrativeContext context = chain.context();
        var narrative = DetailedReportFixtures.validNarrative(context);
        var report = assembler.assemble(chain.role(), chain.analysis(), chain.guidance(),
                DetailedReportFixtures.available(chain, narrative));
        var fallback = assembler.assemble(chain.role(), chain.analysis(), chain.guidance(),
                DetailedReportFixtures.fallback(chain));
        assertThat(report.narrativeStatus()).isEqualTo(PaidNarrativeStatus.AVAILABLE);
        assertThat(report.executiveSummary()).hasSize(2).first().asString().startsWith("Your work combines");
        assertThat(report.opportunities().items()).allSatisfy(item -> assertThat(item.explanation())
                .startsWith("This is a sensible place"));
        assertThat(report.plan().phases().getFirst().actions()).allSatisfy(action -> assertThat(action.note())
                .isEqualTo("Keep this step small and note what you learn."));
        // The deterministic content is identical with and without the narrative.
        assertThat(report.opportunities().items()).extracting(ProfessionalDetailedReport.Opportunity::howAiMayAssist)
                .isEqualTo(fallback.opportunities().items().stream().map(ProfessionalDetailedReport.Opportunity::howAiMayAssist).toList());
        assertThat(report.safeguards()).isEqualTo(fallback.safeguards());
        assertThat(report.measures()).isEqualTo(fallback.measures());
        assertThat(report.responsibilities().cards()).isEqualTo(fallback.responsibilities().cards());
    }

    @Test
    void oversightIsMarkedPerCardOnlyWhenItDistinguishesAResponsibility() throws Exception {
        var solicitor = DetailedReportFixtures.chain("solicitor-8");
        var allOverseen = assembler.assemble(solicitor.role(), solicitor.analysis(), solicitor.guidance(),
                DetailedReportFixtures.fallback(solicitor));
        assertThat(allOverseen.responsibilities().oversightNote()).contains("Every responsibility you listed");
        assertThat(allOverseen.responsibilities().cards()).noneMatch(ProfessionalDetailedReport.ResponsibilityCard::showOversight);

        // The live Nurse analysis also marks every responsibility (very high regulated context): one note, no badges.
        var nurse = DetailedReportFixtures.chain("registered-nurse-8");
        assertThat(assembler.assemble(nurse.role(), nurse.analysis(), nurse.guidance(), DetailedReportFixtures.fallback(nurse))
                .responsibilities().oversightNote()).isNotNull();

        var teacher = DetailedReportFixtures.chain("secondary-school-teacher-8");
        var mixed = assembler.assemble(teacher.role(), teacher.analysis(), teacher.guidance(),
                DetailedReportFixtures.fallback(teacher));
        assertThat(mixed.responsibilities().oversightNote()).isNull();
        assertThat(mixed.responsibilities().cards()).anyMatch(ProfessionalDetailedReport.ResponsibilityCard::showOversight)
                .anyMatch(card -> !card.showOversight());

        var java = DetailedReportFixtures.chain("java-software-developer-8");
        var none = assembler.assemble(java.role(), java.analysis(), java.guidance(), DetailedReportFixtures.fallback(java));
        assertThat(none.responsibilities().oversightNote()).isNull();
        assertThat(none.responsibilities().cards()).noneMatch(ProfessionalDetailedReport.ResponsibilityCard::showOversight);
    }

    @Test
    void oversightIsOneSectionNoteFromSeventyFivePercentAndPerCardBadgesBelowWithTheStoredMarkersUnchanged() {
        // Hand-built: accountability 3.9 sets the O1 marker; 2.0 sets none (regulated context Low, so no O2).
        record Case(int marked, boolean sectionLevel, boolean all) { }
        for (Case expected : List.of(new Case(8, true, true), new Case(6, true, false), new Case(5, false, false),
                new Case(2, false, false))) {
            double[][] rows = new double[8][];
            for (int i = 0; i < 8; i++) {
                rows[i] = AnalysisSnapshots.row(3.4, 1.5, 1.0, 0.1, i < expected.marked() ? 3.9 : 2.0);
            }
            var analysis = AnalysisSnapshots.handBuilt(0.5, rows);
            assertThat(analysis.items()).filteredOn(ResponsibilityAnalysisItem::needsCloseHumanOversight)
                    .hasSize(expected.marked());
            var guidance = new ProfessionalResponsibilityGuidanceSnapshot(UUID.randomUUID(), analysis.assessmentId(),
                    analysis.snapshotId(), ProfessionalGuidancePolicyV1.POLICY_VERSION, Instant.EPOCH,
                    new ProfessionalGuidancePolicyV1().decide(analysis));
            var role = DetailedReportFixtures.roleSnapshot(analysis.assessmentId(), "Test role", "java-software-developer-8");
            var report = assembler.assembleProvisional(role, analysis, guidance);
            var section = report.responsibilities();
            String label = expected.marked() + "/8";
            if (expected.sectionLevel()) {
                assertThat(section.oversightNote()).as(label).startsWith(expected.all() ? "Every responsibility" : "Most of");
                assertThat(section.cards()).as(label).noneMatch(ProfessionalDetailedReport.ResponsibilityCard::showOversight);
                assertThat(report.opportunities().items()).as(label).allSatisfy(item -> assertThat(item.conditions())
                        .doesNotContain("This responsibility needs close human oversight."));
            }
            else {
                assertThat(section.oversightNote()).as(label).isNull();
                assertThat(section.cards()).as(label).filteredOn(ProfessionalDetailedReport.ResponsibilityCard::showOversight)
                        .extracting(ProfessionalDetailedReport.ResponsibilityCard::key)
                        .containsExactlyElementsOf(analysis.items().stream().filter(ResponsibilityAnalysisItem::needsCloseHumanOversight)
                                .map(ResponsibilityAnalysisItem::responsibilityKey).toList());
            }
            // Presentation only: the stored markers and every guidance list are untouched.
            assertThat(report.opportunities().items()).extracting(ProfessionalDetailedReport.Opportunity::key)
                    .containsExactlyElementsOf(guidance.guidance().opportunities().stream().map(o -> o.responsibilityKey()).toList());
        }
    }

    @Test
    void theLiveAccountantNowGetsOneOversightNoteInsteadOfSixBadges() {
        var chain = DetailedReportFixtures.chain("management-accountant-8");
        assertThat(chain.analysis().items()).filteredOn(ResponsibilityAnalysisItem::needsCloseHumanOversight).hasSize(6);
        var report = assembler.assemble(chain.role(), chain.analysis(), chain.guidance(), DetailedReportFixtures.fallback(chain));
        assertThat(report.responsibilities().oversightNote()).startsWith("Most of the responsibilities");
        assertThat(report.responsibilities().cards()).noneMatch(ProfessionalDetailedReport.ResponsibilityCard::showOversight);
    }

    @Test
    void noTwoHumanValueAreasShareIdenticalFallbackProseInAnyCalibrationRole() {
        for (String run : DetailedReportFixtures.ROLES.keySet()) {
            var chain = DetailedReportFixtures.chain(run);
            var report = assembler.assemble(chain.role(), chain.analysis(), chain.guidance(), DetailedReportFixtures.fallback(chain));
            var texts = report.humanValue().items().stream().map(ProfessionalDetailedReport.HumanValue::reasonExplanation).toList();
            assertThat(texts).as(run).doesNotHaveDuplicates();
            // Reasons and order are unchanged.
            assertThat(report.humanValue().items()).extracting(ProfessionalDetailedReport.HumanValue::key)
                    .containsExactlyElementsOf(chain.guidance().guidance().humanValueAreas().stream()
                            .map(HumanValueArea::responsibilityKey).toList());
        }
        var customer = DetailedReportFixtures.chain("customer-service-adviser-8");
        var report = assembler.assemble(customer.role(), customer.analysis(), customer.guidance(),
                DetailedReportFixtures.fallback(customer));
        assertThat(report.humanValue().items()).extracting(ProfessionalDetailedReport.HumanValue::reasonExplanation)
                .contains("In “Respond to customer enquiries”, trust, communication and relationship handling are central.",
                        "In “Resolve straightforward complaints”, trust, communication and relationship handling are central.");
    }

    @Test
    void noInternalRuleIdRawJudgmentOrConfidenceReachesTheReportContent() throws Exception {
        for (String run : DetailedReportFixtures.ROLES.keySet()) {
            var chain = DetailedReportFixtures.chain(run);
            var report = assembler.assemble(chain.role(), chain.analysis(), chain.guidance(),
                    DetailedReportFixtures.fallback(chain));
            String customerFacing = new ObjectMapper().findAndRegisterModules().writeValueAsString(Stream.of(
                    report.executiveSummary(), report.responsibilities(), report.opportunities(), report.humanValue(),
                    report.skills(), report.safeguards(), report.plan(), report.limitations(), report.nextSteps()).toList());
            // The stored category enum is data for the template, never displayed; everything else must be free of codes.
            for (var category : com.chikere.futureai.responsibility.professional.policy.ResponsibilityCategory.values()) {
                customerFacing = customerFacing.replace("\"" + category.name() + "\"", "\"\"");
            }
            assertThat(customerFacing).as(run).doesNotContainPattern("\\b[A-Z][A-Z0-9]*_[A-Z0-9_]+\\b")
                    .doesNotContain("probabilit", "distribution", "confidence", "describedClearly", "closeCall",
                            "threshold", "automatable", "will replace", "replace you", "safe from", "AI-proof", "future-proof");
        }
    }
}
