package com.chikere.futureai.payment.alevel.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "purchased_a_level_report_snapshot")
class PurchasedALevelReportSnapshotEntity {
    @Id @Column(name = "entitlement_id") UUID entitlementId;
    @Column(name = "assessment_id", nullable = false, unique = true) UUID assessmentId;
    @Column(name = "report_version", nullable = false) String reportVersion;
    @Column(name = "frozen_at", nullable = false) Instant frozenAt;
    @JdbcTypeCode(SqlTypes.JSON) @Column(name = "report_json", nullable = false, columnDefinition = "jsonb")
    JsonNode reportJson;

    protected PurchasedALevelReportSnapshotEntity() { }
}
