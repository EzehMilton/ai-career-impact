package com.chikere.futureai.payment.alevel.persistence;

import com.chikere.futureai.report.alevel.application.ALevelReport;
import com.chikere.futureai.report.alevel.application.ALevelReportVersions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Version-aware reader seam. A future incompatible report version must add its own mapper here
 * rather than reinterpreting a stored V1 document.
 */
@Component
final class PurchasedALevelReportSnapshotCodec {
    private final ObjectMapper objectMapper;

    PurchasedALevelReportSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode write(ALevelReport report) {
        if (!ALevelReportVersions.V1.equals(report.reportVersion())) {
            throw new IllegalArgumentException("unsupported purchased report version: " + report.reportVersion());
        }
        return objectMapper.valueToTree(report);
    }

    ALevelReport read(String reportVersion, JsonNode json) {
        if (!ALevelReportVersions.V1.equals(reportVersion)) {
            throw new IllegalStateException("unsupported purchased report version: " + reportVersion);
        }
        try {
            return objectMapper.treeToValue(json, ALevelReport.class);
        }
        catch (JsonProcessingException invalid) {
            throw new IllegalStateException("stored a-level-report-v1 snapshot is invalid", invalid);
        }
    }
}
