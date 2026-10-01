package com.chikere.futureai.payment.professional.persistence;

import com.chikere.futureai.report.professional.application.ProfessionalReport;
import com.chikere.futureai.report.professional.application.ProfessionalReportVersions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
final class PurchasedProfessionalReportSnapshotCodec {
    private final ObjectMapper objectMapper;

    PurchasedProfessionalReportSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode write(ProfessionalReport report) {
        if (!ProfessionalReportVersions.V1.equals(report.reportVersion())) {
            throw new IllegalArgumentException("unsupported purchased report version: " + report.reportVersion());
        }
        return objectMapper.valueToTree(report);
    }

    ProfessionalReport read(String reportVersion, JsonNode json) {
        if (!ProfessionalReportVersions.V1.equals(reportVersion)) {
            throw new IllegalStateException("unsupported purchased report version: " + reportVersion);
        }
        try { return objectMapper.treeToValue(json, ProfessionalReport.class); }
        catch (JsonProcessingException invalid) {
            throw new IllegalStateException("stored professional-report-v1 snapshot is invalid", invalid);
        }
    }
}
