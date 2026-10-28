package com.chikere.futureai.payment.university.persistence;

import com.chikere.futureai.report.university.application.UniversityReport;
import com.chikere.futureai.report.university.application.UniversityReportVersions;
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
final class PurchasedUniversityReportSnapshotCodec {
    private final ObjectMapper objectMapper;

    PurchasedUniversityReportSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    JsonNode write(String reportVersion, Optional<UniversityReport> report) {
        if (UniversityReportVersions.V2.equals(reportVersion) && report.isEmpty()) {
            // A v2 purchase freezes no document here; its detailed report is stored separately once prepared.
            ObjectNode marker = objectMapper.createObjectNode();
            marker.put("reportVersion", reportVersion);
            marker.put("detailedReport", "stored separately in university_detailed_report");
            return marker;
        }
        if (!UniversityReportVersions.V1.equals(reportVersion) || report.isEmpty()) {
            throw new IllegalArgumentException("unsupported purchased report version: " + reportVersion);
        }
        return objectMapper.valueToTree(report.get());
    }

    Optional<UniversityReport> read(String reportVersion, JsonNode json) {
        if (UniversityReportVersions.V2.equals(reportVersion)) {
            return Optional.empty();
        }
        if (!UniversityReportVersions.V1.equals(reportVersion)) {
            throw new IllegalStateException("unsupported purchased report version: " + reportVersion);
        }
        try {
            return Optional.of(objectMapper.treeToValue(json, UniversityReport.class));
        }
        catch (JsonProcessingException invalid) {
            throw new IllegalStateException("stored university-report-v1 snapshot is invalid", invalid);
        }
    }
}
