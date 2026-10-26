package com.chikere.futureai.payment.professional.persistence;

import com.chikere.futureai.pdf.professional.application.ProfessionalReportPdfModel;
import com.chikere.futureai.pdf.professional.application.ProfessionalReportPdfVersions;
import com.chikere.futureai.report.professional.application.ProfessionalReport;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssembler;
import com.chikere.futureai.report.professional.application.ProfessionalReportAssemblerTest;
import com.chikere.futureai.report.professional.application.ProfessionalReportTier;
import com.chikere.futureai.report.professional.application.ProfessionalReportVersions;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** New purchases freeze professional-report-v2; snapshots frozen as v1 stay readable exactly as they were. */
class PurchasedProfessionalReportSnapshotCodecTest {

    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();
    private final PurchasedProfessionalReportSnapshotCodec codec = new PurchasedProfessionalReportSnapshotCodec(json);

    private ProfessionalReport current() {
        return new ProfessionalReportAssembler().assemble(ProfessionalReportAssemblerTest.source(true, true, true),
                ProfessionalReportTier.PAID);
    }

    @Test
    void aNewPurchaseIsFrozenAsTheCurrentV2ContentAndReadsBackUnchanged() {
        var report = current();
        assertThat(report.reportVersion()).isEqualTo("professional-report-v2").isEqualTo(ProfessionalReportVersions.CURRENT);
        assertThat(report.methodology().reportVersion()).isEqualTo("professional-report-v2");

        assertThat(codec.read("professional-report-v2", codec.write(report))).isEqualTo(report);
    }

    @Test
    void aSnapshotFrozenAsV1IsReadBackWithItsOriginalVersionAndContent() {
        var report = current();
        ObjectNode stored = (ObjectNode) codec.write(report);
        stored.put("reportVersion", "professional-report-v1");
        ((ObjectNode) stored.get("methodology")).put("reportVersion", "professional-report-v1");
        ((ObjectNode) stored.withArray("measures").get(0)).put("conciseExplanation", "Original v1 wording.");

        ProfessionalReport historical = codec.read("professional-report-v1", stored);

        assertThat(historical.reportVersion()).isEqualTo("professional-report-v1");
        assertThat(historical.methodology().reportVersion()).isEqualTo("professional-report-v1");
        assertThat(historical.measures().getFirst().conciseExplanation()).isEqualTo("Original v1 wording.");
        assertThat(historical.measures()).extracting(ProfessionalReport.MeasureSummary::indicator)
                .containsExactlyElementsOf(report.measures().stream().map(ProfessionalReport.MeasureSummary::indicator).toList());
        // A historical snapshot still renders: its PDF model keeps the v1 content version under the current template.
        var model = new ProfessionalReportPdfModel(ProfessionalReportPdfVersions.PDF_TEMPLATE, "professional-report-v1",
                Instant.parse("2026-09-01T00:00:00Z"), historical);
        assertThat(model.purchasedReportVersion()).isEqualTo("professional-report-v1");
        assertThat(model.pdfTemplateVersion()).isEqualTo("professional-report-pdf-v4");
    }

    @Test
    void aSnapshotFrozenFromAV1AssessmentKeepsItsV1FactorsAfterTheV2PolicyChange() {
        var nurse = com.chikere.futureai.decision.professional.ProfessionalMaterialityCalibrationCases.registeredNurse();
        var v1 = new ProfessionalReportAssembler().assemble(ProfessionalReportAssemblerTest.source(true, true, true,
                nurse, new com.chikere.futureai.decision.professional.ProfessionalDecisionPolicyV1()),
                ProfessionalReportTier.PAID);
        assertThat(v1.exposureDrivers()).hasSize(2);

        ProfessionalReport frozen = codec.read(v1.reportVersion(), codec.write(v1));

        assertThat(frozen).isEqualTo(v1);
        assertThat(frozen.exposureDrivers()).isEqualTo(v1.exposureDrivers());
        assertThat(frozen.methodology().decisionPolicyVersion()).isEqualTo("professional-decision-v1");
        // The report version describes composition, not the decision, so it is unchanged by materiality.
        assertThat(frozen.reportVersion()).isEqualTo("professional-report-v2");
    }

    @Test
    void onlyTheCurrentVersionIsWrittenAndUnknownVersionsAreRefused() {
        ObjectNode stored = (ObjectNode) codec.write(current());
        stored.put("reportVersion", "professional-report-v1");
        ProfessionalReport historical = codec.read("professional-report-v1", stored);

        assertThatThrownBy(() -> codec.write(historical)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("professional-report-v1");
        assertThatThrownBy(() -> codec.read("professional-report-v9", stored)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("professional-report-v9");
    }
}
