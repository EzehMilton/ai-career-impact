package com.chikere.futureai.evidence.alevel.persistence;

import com.chikere.futureai.evidence.alevel.application.ALevelEvidenceCategory;
import com.chikere.futureai.evidence.alevel.application.EvidenceSourceQuality;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "a_level_assessment_evidence_item")
class ALevelEvidenceItemEntity {
    @Id UUID id;
    @Column(name = "assessment_id", nullable = false) UUID assessmentId;
    @Column(name = "item_order", nullable = false) int itemOrder;
    @Enumerated(EnumType.STRING) @Column(nullable = false) ALevelEvidenceCategory category;
    @Column(nullable = false, columnDefinition = "text") String title;
    @Column(nullable = false, columnDefinition = "text") String summary;
    @Column(name = "source_name", nullable = false, columnDefinition = "text") String sourceName;
    @Column(name = "source_reference", columnDefinition = "text") String sourceReference;
    @Column(name = "source_url", columnDefinition = "text") String sourceUrl;
    @Column(columnDefinition = "text") String publisher;
    @Column(columnDefinition = "text") String geography;
    @Column(name = "publication_date") LocalDate publicationDate;
    @Column(name = "retrieved_at", nullable = false) Instant retrievedAt;
    @Column(name = "metric_name", columnDefinition = "text") String metricName;
    @Column(name = "metric_value") BigDecimal metricValue;
    @Column(name = "metric_unit", columnDefinition = "text") String metricUnit;
    @Enumerated(EnumType.STRING) @Column(name = "source_quality", nullable = false)
    EvidenceSourceQuality sourceQuality;
    @Column(name = "relevance_note", nullable = false, columnDefinition = "text") String relevanceNote;

    protected ALevelEvidenceItemEntity() { }
}
