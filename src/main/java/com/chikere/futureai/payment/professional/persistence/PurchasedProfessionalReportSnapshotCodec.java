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

    /** Only a report assembled under the current composition is frozen into a new purchase. */
    JsonNode write(ProfessionalReport report) {
        if (!ProfessionalReportVersions.CURRENT.equals(report.reportVersion())) {
            throw new IllegalArgumentException("unsupported purchased report version: " + report.reportVersion());
        }
        return objectMapper.valueToTree(report);
    }

    /** Any version ever written is read back unchanged; v1 and v2 share the same JSON shape. */
    ProfessionalReport read(String reportVersion, JsonNode json) {
        if (!ProfessionalReportVersions.READABLE.contains(reportVersion)) {
            throw new IllegalStateException("unsupported purchased report version: " + reportVersion);
        }
        try { return objectMapper.treeToValue(json, ProfessionalReport.class); }
        catch (JsonProcessingException invalid) {
            throw new IllegalStateException("stored " + reportVersion + " snapshot is invalid", invalid);
        }
    }
}
