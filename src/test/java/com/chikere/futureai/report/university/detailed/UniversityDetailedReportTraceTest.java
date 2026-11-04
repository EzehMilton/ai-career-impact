package com.chikere.futureai.report.university.detailed;

import com.chikere.futureai.coursemodule.university.analysis.UniversityModuleAnalysisUnavailableException;
import com.chikere.futureai.narrative.university.paid.ScriptedPaidNarrativeGenerator;
import com.chikere.futureai.trace.AssessmentTrace;
import com.chikere.futureai.trace.TraceLogCapture;
import com.chikere.futureai.trace.TraceStage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The university-report-v2 lifecycle as traced end to end on the real services: selection refused before Jev,
 * preparation, module analysis, paid narrative, storage, reuse and the PDF. Identifiers and versions only.
 */
class UniversityDetailedReportTraceTest {

    private final DetailedReportTestPorts ports = new DetailedReportTestPorts();
    private final AssessmentTrace trace = AssessmentTrace.enabled();

    @Test
    void theV2LifecycleIsTracedFromSelectionToTheStoredReportAndItsPdf() {
        UUID id = ports.paidCourseProfile(10);
        ports.narrative.then(ScriptedPaidNarrativeGenerator.cleanNarrative());
        var service = ports.service(trace);

        try (TraceLogCapture log = TraceLogCapture.start()) {
            assertThatThrownBy(() -> service.prepare(id, Optional.empty()))
                    .isInstanceOf(UniversityModuleAnalysisUnavailableException.class);
            assertThat(log.stages()).containsSubsequence("UNIVERSITY_REPORT_V2_REQUEST",
                    "UNIVERSITY_MODULE_ANALYSIS_DENIED");
            assertThat(log.only(TraceStage.UNIVERSITY_MODULE_ANALYSIS_DENIED).at("/payload/reason").asText())
                    .isEqualTo("MODULE_SELECTION_REQUIRED");
            assertThat(log.only(TraceStage.UNIVERSITY_MODULE_ANALYSIS_DENIED).at("/payload/providerCalled")
                    .asBoolean()).isFalse();
            assertThat(log.stages()).doesNotContain("UNIVERSITY_MODULE_ANALYSIS_REQUEST", "PAID_NARRATIVE_REQUEST");

            log.clear();
            var stored = service.prepare(id, Optional.of(List.of(1, 2, 3, 4, 5, 6, 7, 8))).stored().orElseThrow();
            assertThat(log.stages()).containsSubsequence("UNIVERSITY_REPORT_V2_REQUEST",
                    "UNIVERSITY_MODULE_ANALYSIS_REQUEST", "UNIVERSITY_MODULE_ANALYSIS_PERSISTED",
                    "PAID_NARRATIVE_REQUEST", "PAID_NARRATIVE_RESPONSE", "PAID_NARRATIVE_VALIDATION",
                    "UNIVERSITY_REPORT_V2_GENERATED");
            var request = log.only(TraceStage.UNIVERSITY_REPORT_V2_REQUEST).get("payload");
            assertThat(request.get("entitlement").asText()).isEqualTo("ACTIVE");
            assertThat(request.get("selectionSupplied").asBoolean()).isTrue();
            assertThat(log.only(TraceStage.PAID_NARRATIVE_RESPONSE).at("/payload/status").asText())
                    .isEqualTo("SUCCEEDED");
            var generated = log.only(TraceStage.UNIVERSITY_REPORT_V2_GENERATED).get("payload");
            assertThat(generated.get("reportId").asText()).isEqualTo(stored.reportId().toString());
            assertThat(generated.get("paidNarrativeStatus").asText()).isEqualTo("AVAILABLE");
            assertThat(generated.get("moduleAnalysisId").asText()).isEqualTo(stored.moduleAnalysisId().toString());
            // The report body is never logged with the lifecycle event.
            assertThat(generated.has("report")).isFalse();

            log.clear();
            service.prepare(id, Optional.empty());
            assertThat(log.stages()).containsExactly("UNIVERSITY_REPORT_V2_REQUEST", "UNIVERSITY_REPORT_V2_REUSED");
            assertThat(log.only(TraceStage.UNIVERSITY_REPORT_V2_REQUEST).at("/payload/reportStored").asBoolean())
                    .isTrue();

            log.clear();
            var pdf = new UniversityDetailedReportPdfService(service, new UniversityDetailedReportPdfRenderer() {
                @Override
                public String pdfVersion() {
                    return "university-report-pdf-v3";
                }

                @Override
                public byte[] render(UniversityDetailedReport report) {
                    return "%PDF-".getBytes();
                }
            }, trace).render(id);
            var rendered = log.only(TraceStage.UNIVERSITY_REPORT_V2_PDF_RENDERED).get("payload");
            assertThat(rendered.get("reportId").asText()).isEqualTo(stored.reportId().toString());
            assertThat(rendered.get("pdfVersion").asText()).isEqualTo("university-report-pdf-v3");
            assertThat(rendered.get("bytes").asInt()).isEqualTo(pdf.bytes().length);
            assertThat(log.all()).doesNotContain("%PDF-");
        }
        assertThat(ports.jevRequests).hasSize(1);
        assertThat(ports.narrative.calls()).isEqualTo(1);
    }

    @Test
    void aRetryableNarrativeIsTracedAsProvisionalAndNothingStored() {
        UUID id = ports.paidCourseProfile(6);
        ports.narrativeConfigured = false;

        try (TraceLogCapture log = TraceLogCapture.start()) {
            ports.service(trace).prepare(id, Optional.empty());

            var provisional = log.only(TraceStage.UNIVERSITY_REPORT_V2_PROVISIONAL).get("payload");
            assertThat(provisional.get("stored").asBoolean()).isFalse();
            assertThat(provisional.get("paidNarrativeStatus").asText()).isEqualTo("RETRYABLE_FALLBACK");
            assertThat(provisional.get("retryReason").asText()).isEqualTo("PROVIDER_NOT_CONFIGURED");
            assertThat(log.stages()).doesNotContain("UNIVERSITY_REPORT_V2_GENERATED");
        }
    }
}
