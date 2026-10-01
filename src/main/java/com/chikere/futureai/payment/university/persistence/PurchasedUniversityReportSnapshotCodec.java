package com.chikere.futureai.payment.university.persistence;

import com.chikere.futureai.report.university.application.UniversityReport;
import com.chikere.futureai.report.university.application.UniversityReportVersions;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

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

    JsonNode write(UniversityReport report) {
        if (!UniversityReportVersions.V1.equals(report.reportVersion())) {
            throw new IllegalArgumentException("unsupported purchased report version: " + report.reportVersion());
        }
        return objectMapper.valueToTree(report);
    }

    UniversityReport read(String reportVersion, JsonNode json) {
        if (!UniversityReportVersions.V1.equals(reportVersion)) {
            throw new IllegalStateException("unsupported purchased report version: " + reportVersion);
        }
        try {
            return objectMapper.treeToValue(json, UniversityReport.class);
        }
        catch (JsonProcessingException invalid) {
            throw new IllegalStateException("stored university-report-v1 snapshot is invalid", invalid);
        }
    }
}
