package com.chikere.futureai.payment.alevel.persistence;

import com.chikere.futureai.report.alevel.application.ALevelReport;
import com.chikere.futureai.report.alevel.application.ALevelReportVersions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Optional;

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

    JsonNode write(String reportVersion, Optional<ALevelReport> report) {
        if (ALevelReportVersions.V2.equals(reportVersion) && report.isEmpty()) {
            // A v2 purchase freezes no document here; its detailed report is stored separately once prepared.
            ObjectNode marker = objectMapper.createObjectNode();
            marker.put("reportVersion", reportVersion);
            marker.put("detailedReport", "stored separately in a_level_detailed_report");
            return marker;
        }
        if (!ALevelReportVersions.V1.equals(reportVersion) || report.isEmpty()) {
            throw new IllegalArgumentException("unsupported purchased report version: " + reportVersion);
        }
        return objectMapper.valueToTree(report.get());
    }

    Optional<ALevelReport> read(String reportVersion, JsonNode json) {
        if (ALevelReportVersions.V2.equals(reportVersion)) {
            return Optional.empty();
        }
        if (!ALevelReportVersions.V1.equals(reportVersion)) {
            throw new IllegalStateException("unsupported purchased report version: " + reportVersion);
        }
        try {
            return Optional.of(objectMapper.treeToValue(json, ALevelReport.class));
        }
        catch (JsonProcessingException invalid) {
            throw new IllegalStateException("stored a-level-report-v1 snapshot is invalid", invalid);
        }
    }
}
