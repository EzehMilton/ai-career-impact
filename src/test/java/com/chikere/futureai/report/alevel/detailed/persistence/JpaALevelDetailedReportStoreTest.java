package com.chikere.futureai.report.alevel.detailed.persistence;

import com.chikere.futureai.TestcontainersConfiguration;
import com.chikere.futureai.assessment.alevel.application.ALevelAssessmentUseCase;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentInput;
import com.chikere.futureai.assessment.alevel.application.ALevelJudgmentProvider;
import com.chikere.futureai.assessment.alevel.application.ALevelTestAssessments;
import com.chikere.futureai.assessment.alevel.application.AssessmentSource;
import com.chikere.futureai.judgment.fixture.alevel.LocalFixtureALevelV2JudgmentProvider;
import com.chikere.futureai.narrative.alevel.application.ALevelNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeGenerator;
import com.chikere.futureai.narrative.alevel.paid.ALevelPaidNarrativeStatus;
import com.chikere.futureai.narrative.alevel.paid.ScriptedALevelPaidNarrativeGenerator;
import com.chikere.futureai.payment.alevel.application.ALevelReportPurchaseService;
import com.chikere.futureai.payment.complimentary.ComplimentaryRedemptionResult;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportPdfService;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportSnapshot;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportStore;
import com.chikere.futureai.report.alevel.detailed.ALevelDetailedReportUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The a-level-report-v2 journey on migrated PostgreSQL through the real beans: one Jev call for the whole assessment,
 * zero OpenAI calls on the free path and at activation, one paid narrative call at preparation, and none afterwards.
 * Every provider is a mock; nothing real is called.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class JpaALevelDetailedReportStoreTest {

    @MockitoBean com.chikere.futureai.assessment.professional.application.ProfessionalJudgmentProvider unusedProfessional;
    @MockitoBean com.chikere.futureai.narrative.professional.application.ProfessionalNarrativeGenerator unusedNarrative;
    @MockitoBean com.chikere.futureai.assessment.university.application.UniversityJudgmentProvider unusedUniversity;
    @MockitoBean com.chikere.futureai.narrative.university.application.UniversityNarrativeGenerator unusedUniversityNarrative;
    @MockitoBean ALevelJudgmentProvider jev;
    @MockitoBean ALevelNarrativeGenerator freeNarrative;
    @MockitoBean ALevelPaidNarrativeGenerator paidNarrative;

    @Autowired ALevelAssessmentUseCase workflow;
    @Autowired ALevelReportPurchaseService purchases;
    @Autowired ALevelDetailedReportUseCase reports;
    @Autowired ALevelDetailedReportStore store;
    @Autowired ALevelDetailedReportPdfService pdfs;
    @Autowired DataSource dataSource;

    @BeforeEach
    void providers() {
        when(jev.judge(any())).thenAnswer(call -> new LocalFixtureALevelV2JudgmentProvider("maths_economics_geography")
                .judge(call.<ALevelJudgmentInput>getArgument(0)));
        when(jev.source()).thenReturn(AssessmentSource.JEV);
        when(paidNarrative.generate(any())).thenReturn(new ALevelPaidNarrativeGenerator.GeneratedNarrative(
                ScriptedALevelPaidNarrativeGenerator.cleanNarrative(), Optional.of("req_test"), Optional.of("model")));
        when(paidNarrative.providerIdentifier()).thenReturn("openai");
    }

    @Test
    void oneAssessmentIsOneJevCallAndTheStoredReportIsReusedWithNoProviderCall() {
        UUID id = workflow.submit(ALevelTestAssessments.state());
        verify(jev, times(1)).judge(any());
        verify(freeNarrative, never()).generate(any());

        assertThat(purchases.grantComplimentaryAccess(id)).isEqualTo(ComplimentaryRedemptionResult.UNLOCKED);
        assertThat(purchases.activePurchaseVersion(id)).contains("a-level-report-v2");
        // Activation grants access only: no report and no narrative.
        assertThat(store.findByAssessmentId(id)).isEmpty();
        verify(paidNarrative, never()).generate(any());

        var first = reports.prepare(id);
        assertThat(first.provisional()).isFalse();
        verify(paidNarrative, times(1)).generate(any());

        var again = reports.prepare(id);
        var viewed = reports.find(id);
        var pdf = pdfs.render(id);

        assertThat(again.reused()).isTrue();
        assertThat(again.stored()).isEqualTo(first.stored());
        assertThat(viewed).isEqualTo(first.stored());
        assertThat(new String(pdf.bytes(), 0, 5, StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
        // Across submission, activation, two preparations, a view and a PDF: one Jev call, one paid narrative call.
        verify(jev, times(1)).judge(any());
        verify(paidNarrative, times(1)).generate(any());
        verify(paidNarrative, never()).repair(any(), any());
        verify(freeNarrative, never()).generate(any());
    }

    @Test
    void theStoredReportRoundTripsExactlyWithEveryVersionAndIsInsertedOnce() {
        UUID id = workflow.submit(ALevelTestAssessments.state());
        purchases.grantComplimentaryAccess(id);
        var stored = reports.prepare(id).stored().orElseThrow();

        var reloaded = store.findByAssessmentId(id).orElseThrow();
        assertThat(reloaded).isEqualTo(stored);
        var row = new JdbcTemplate(dataSource).queryForMap("select judgment_schema_version, decision_policy_version, "
                + "guidance_policy_version, paid_narrative_version, paid_narrative_status, evidence_version, "
                + "report_version, pdf_version from a_level_detailed_report where assessment_id = ?", id);
        assertThat(row).containsEntry("judgment_schema_version", "a-level-v2")
                .containsEntry("decision_policy_version", "a-level-decision-v2")
                .containsEntry("guidance_policy_version", "a-level-guidance-policy-v1")
                .containsEntry("paid_narrative_version", "a-level-paid-narrative-v1")
                .containsEntry("paid_narrative_status", "AVAILABLE")
                .containsEntry("report_version", "a-level-report-v2")
                .containsEntry("pdf_version", "a-level-report-pdf-v2");

        var rival = new ALevelDetailedReportSnapshot(UUID.randomUUID(), id, stored.entitlementId(),
                stored.judgmentSchemaVersion(), stored.decisionPolicyVersion(), stored.guidancePolicyVersion(),
                stored.paidNarrativeVersion(), ALevelPaidNarrativeStatus.FALLBACK, Optional.empty(),
                stored.reportVersion(), stored.pdfVersion(), Instant.parse("2026-10-29T09:00:00Z"), stored.report());
        assertThat(store.saveIfAbsent(rival)).isEqualTo(stored);
        assertThat(new JdbcTemplate(dataSource).queryForObject(
                "select count(*) from a_level_detailed_report where assessment_id = ?", Integer.class, id)).isEqualTo(1);
    }

    @Test
    void aRetryableNarrativeStoresNothingAndTheRetryMakesNoJevCall() {
        UUID id = workflow.submit(ALevelTestAssessments.state());
        purchases.grantComplimentaryAccess(id);
        when(paidNarrative.generate(any())).thenThrow(new IllegalStateException("timeout"));

        assertThat(reports.prepare(id).provisional()).isTrue();
        assertThat(store.findByAssessmentId(id)).isEmpty();

        doReturn(new ALevelPaidNarrativeGenerator.GeneratedNarrative(
                ScriptedALevelPaidNarrativeGenerator.cleanNarrative(), Optional.empty(), Optional.empty()))
                .when(paidNarrative).generate(any());
        assertThat(reports.prepare(id).stored()).isPresent();
        verify(jev, times(1)).judge(any());
    }
}
