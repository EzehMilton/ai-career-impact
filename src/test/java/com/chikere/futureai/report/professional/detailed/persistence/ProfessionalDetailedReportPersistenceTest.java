package com.chikere.futureai.report.professional.detailed.persistence;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.domain.ProfessionalAssessmentState;
import com.chikere.futureai.assessment.professional.application.AssessmentSource;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentResult;
import com.chikere.futureai.assessment.professional.application.ProfessionalAssessmentStore;
import com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider;
import com.chikere.futureai.assessment.professional.application.StoredProfessionalAssessment;
import com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV2;
import com.chikere.futureai.judgment.fixture.professional.LocalFixtureProfessionalJudgmentProvider;
import com.chikere.futureai.narrative.professional.application.NarrativeRepairFeedback;
import com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator;
import com.chikere.futureai.narrative.professional.paid.PaidNarrativeStatus;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeContext;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeGenerator;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeService;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeStore;
import com.chikere.futureai.narrative.professional.paid.ProfessionalPaidNarrativeValidator;
import com.chikere.futureai.payment.professional.application.*;
import com.chikere.futureai.pdf.professional.openhtml.OpenHtmlProfessionalDetailedReportPdfRenderer;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssembler;
import com.chikere.futureai.report.professional.application.ProfessionalReportSource;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.report.professional.detailed.*;
import com.chikere.futureai.responsibility.professional.analysis.HandBuiltResponsibilityJudgments;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisService;
import com.chikere.futureai.responsibility.professional.analysis.ProfessionalResponsibilityAnalysisStore;
import com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentProvider;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalGuidancePolicyV1;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceService;
import com.chikere.futureai.responsibility.professional.guidance.ProfessionalResponsibilityGuidanceStore;
import com.chikere.futureai.responsibility.professional.policy.ProfessionalResponsibilityPolicyV1;
import com.chikere.futureai.trace.AssessmentTrace;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The detailed report on migrated PostgreSQL (V21): paid narrative, report-v3 snapshot and PDF-v5 bytes, each stored
 * once and read back exactly. The responsibility provider is a mock and the paid narrative is scripted.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProfessionalDetailedReportPersistenceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-27T12:00:00Z"), ZoneOffset.UTC);

    @MockitoBean ProfessionalJudgmentProvider unusedLiveProvider;
    @MockitoBean ProfessionalNarrativeGenerator unusedNarrativeGenerator;
    @MockitoBean com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider unusedUniversityProvider;
    @MockitoBean com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator unusedUniversityNarrative;
    @MockitoBean com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider unusedALevelProvider;
    @MockitoBean com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator unusedALevelNarrative;

    @Autowired ProfessionalAssessmentStore assessments;
    @Autowired ProfessionalPurchaseStore purchases;
    @Autowired ProfessionalResponsibilityAnalysisStore analyses;
    @Autowired ProfessionalResponsibilityGuidanceStore guidance;
    @Autowired ProfessionalPaidNarrativeStore narratives;
    @Autowired ProfessionalDetailedReportStore reports;
    @Autowired ProfessionalDetailedReportPdfStore pdfs;
    @Autowired DataSource dataSource;

    @Test
    void theCompleteLifecycleStoresEachArtefactOnceAndReusesItWithoutAnyProviderCall() throws Exception {
        var id = purchasedAssessment(List.of("Prepare monthly accounts", "Meet department managers", "Install equipment"));
        var jev = provider();
        var openAi = new AtomicInteger();
        var first = service(jev, openAi).prepare(id, Optional.empty());
        var again = service(jev, openAi).prepare(id, Optional.empty());

        assertThat(first.reused()).isFalse();
        assertThat(again.reused()).isTrue();
        assertThat(again.snapshot()).isEqualTo(first.snapshot());
        assertThat(reports.findByAssessmentId(id)).isEqualTo(first.snapshot());
        verify(jev, times(1)).judge(any());
        assertThat(openAi).hasValue(1);

        var snapshot = first.snapshot().orElseThrow();
        assertThat(snapshot.paidNarrativeStatus()).isEqualTo(PaidNarrativeStatus.AVAILABLE);
        var narrative = narratives.findByAssessmentId(id).orElseThrow();
        assertThat(narrative.narrativeId()).isEqualTo(snapshot.paidNarrativeId());
        assertThat(narrative.narrative()).isPresent();
        assertThat(narrative.model()).contains("scripted-model");
        assertThat(narrative.providerRequestId()).contains("req_scripted");
        var row = new JdbcTemplate(dataSource).queryForMap("select * from professional_detailed_report where assessment_id = ?", id);
        assertThat(row).containsEntry("report_version", "professional-report-v3")
                .containsEntry("pdf_version", "professional-report-pdf-v5")
                .containsEntry("role_report_version", "professional-report-v2")
                .containsEntry("decision_policy_version", "professional-decision-v2")
                .containsEntry("responsibility_schema_version", "professional-responsibility-v1")
                .containsEntry("responsibility_policy_version", "professional-responsibility-policy-v1")
                .containsEntry("guidance_policy_version", "professional-guidance-policy-v1")
                .containsEntry("paid_narrative_version", "professional-paid-narrative-v1")
                .containsEntry("paid_narrative_status", "AVAILABLE");

        // The v5 PDF is rendered once from the stored report and its bytes are reused.
        var pdfService = new ProfessionalDetailedReportPdfService(purchases, reports, service(jev, openAi), pdfs,
                new ProfessionalDetailedReportViewMapper(), new OpenHtmlProfessionalDetailedReportPdfRenderer(), 5_242_880, CLOCK);
        var pdf = pdfService.findOrCreate(id).orElseThrow();
        assertThat(pdf.bytes()).startsWith("%PDF-".getBytes());
        assertThat(pdf.provisional()).isFalse();
        assertThat(pdfs.findByReportId(snapshot.reportId()).orElseThrow().pdfVersion()).isEqualTo("professional-report-pdf-v5");
        assertThat(pdfService.findOrCreate(id).orElseThrow().bytes()).isEqualTo(pdf.bytes());
        assertThat(count("professional_detailed_report_pdf", id)).isEqualTo(1);

        // The purchased role-level snapshot is untouched.
        assertThat(purchases.findSnapshotByAssessmentId(id).orElseThrow().reportVersion()).isEqualTo("professional-report-v2");
    }

    @Test
    void oneNarrativeAndOneReportPerAssessmentAndASecondSaveNeverOverwrites() {
        var id = purchasedAssessment(List.of("Prepare monthly accounts"));
        var stored = service(provider(), new AtomicInteger()).prepare(id, Optional.empty()).snapshot().orElseThrow();
        var competing = new ProfessionalDetailedReportSnapshot(UUID.randomUUID(), id, stored.entitlementId(),
                stored.analysisId(), stored.guidanceId(), stored.paidNarrativeId(), stored.paidNarrativeStatus(),
                stored.reportVersion(), stored.pdfVersion(), stored.generatedAt().plusSeconds(60), stored.report());
        assertThat(reports.saveIfAbsent(competing)).isEqualTo(stored);
        assertThat(count("professional_detailed_report", id)).isEqualTo(1);
        var narrative = narratives.findByAssessmentId(id).orElseThrow();
        assertThatThrownBy(() -> new JdbcTemplate(dataSource).update("insert into professional_paid_narrative (id, "
                + "assessment_id, analysis_id, guidance_id, narrative_version, status, provider, attempts, fallback_reason, "
                + "generated_at) values (?, ?, ?, ?, 'v', 'FALLBACK', 'x', 0, 'r', now())", UUID.randomUUID(), id,
                narrative.analysisId(), narrative.guidanceId())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRejectsAnAvailableNarrativeWithoutTextAndAFallbackWithoutAReason() {
        var id = purchasedAssessment(List.of("Prepare monthly accounts"));
        service(provider(), new AtomicInteger()).prepare(id, Optional.empty());
        var narrative = narratives.findByAssessmentId(id).orElseThrow();
        new JdbcTemplate(dataSource).update("delete from professional_detailed_report where assessment_id = ?", id);
        new JdbcTemplate(dataSource).update("delete from professional_paid_narrative where assessment_id = ?", id);
        for (String values : List.of("'AVAILABLE', null, null", "'FALLBACK', null, null", "'UNKNOWN', null, 'r'",
                "'RETRYABLE_FALLBACK', null, 'r'")) {
            assertThatThrownBy(() -> new JdbcTemplate(dataSource).update("insert into professional_paid_narrative (id, "
                    + "assessment_id, analysis_id, guidance_id, narrative_version, status, narrative, fallback_reason, "
                    + "provider, attempts, generated_at) values (?, ?, ?, ?, 'v', " + values + ", 'x', 0, now())",
                    UUID.randomUUID(), id, narrative.analysisId(), narrative.guidanceId()))
                    .as(values).isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    @Test
    void aStoredReportIsReadBackExactlyAsStoredAndNeverReassembled() {
        var id = purchasedAssessment(List.of("Prepare monthly accounts", "Meet department managers"));
        var openAi = new AtomicInteger();
        service(provider(), openAi).prepare(id, Optional.empty());
        new JdbcTemplate(dataSource).update("update professional_detailed_report set report = jsonb_set(report, "
                + "'{executiveSummary,0}', '\"An older summary\"') where assessment_id = ?", id);
        var served = service(provider(), openAi).prepare(id, Optional.empty()).snapshot().orElseThrow();
        assertThat(served.report().executiveSummary().getFirst()).isEqualTo("An older summary");
        assertThat(openAi).hasValue(1);
    }

    @Test
    void aTransientNarrativeFailureStoresNothingCanonicalAndALaterPreparationFreezesTheReport() {
        var id = purchasedAssessment(List.of("Prepare monthly accounts", "Meet department managers", "Install equipment"));
        var jev = provider();
        var outage = new AtomicInteger(-1);
        var provisional = service(jev, outage).prepare(id, Optional.empty());
        assertThat(provisional.provisional()).isTrue();
        assertThat(provisional.report().narrativeStatus()).isEqualTo(PaidNarrativeStatus.RETRYABLE_FALLBACK);
        assertThat(provisional.report().opportunities().items()).isNotEmpty();
        assertThat(count("professional_paid_narrative", id)).isZero();
        assertThat(count("professional_detailed_report", id)).isZero();
        // Analysis and guidance are stored, so the provisional report can be shown without any provider call.
        assertThat(service(jev, outage).provisional(id)).isPresent();
        // A provisional PDF is rendered for the download only; nothing is stored.
        var pdfService = new ProfessionalDetailedReportPdfService(purchases, reports, service(jev, outage), pdfs,
                new ProfessionalDetailedReportViewMapper(), new OpenHtmlProfessionalDetailedReportPdfRenderer(), 5_242_880, CLOCK);
        assertThat(pdfService.findOrCreate(id).orElseThrow().provisional()).isTrue();
        assertThat(count("professional_detailed_report_pdf", id)).isZero();

        var recovered = new AtomicInteger();
        var canonical = service(jev, recovered).prepare(id, Optional.empty()).snapshot().orElseThrow();
        assertThat(canonical.paidNarrativeStatus()).isEqualTo(PaidNarrativeStatus.AVAILABLE);
        assertThat(count("professional_detailed_report", id)).isEqualTo(1);
        verify(jev, times(1)).judge(any());
        assertThat(recovered).hasValue(1);
    }

    // ---- Helpers ----

    /** {@code openAi} counts calls; starting at -1 makes the scripted provider fail like a timeout. */
    private ProfessionalDetailedReportService service(ProfessionalResponsibilityJudgmentProvider jev, AtomicInteger openAi) {
        ProfessionalReportPurchaseUseCase purchaseUseCase = mock(ProfessionalReportPurchaseUseCase.class);
        when(purchaseUseCase.findEntitlement(any())).thenAnswer(call -> purchases.findEntitlementByAssessmentId(call.getArgument(0)));
        var analysisService = new ProfessionalResponsibilityAnalysisService(assessments, purchaseUseCase,
                () -> Optional.of(jev), new ProfessionalResponsibilityPolicyV1(), analyses, CLOCK, AssessmentTrace.disabled());
        var guidanceService = new ProfessionalResponsibilityGuidanceService(purchaseUseCase, analysisService,
                new ProfessionalGuidancePolicyV1(), guidance, CLOCK, AssessmentTrace.disabled());
        ProfessionalPaidNarrativeGenerator generator = new ProfessionalPaidNarrativeGenerator() {
            @Override
            public GeneratedPaidNarrative generate(ProfessionalPaidNarrativeContext context) {
                if (openAi.get() < 0) {
                    throw new IllegalStateException("timeout");
                }
                openAi.incrementAndGet();
                return new GeneratedPaidNarrative(DetailedReportFixtures.validNarrative(context),
                        Optional.of("req_scripted"), Optional.of("scripted-model"));
            }

            @Override
            public GeneratedPaidNarrative repair(ProfessionalPaidNarrativeContext context, NarrativeRepairFeedback feedback) {
                return generate(context);
            }

            @Override
            public String providerIdentifier() {
                return "scripted";
            }

            @Override
            public Optional<String> modelIdentifier() {
                return Optional.empty();
            }
        };
        var narrativeService = new ProfessionalPaidNarrativeService(narratives, () -> Optional.of(generator),
                new ProfessionalPaidNarrativeValidator(), CLOCK, AssessmentTrace.disabled());
        return new ProfessionalDetailedReportService(purchases, assessments, analysisService, guidanceService,
                narrativeService, new ProfessionalDetailedReportAssembler(), reports, CLOCK, AssessmentTrace.disabled());
    }

    private UUID purchasedAssessment(List<String> responsibilities) {
        var state = new ProfessionalAssessmentState("Management Accountant", "I prepare accounts and advise managers.",
                responsibilities, List.of("Excel"), OptionalInt.of(7));
        var judgments = new LocalFixtureProfessionalJudgmentProvider("senior_software_architect").judge(state);
        var decision = new ProfessionalDecisionPolicyV2().decide(judgments);
        var assessment = new StoredProfessionalAssessment(UUID.randomUUID(), Instant.parse("2026-10-27T08:00:00Z"), state,
                judgments, new ProfessionalAssessmentResult(decision, AssessmentSource.LOCAL_FIXTURE,
                        judgments.schemaVersion(), decision.policyVersion()), Optional.empty());
        assessments.save(assessment);
        var report = new ProfessionalReportAssembler().assemble(new ProfessionalReportSource(assessment, Optional.empty(),
                Optional.empty(), Optional.empty()), ProfessionalReportTier.PAID);
        UUID entitlementId = UUID.randomUUID();
        Instant at = Instant.now(CLOCK);
        purchases.saveComplimentary(new ProfessionalReportEntitlement(entitlementId, assessment.id(),
                ProfessionalReportTier.PAID, ProfessionalEntitlementStatus.ACTIVE, ProfessionalPaymentProvider.COMPLIMENTARY,
                "complimentary_" + entitlementId, URI.create("http://localhost/report"), "complimentary", Optional.empty(),
                Optional.of(report.reportVersion()), at, Optional.of(at)),
                new PurchasedProfessionalReportSnapshot(entitlementId, assessment.id(), report.reportVersion(), at, report));
        return assessment.id();
    }

    /** Hand-built per-responsibility values: a good opportunity, a human-led item and a hands-on item. */
    private static ProfessionalResponsibilityJudgmentProvider provider() {
        ProfessionalResponsibilityJudgmentProvider provider = mock(ProfessionalResponsibilityJudgmentProvider.class);
        when(provider.judge(any())).thenAnswer(call -> {
            com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityAnalysisRequest request =
                    call.getArgument(0);
            var base = HandBuiltResponsibilityJudgments.forRequest(request);
            var sets = new java.util.ArrayList<com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet>();
            for (var set : base.responsibilities()) {
                double[] v = switch (set.responsibilityIndex()) {
                    case 1 -> new double[] {3.4, 1.6, 1.2, 0.2, 2.0};
                    case 2 -> new double[] {2.6, 2.8, 3.6, 0.4, 2.2};
                    default -> new double[] {1.4, 2.0, 1.8, 3.8, 3.8};
                };
                sets.add(new com.chikere.futureai.responsibility.professional.application.ResponsibilityJudgmentSet(
                        set.responsibilityIndex(), set.responsibilityKey(), set.responsibilityText(), 0.4,
                        HandBuiltResponsibilityJudgments.judgment(v[0]), HandBuiltResponsibilityJudgments.judgment(v[1]),
                        HandBuiltResponsibilityJudgments.judgment(v[2]), HandBuiltResponsibilityJudgments.judgment(v[3]),
                        HandBuiltResponsibilityJudgments.judgment(v[4])));
            }
            return new com.chikere.futureai.responsibility.professional.application.ProfessionalResponsibilityJudgmentResult(
                    base.schemaVersion(), base.source(), base.model(), base.providerRequestId(), base.inputTokens(),
                    base.outputTokens(), base.regulatedContext(), sets);
        });
        return provider;
    }

    private int count(String table, UUID assessmentId) {
        return new JdbcTemplate(dataSource).queryForObject("select count(*) from " + table + " where assessment_id = ?",
                Integer.class, assessmentId);
    }
}
